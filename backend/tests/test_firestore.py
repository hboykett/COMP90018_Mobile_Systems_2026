import os
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import Mock

import httpx
import pytest
from conftest import headers, respond, send
from fastapi.testclient import TestClient
from google.api_core.exceptions import Aborted, ServiceUnavailable

from social_api.app import create_app
from social_api.database import Database
from social_api.errors import ApiError


def test_username_rename_releases_old_name_and_updates_friend_views(client, profiles):
    respond(client, send(client, profiles).json()["id"])
    changed = client.put(
        "/v1/me",
        headers=headers("bob"),
        json={"username": "bobby", "display_name": "Bobby"},
    )
    assert changed.status_code == 200
    assert changed.json()["id"] == profiles["bob"]["id"]
    assert client.get("/v1/users/lookup?username=bob", headers=headers("alice")).status_code == 404
    assert (
        client.get("/v1/users/lookup?username=bobby", headers=headers("alice")).json()
        == changed.json()
    )
    assert client.get("/v1/friends", headers=headers("alice")).json()[0]["user"] == changed.json()
    claimed = client.put(
        "/v1/me",
        headers=headers("charlie"),
        json={"username": "bob", "display_name": "Charlie"},
    )
    assert claimed.status_code == 200


def test_username_reservation_is_atomic_across_api_instances(client, profiles, settings, verifier):
    with TestClient(create_app(settings, verifier)) as other:
        with ThreadPoolExecutor(max_workers=2) as pool:
            calls = [
                pool.submit(
                    app_client.put,
                    "/v1/me",
                    headers=headers(user),
                    json={"username": "one_name", "display_name": user.title()},
                )
                for app_client, user in [(client, "alice"), (other, "bob")]
            ]
            results = [call.result() for call in calls]
    assert sorted(result.status_code for result in results) == [200, 409]
    winner = next(result.json() for result in results if result.status_code == 200)
    lookup = client.get("/v1/users/lookup?username=one_name", headers=headers("charlie"))
    assert lookup.json() == winner
    # The losing user's profile and original name survive the failed transaction.
    loser = "bob" if winner["id"] == profiles["alice"]["id"] else "alice"
    assert client.get("/v1/me", headers=headers(loser)).json() == profiles[loser]


def test_duplicate_send_across_instances_keeps_one_request(
    client, profiles, settings, verifier, database
):
    with TestClient(create_app(settings, verifier)) as other:
        with ThreadPoolExecutor(max_workers=2) as pool:
            calls = [pool.submit(send, app_client, profiles) for app_client in (client, other)]
            results = [call.result() for call in calls]
    assert [result.status_code for result in results] == [200, 200]
    assert results[0].json() == results[1].json()
    assert len(list(database.relationships.stream())) == 1
    assert len(list(database.request_ids.stream())) == 1


def test_resend_removes_old_request_pointer_atomically(client, profiles, database):
    old_id = send(client, profiles).json()["id"]
    respond(client, old_id, action="decline")
    new_id = send(client, profiles).json()["id"]
    assert not database.request_ids.document(old_id).get().exists
    assert database.request_ids.document(new_id).get().exists
    assert len(list(database.relationships.stream())) == 1


@pytest.mark.parametrize("username", ["foo/bar", "foo/bar/baz", "__bad__"])
def test_lookup_cannot_traverse_firestore_paths(client, profiles, username):
    response = client.get(
        "/v1/users/lookup", params={"username": username}, headers=headers("alice")
    )
    assert response.status_code == 404


def test_username_can_match_a_firestore_reserved_name(client):
    created = client.put(
        "/v1/me",
        headers=headers("alice"),
        json={"username": "__alice__", "display_name": "Alice"},
    )
    assert created.status_code == 200
    found = client.get("/v1/users/lookup?username=__alice__", headers=headers("alice"))
    assert found.json() == created.json()


def test_raw_client_cannot_read_or_write_social_documents(settings, profiles, database):
    subject = database.subject_hash("firebase-uid-alice")
    document = f"social_backends/{settings.firestore_namespace}/users/{subject}"
    url = (
        f"http://{os.environ['FIRESTORE_EMULATOR_HOST']}/v1/projects/"
        f"{settings.firebase_project_id}/databases/(default)/documents/{document}"
    )
    with httpx.Client(trust_env=False) as raw_client:
        assert raw_client.get(url).status_code == 403
        assert raw_client.patch(url, json={"fields": {}}).status_code == 403


def test_storage_outage_returns_retryable_error(client, profiles, monkeypatch):
    monkeypatch.setattr(
        "social_api.service.SocialService.get_profile", Mock(side_effect=ServiceUnavailable("down"))
    )
    response = client.get("/v1/me", headers=headers("alice"))
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "storage_unavailable"


def test_exhausted_transaction_retries_return_retryable_error(database, monkeypatch):
    def abort(_transaction):
        raise ValueError("exhausted") from Aborted("contention")

    monkeypatch.setattr("social_api.database.firestore.transactional", lambda _operation: abort)
    monkeypatch.setattr("social_api.database.time.sleep", Mock())
    with pytest.raises(ApiError) as caught:
        database.run(lambda _transaction: None)
    assert caught.value.status == 503
    assert caught.value.code == "storage_busy"


def test_cloud_run_cannot_accidentally_use_emulator(settings, monkeypatch):
    monkeypatch.setenv("K_SERVICE", "social-api")
    with pytest.raises(ValueError, match="must not be set on Cloud Run"):
        Database(settings)
