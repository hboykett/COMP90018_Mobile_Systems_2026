from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from uuid import UUID, uuid4

import pytest
from conftest import assert_reads, headers, make_friends, rules_headers, share
from fastapi.testclient import TestClient
from google.api_core.exceptions import ServiceUnavailable
from google.cloud.firestore_v1.transaction import Transaction

from social_api.app import create_app


def publish(client, deck, user="alice", published=True):
    method = client.post if published else client.delete
    return method(f"/v1/decks/{deck.id}/publish", headers=headers(user) if user else {})


def save_copy(client, deck, user="bob", request_id=None, **extra):
    return client.post(
        f"/v1/decks/{deck.id}/copies",
        headers=headers(user) if user else {},
        json={"request_id": request_id or str(uuid4()), **extra},
    )


@pytest.fixture
def copies(database):
    """Track only the new decks created by this test for cleanup."""
    tracked = []

    def track(response):
        assert response.status_code == 200, response.text
        ref = database.client.collection("decks").document(response.json()["deckId"])
        tracked.append(ref)
        return ref

    yield track
    for ref in tracked:
        for collection in ("cards", "shares"):
            for document in ref.collection(collection).stream():
                document.reference.delete()
        ref.delete()


def test_publish_unpublish_are_idempotent_and_enforce_both_access_paths(client, raw_client, deck):
    original = deck.get().to_dict()
    published = publish(client, deck)
    assert published.status_code == 200
    assert published.json()["visibility"] == "public"
    assert publish(client, deck).json() == published.json()
    assert_reads(client, raw_client, deck, "bob", True)
    assert_reads(client, raw_client, deck, None, False)
    private = publish(client, deck, published=False)
    assert private.status_code == 200
    assert private.json()["visibility"] == "private"
    assert publish(client, deck, published=False).json() == private.json()
    assert_reads(client, raw_client, deck, "bob", False)
    saved = deck.get().to_dict()
    assert saved["updatedAt"] > original["updatedAt"]
    assert {k: v for k, v in saved.items() if k != "updatedAt"} == {
        k: v for k, v in original.items() if k != "updatedAt"
    }


@pytest.mark.parametrize("user", ["bob", "charlie", None])
@pytest.mark.parametrize("published", [True, False])
def test_only_owner_can_publish_or_unpublish(client, profiles, deck, user, published):
    make_friends(client, profiles)
    share(client, deck, profiles)
    result = publish(client, deck, user, published)
    assert result.status_code == (403 if user else 401)
    assert deck.get().to_dict()["visibility"] == "private"


def test_unpublish_preserves_explicit_friend_share(client, raw_client, profiles, deck):
    make_friends(client, profiles)
    share(client, deck, profiles)
    publish(client, deck)
    assert_reads(client, raw_client, deck, "charlie", True)
    publish(client, deck, published=False)
    assert_reads(client, raw_client, deck, "bob", True)
    assert_reads(client, raw_client, deck, "charlie", False)


@pytest.mark.parametrize("access", ["owner", "public", "shared"])
def test_copy_creates_private_owned_deck_with_fresh_cards_and_no_progress(
    client, raw_client, profiles, deck, copies, access
):
    user = "alice" if access == "owner" else "bob"
    if access == "public":
        publish(client, deck)
    elif access == "shared":
        make_friends(client, profiles)
        share(client, deck, profiles)
    deck.update({"cardCount": 999, "studyProgress": {"reviews": 20}})
    first = next(deck.collection("cards").stream())
    first.reference.update({"interval": 20, "easeFactor": 2.5, "dueAt": datetime.now(UTC)})
    second = deck.collection("cards").document(str(uuid4()))
    second.set({"front": "bye", "back": "au revoir", "position": 5, "backImageUri": "image://b"})
    source_cards = {card.id: card.to_dict() for card in deck.collection("cards").stream()}
    source_deck = deck.get().to_dict()
    result = save_copy(client, deck, user, name="  My vocabulary  ")
    copied = copies(result)
    row = copied.get().to_dict()
    assert copied.id != deck.id
    assert UUID(copied.id)
    assert row["name"] == "My vocabulary"
    assert row["ownerId"] == f"firebase-uid-{user}"
    assert row["visibility"] == "private"
    assert row["cardCount"] == 2
    assert row["copiedFrom"] == {
        "deckId": deck.id,
        "ownerId": "firebase-uid-alice",
        "copiedAt": row["updatedAt"],
    }
    assert "studyProgress" not in row
    copied_cards = list(copied.collection("cards").order_by("position").stream())
    assert {card.id for card in copied_cards}.isdisjoint(source_cards)
    assert all(UUID(card.id) for card in copied_cards)
    assert [card.to_dict()["position"] for card in copied_cards] == [0, 5]
    assert copied_cards[0].to_dict() == {
        "front": "hello",
        "back": "bonjour",
        "position": 0,
        "frontImageUri": "image://front",
    }
    assert copied_cards[1].to_dict()["backImageUri"] == "image://b"
    assert list(copied.collection("shares").stream()) == []
    assert deck.get().to_dict() == source_deck
    assert {card.id: card.to_dict() for card in deck.collection("cards").stream()} == source_cards
    assert_reads(client, raw_client, copied, user, True)
    assert_reads(client, raw_client, copied, "charlie", False)
    if user == "bob":
        assert_reads(client, raw_client, copied, "alice", False)


@pytest.mark.parametrize(
    "state", ["private", "friend_only", "revoked", "unfriended", "unpublished"]
)
def test_copy_rechecks_current_source_access(client, profiles, deck, database, state):
    if state in ("friend_only", "revoked", "unfriended"):
        make_friends(client, profiles)
    if state in ("revoked", "unfriended"):
        share(client, deck, profiles)
    if state == "revoked":
        client.delete(
            f"/v1/decks/{deck.id}/shares/{profiles['bob']['id']}", headers=headers("alice")
        )
    elif state == "unfriended":
        client.delete(f"/v1/friends/{profiles['bob']['id']}", headers=headers("alice"))
    elif state == "unpublished":
        publish(client, deck)
        publish(client, deck, published=False)
    result = save_copy(client, deck)
    assert result.status_code == 404
    assert result.json()["error"]["code"] == "deck_not_found"
    assert list(database.copy_requests.stream()) == []


def test_public_copy_does_not_require_a_social_profile(client, deck, copies):
    assert publish(client, deck).status_code == 200
    copied = copies(save_copy(client, deck))
    assert copied.get().to_dict()["ownerId"] == "firebase-uid-bob"


def test_copy_requires_authentication_and_forbids_ownership_or_attribution_input(client, deck):
    publish(client, deck)
    assert save_copy(client, deck, user=None).status_code == 401
    for extra in (
        {"ownerId": "firebase-uid-alice"},
        {"visibility": "public"},
        {"copiedFrom": {}},
        {"name": "   "},
        {"name": "x" * 201},
    ):
        assert save_copy(client, deck, **extra).status_code == 422
    assert (
        client.post(f"/v1/decks/{deck.id}/copies", headers=headers("bob"), json={}).status_code
        == 422
    )


def test_copy_retries_survive_restart_source_edits_and_loss_of_source_access(
    client, settings, verifier, deck, copies, database
):
    publish(client, deck)
    request_id = str(uuid4())
    first = save_copy(client, deck, request_id=request_id)
    copied = copies(first)
    deck.update({"name": "Changed", "visibility": "private"})
    next(deck.collection("cards").stream()).reference.update({"front": "Changed source card"})
    with TestClient(create_app(settings, verifier)) as restarted:
        repeat = save_copy(restarted, deck, request_id=request_id)
    assert repeat.json() == first.json()
    assert next(copied.collection("cards").stream()).to_dict()["front"] == "hello"
    assert save_copy(client, deck).status_code == 404
    deck.delete()
    assert save_copy(client, deck, request_id=request_id).json() == first.json()
    assert len(list(database.copy_requests.stream())) == 1


def test_copy_request_id_conflicts_and_is_scoped_to_the_user(client, deck, copies):
    publish(client, deck)
    request_id = str(uuid4())
    first = copies(save_copy(client, deck, request_id=request_id))
    assert save_copy(client, deck, request_id=request_id, name="Different").status_code == 409
    other_source = client.post(
        f"/v1/decks/{uuid4()}/copies", headers=headers("bob"), json={"request_id": request_id}
    )
    assert other_source.status_code == 409
    other_user = copies(save_copy(client, deck, user="charlie", request_id=request_id))
    assert first.id != other_user.id
    new_request = copies(save_copy(client, deck))
    assert new_request.id != first.id


def test_deleted_copy_is_not_recreated_on_retry(client, deck, copies):
    publish(client, deck)
    request_id = str(uuid4())
    copied = copies(save_copy(client, deck, request_id=request_id))
    saved = copied.get().to_dict()
    copied.delete()
    assert save_copy(client, deck, request_id=request_id).status_code == 410
    # Reusing that document ID for an unrelated deck cannot satisfy the old receipt.
    saved.pop("copiedFrom")
    copied.set(saved)
    assert save_copy(client, deck, request_id=request_id).status_code == 410


def test_concurrent_retries_across_instances_create_only_one_complete_copy(
    client, settings, verifier, database, deck, copies
):
    publish(client, deck)
    request_id = str(uuid4())
    with TestClient(create_app(settings, verifier)) as other:
        with ThreadPoolExecutor(max_workers=2) as pool:
            tasks = [
                pool.submit(save_copy, app, deck, request_id=request_id) for app in (client, other)
            ]
            results = [task.result() for task in tasks]
    copied = copies(results[0])
    assert results[0].json() == results[1].json()
    assert len(list(copied.collection("cards").stream())) == 1
    assert len(list(database.copy_requests.stream())) == 1


def test_empty_deck_copy_uses_actual_card_count(client, deck, copies):
    for card in deck.collection("cards").stream():
        card.reference.delete()
    copied = copies(save_copy(client, deck, user="alice"))
    assert copied.get().to_dict()["cardCount"] == 0
    assert list(copied.collection("cards").stream()) == []


@pytest.mark.parametrize("problem", ["missing_position", "invalid_front", "too_many", "too_large"])
def test_invalid_or_oversized_sources_never_leave_partial_copies(
    client, deck, database, monkeypatch, problem
):
    if problem == "missing_position":
        deck.collection("cards").document(str(uuid4())).set({"front": "a", "back": "b"})
    elif problem == "invalid_front":
        next(deck.collection("cards").stream()).reference.update({"front": 5})
    elif problem == "too_many":
        monkeypatch.setattr("social_api.decks.MAX_COPY_CARDS", 1)
        deck.collection("cards").document(str(uuid4())).set(
            {"front": "a", "back": "b", "position": 2}
        )
    else:
        monkeypatch.setattr("social_api.decks.MAX_COPY_CONTENT_BYTES", 400)
        next(deck.collection("cards").stream()).reference.update({"front": "long card" * 100})
    before = {ref.id for ref in database.client.collection("decks").stream()}
    response = save_copy(client, deck, user="alice")
    assert response.status_code == (413 if problem.startswith("too_") else 409)
    assert {ref.id for ref in database.client.collection("decks").stream()} == before
    assert list(database.copy_requests.stream()) == []


def test_failure_while_preparing_writes_does_not_save_a_partial_copy(
    client, deck, database, monkeypatch
):
    original_create = Transaction.create

    def fail_card(self, ref, data):
        if ref.parent.id == "cards":
            raise ServiceUnavailable("Temporary outage")
        return original_create(self, ref, data)

    before = {ref.id for ref in database.client.collection("decks").stream()}
    monkeypatch.setattr(Transaction, "create", fail_card)
    response = save_copy(client, deck, user="alice")
    assert response.status_code == 503
    assert {ref.id for ref in database.client.collection("decks").stream()} == before
    assert list(database.copy_requests.stream()) == []


def test_source_attribution_is_protected_while_copy_content_remains_editable(
    client, raw_client, deck, copies, database
):
    publish(client, deck)
    copied = copies(save_copy(client, deck))
    path = f"decks/{copied.id}"
    for fields in (
        {},
        {"copiedFrom": {"nullValue": None}},
        {"copiedFrom": {"mapValue": {"fields": {}}}},
    ):
        result = raw_client.patch(
            path,
            params={"updateMask.fieldPaths": "copiedFrom"},
            headers=rules_headers("bob"),
            json={"fields": fields},
        )
        assert result.status_code == 403, result.text
    changed = raw_client.patch(
        path,
        params={"updateMask.fieldPaths": "name"},
        headers=rules_headers("bob"),
        json={"fields": {"name": {"stringValue": "My edited copy"}}},
    )
    assert changed.status_code == 200, changed.text
    assert deck.get().to_dict()["name"] == "Shared vocabulary"
    attribution = copied.get().to_dict()["copiedFrom"]
    # A user cannot create their own server-issued provenance, even on their own deck.
    forged_id = str(uuid4())
    fields = changed.json()["fields"]
    try:
        result = raw_client.patch(
            f"decks/{forged_id}", headers=rules_headers("bob"), json={"fields": fields}
        )
        assert result.status_code == 403
    finally:
        database.client.collection("decks").document(forged_id).delete()
    assert publish(client, copied, user="bob").status_code == 200
    assert copied.get().to_dict()["copiedFrom"] == attribution


def test_share_copy_publish_lifecycle_is_independent_of_original(
    client, raw_client, profiles, deck, copies
):
    make_friends(client, profiles)
    share(client, deck, profiles)
    copied = copies(save_copy(client, deck))
    client.delete(f"/v1/decks/{deck.id}/shares/{profiles['bob']['id']}", headers=headers("alice"))
    assert_reads(client, raw_client, deck, "bob", False)
    assert_reads(client, raw_client, copied, "bob", True)
    assert publish(client, copied, user="bob").status_code == 200
    assert_reads(client, raw_client, copied, "charlie", True)
    assert publish(client, copied, user="bob", published=False).status_code == 200
    assert_reads(client, raw_client, copied, "charlie", False)
    copy_of_copy = copies(save_copy(client, copied))
    assert copy_of_copy.get().to_dict()["copiedFrom"]["deckId"] == copied.id


def test_missing_decks_return_404(client):
    missing = str(uuid4())
    for method in (client.post, client.delete):
        result = method(f"/v1/decks/{missing}/publish", headers=headers("alice"))
        assert result.status_code == 404
    result = client.post(
        f"/v1/decks/{missing}/copies", headers=headers("alice"), json={"request_id": str(uuid4())}
    )
    assert result.status_code == 404
