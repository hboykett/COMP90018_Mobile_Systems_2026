import base64
import json
import os
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from uuid import uuid4

import httpx
import pytest
from conftest import headers, respond, send
from fastapi.testclient import TestClient

from social_api.app import create_app


def rules_headers(user):
    if user is None:
        return {}
    # Unsigned JWTs are accepted ONLY by the local Firestore emulator. The API's
    # production verifier is unchanged; no service credential bypasses these rules.
    uid = f"firebase-uid-{user}"
    payload = {
        "sub": uid,
        "user_id": uid,
        "aud": "demo-social-api",
        "iss": "https://securetoken.google.com/demo-social-api",
        "iat": int(time.time()),
        "exp": int(time.time()) + 3600,
        "auth_time": int(time.time()),
        "firebase": {"sign_in_provider": "custom", "identities": {}},
    }

    def encode(value):
        return base64.urlsafe_b64encode(json.dumps(value).encode()).rstrip(b"=").decode()

    token = f"{encode({'alg': 'none', 'typ': 'JWT'})}.{encode(payload)}."
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def raw_client(settings):
    # The settings fixture requires a loopback emulator before constructing this URL.
    url = (
        f"http://{os.environ['FIRESTORE_EMULATOR_HOST']}/v1/projects/"
        f"{settings.firebase_project_id}/databases/(default)/documents/"
    )
    with httpx.Client(base_url=url, trust_env=False, timeout=15) as client:
        yield client


@pytest.fixture
def deck(database):
    ref = database.client.collection("decks").document(str(uuid4()))
    ref.set(
        {
            "name": "Shared vocabulary",
            "ownerId": "firebase-uid-alice",
            "visibility": "private",
            "updatedAt": datetime.now(UTC),
            "cardCount": 1,
        }
    )
    ref.collection("cards").document(str(uuid4())).set(
        {"front": "hello", "back": "bonjour", "position": 0, "frontImageUri": "image://front"}
    )
    yield ref
    for collection in ("cards", "shares"):
        for document in ref.collection(collection).stream():
            document.reference.delete()
    ref.delete()


def make_friends(client, profiles, sender="alice", recipient="bob"):
    request = send(client, profiles, sender, recipient)
    assert request.status_code == 200
    result = respond(client, request.json()["id"], user=recipient)
    assert result.status_code == 200
    return request.json()["id"]


def share(client, deck, profiles, actor="alice", recipient="bob", **extra):
    return client.post(
        f"/v1/decks/{deck.id}/shares",
        headers=headers(actor),
        json={"recipient_id": profiles[recipient]["id"], **extra},
    )


def assert_reads(client, raw_client, deck, user, allowed):
    api_headers = headers(user) if user else {}
    for suffix in ("", "/cards"):
        api = client.get(f"/v1/decks/{deck.id}{suffix}", headers=api_headers)
        assert api.status_code == (200 if allowed else 404 if user else 401), api.text
        raw = raw_client.get(f"decks/{deck.id}{suffix}", headers=rules_headers(user))
        assert raw.status_code == (200 if allowed else 403), raw.text
    card = next(deck.collection("cards").stream())
    raw = raw_client.get(f"decks/{deck.id}/cards/{card.id}", headers=rules_headers(user))
    assert raw.status_code == (200 if allowed else 403), raw.text


@pytest.mark.parametrize("visibility", ["private", "public"])
def test_owner_public_and_anonymous_access_match_without_social_profile(
    client, raw_client, deck, visibility
):
    deck.update({"visibility": visibility})
    for user in ("alice", "bob", None):
        allowed = user is not None and (user == "alice" or visibility == "public")
        assert_reads(client, raw_client, deck, user, allowed)


def test_explicit_share_is_required_and_preserves_deck_schema(client, raw_client, profiles, deck):
    before = deck.get().to_dict()
    make_friends(client, profiles)
    assert_reads(client, raw_client, deck, "bob", False)
    granted = share(client, deck, profiles)
    assert granted.status_code == 200
    assert granted.json()["recipient_id"] == profiles["bob"]["id"]
    assert share(client, deck, profiles).json() == granted.json()
    assert deck.get().to_dict() == before
    assert_reads(client, raw_client, deck, "bob", True)
    assert_reads(client, raw_client, deck, "charlie", False)
    response = client.get(f"/v1/decks/{deck.id}", headers=headers("bob"))
    assert response.json()["ownerId"] == "firebase-uid-alice"
    cards = client.get(f"/v1/decks/{deck.id}/cards", headers=headers("bob")).json()
    assert cards[0]["frontImageUri"] == "image://front"


@pytest.mark.parametrize("state", ["absent", "pending", "declined", "cancelled", "removed"])
def test_sharing_requires_an_accepted_friendship(client, profiles, deck, state):
    if state != "absent":
        request_id = send(client, profiles).json()["id"]
        if state == "declined":
            respond(client, request_id, action="decline")
        elif state == "cancelled":
            respond(client, request_id, user="alice", action="cancel")
        elif state == "removed":
            respond(client, request_id)
            client.delete(f"/v1/friends/{profiles['bob']['id']}", headers=headers("alice"))
    result = share(client, deck, profiles)
    assert result.status_code == 403
    assert result.json()["error"]["code"] == "friendship_required"
    assert list(deck.collection("shares").stream()) == []


def test_only_owner_can_share_or_revoke_and_no_identity_spoofing(client, profiles, deck):
    make_friends(client, profiles)
    make_friends(client, profiles, sender="bob", recipient="charlie")
    assert share(client, deck, profiles).status_code == 200
    assert share(client, deck, profiles, actor="bob", recipient="charlie").status_code == 403
    assert share(client, deck, profiles, owner_id="firebase-uid-charlie").status_code == 422
    assert share(client, deck, profiles, recipient="alice").status_code == 422
    path = f"/v1/decks/{deck.id}/shares/{profiles['bob']['id']}"
    assert client.delete(path, headers=headers("charlie")).status_code == 403
    assert client.delete(path, headers=headers("bob")).status_code == 403
    assert client.post(f"/v1/decks/{deck.id}/shares", json={}).status_code == 401
    assert client.delete(path).status_code == 401


def test_revocation_removes_access_and_can_be_retried(client, raw_client, profiles, deck):
    make_friends(client, profiles)
    share(client, deck, profiles)
    path = f"/v1/decks/{deck.id}/shares/{profiles['bob']['id']}"
    assert client.delete(path, headers=headers("alice")).status_code == 204
    assert client.delete(path, headers=headers("alice")).status_code == 204
    assert_reads(client, raw_client, deck, "bob", False)
    assert_reads(client, raw_client, deck, "alice", True)


@pytest.mark.parametrize("unfriender", ["alice", "bob"])
def test_unfriend_revokes_access_and_refriend_requires_new_share(
    client, raw_client, profiles, deck, unfriender
):
    old_id = make_friends(client, profiles)
    share(client, deck, profiles)
    friend = "bob" if unfriender == "alice" else "alice"
    result = client.delete(f"/v1/friends/{profiles[friend]['id']}", headers=headers(unfriender))
    assert result.status_code == 204
    assert_reads(client, raw_client, deck, "bob", False)
    assert make_friends(client, profiles) != old_id
    assert_reads(client, raw_client, deck, "bob", False)
    assert share(client, deck, profiles).status_code == 200
    assert_reads(client, raw_client, deck, "bob", True)


def test_public_access_survives_unfriend_and_revoke_but_unpublish_denies(
    client, raw_client, profiles, deck
):
    make_friends(client, profiles)
    share(client, deck, profiles)
    deck.update({"visibility": "public"})
    client.delete(f"/v1/friends/{profiles['bob']['id']}", headers=headers("alice"))
    path = f"/v1/decks/{deck.id}/shares/{profiles['bob']['id']}"
    assert client.delete(path, headers=headers("alice")).status_code == 204
    assert_reads(client, raw_client, deck, "bob", True)
    deck.update({"visibility": "private"})
    assert_reads(client, raw_client, deck, "bob", False)


def test_friend_cannot_modify_decks_cards_grants_or_social_state(
    client, raw_client, profiles, deck, database
):
    make_friends(client, profiles)
    share(client, deck, profiles)
    card = next(deck.collection("cards").stream())
    paths = [
        f"decks/{deck.id}",
        f"decks/{deck.id}/cards/{card.id}",
        f"decks/{deck.id}/cards/{uuid4()}",
        f"decks/{deck.id}/shares/firebase-uid-bob",
        f"decks/{deck.id}/shares/firebase-uid-charlie",
        database.user_ids.document(profiles["bob"]["id"]).path,
        next(database.relationships.stream()).reference.path,
    ]
    for path in paths:
        result = raw_client.patch(path, headers=rules_headers("bob"), json={"fields": {}})
        assert result.status_code == 403, (path, result.text)
        assert raw_client.delete(path, headers=rules_headers("bob")).status_code == 403
    # Even the owner cannot bypass accepted-friend checks by writing grants directly.
    grant = f"decks/{deck.id}/shares/firebase-uid-charlie"
    for user in ("alice", "bob", "charlie", None):
        assert (
            raw_client.patch(grant, headers=rules_headers(user), json={"fields": {}}).status_code
            == 403
        )
        assert raw_client.get(grant, headers=rules_headers(user)).status_code == 403
        assert raw_client.get(paths[-2], headers=rules_headers(user)).status_code == 403


def test_old_grant_cannot_authorize_deck_recreated_with_a_different_owner(
    client, raw_client, profiles, deck
):
    make_friends(client, profiles)
    share(client, deck, profiles)
    # Firestore doesn't delete subcollections when their parent is deleted.
    original = deck.get().to_dict()
    deck.delete()
    assert client.get(f"/v1/decks/{deck.id}", headers=headers("bob")).status_code == 404
    deck.set({**original, "ownerId": "firebase-uid-charlie"})
    assert_reads(client, raw_client, deck, "bob", False)


def test_profile_mapping_upgrades_existing_accounts_without_changing_public_ids(
    client, profiles, deck, database
):
    make_friends(client, profiles)
    pointer = database.user_ids.document(profiles["bob"]["id"])
    pointer.set({"subject_hash": database.subject_hash("firebase-uid-bob")})
    blocked = share(client, deck, profiles)
    assert blocked.status_code == 409
    assert blocked.json()["error"]["code"] == "recipient_profile_refresh_required"
    refreshed = client.get("/v1/me", headers=headers("bob"))
    assert refreshed.json() == profiles["bob"]
    assert pointer.get().to_dict()["firebase_uid"] == "firebase-uid-bob"
    assert share(client, deck, profiles).status_code == 200
    assert set(refreshed.json()) == {"id", "username", "display_name", "created_at", "updated_at"}


def test_share_and_unfriend_across_instances_never_leave_read_access(
    client, profiles, deck, raw_client, settings, verifier
):
    make_friends(client, profiles)
    with TestClient(create_app(settings, verifier)) as other:
        with ThreadPoolExecutor(max_workers=2) as pool:
            grant = pool.submit(share, client, deck, profiles)
            remove = pool.submit(
                other.delete, f"/v1/friends/{profiles['bob']['id']}", headers=headers("alice")
            )
            assert remove.result().status_code == 204
            assert grant.result().status_code in (200, 403)
    assert_reads(client, raw_client, deck, "bob", False)


def test_card_pagination_after_authorization(client, profiles, deck):
    make_friends(client, profiles)
    share(client, deck, profiles)
    for position in (1, 2):
        deck.collection("cards").document(str(uuid4())).set(
            {"front": str(position), "back": "answer", "position": position}
        )
    result = client.get(f"/v1/decks/{deck.id}/cards?limit=1&offset=1", headers=headers("bob"))
    assert result.status_code == 200
    assert [card["position"] for card in result.json()] == [1]


def test_original_owner_and_public_catalogue_queries_still_work(raw_client, deck):
    for field, value in (("ownerId", "firebase-uid-alice"), ("visibility", "public")):
        if field == "visibility":
            deck.update({"visibility": "public"})
        query = {
            "structuredQuery": {
                "from": [{"collectionId": "decks"}],
                "where": {
                    "fieldFilter": {
                        "field": {"fieldPath": field},
                        "op": "EQUAL",
                        "value": {"stringValue": value},
                    }
                },
                "orderBy": [{"field": {"fieldPath": "updatedAt"}, "direction": "DESCENDING"}],
            }
        }
        result = raw_client.post(
            str(raw_client.base_url).rstrip("/") + ":runQuery",
            headers=rules_headers("alice"),
            json=query,
        )
        assert result.status_code == 200, result.text
        names = [row["document"]["name"] for row in result.json() if "document" in row]
        assert any(name.endswith(f"/decks/{deck.id}") for name in names)


def test_owner_can_still_edit_deck_and_manage_cards(raw_client, deck):
    card = next(deck.collection("cards").stream())
    for path, fields in (
        (
            f"decks/{deck.id}",
            {**deck.get().to_dict(), "name": "Renamed"},
        ),
        (f"decks/{deck.id}/cards/{card.id}", {"front": "updated", "back": "answer", "position": 0}),
        (f"decks/{deck.id}/cards/{uuid4()}", {"front": "new", "back": "answer", "position": 1}),
    ):
        encoded = {}
        for key, value in fields.items():
            if isinstance(value, datetime):
                encoded[key] = {"timestampValue": value.isoformat()}
            elif isinstance(value, int):
                encoded[key] = {"integerValue": str(value)}
            else:
                encoded[key] = {"stringValue": value}
        result = raw_client.patch(path, headers=rules_headers("alice"), json={"fields": encoded})
        assert result.status_code == 200, result.text
    changed_owner = raw_client.patch(
        f"decks/{deck.id}",
        params={"updateMask.fieldPaths": "ownerId"},
        headers=rules_headers("alice"),
        json={"fields": {"ownerId": {"stringValue": "firebase-uid-bob"}}},
    )
    assert changed_owner.status_code == 403
    assert raw_client.delete(path, headers=rules_headers("alice")).status_code == 200


def test_direct_client_can_create_only_own_decks(raw_client, database):
    path = f"decks/{uuid4()}"
    fields = {
        "name": {"stringValue": "Created from Android"},
        "ownerId": {"stringValue": "firebase-uid-alice"},
        "visibility": {"stringValue": "private"},
        "updatedAt": {"timestampValue": datetime.now(UTC).isoformat()},
        "cardCount": {"integerValue": "0"},
    }
    try:
        for user, status in ((None, 403), ("bob", 403), ("alice", 200)):
            result = raw_client.patch(path, headers=rules_headers(user), json={"fields": fields})
            assert result.status_code == status, result.text
        assert raw_client.delete(path, headers=rules_headers("alice")).status_code == 200
    finally:
        database.client.document(path).delete()
