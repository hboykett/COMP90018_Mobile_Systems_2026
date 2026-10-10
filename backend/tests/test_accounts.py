import json
from dataclasses import replace

import pytest
from conftest import headers
from fastapi.testclient import TestClient

from social_api.app import create_app
from social_api.database import Database


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


@pytest.mark.parametrize("authorization", [None, "Basic alice", "Bearer invalid", "Bearer"])
def test_authentication_required(client, authorization):
    response = client.get(
        "/v1/me", headers={"Authorization": authorization} if authorization else {}
    )
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


def test_profile_is_required_before_social_actions(client):
    assert (
        client.get("/v1/me", headers=headers("alice")).json()["error"]["code"] == "profile_required"
    )
    assert client.get("/v1/friends", headers=headers("alice")).status_code == 404


def test_profile_upsert_keeps_identity_and_creation_time(client):
    first = client.put(
        "/v1/me",
        headers=headers("alice"),
        json={"username": " Alice ", "display_name": " Alice A "},
    )
    assert first.status_code == 200
    assert first.json()["username"] == "alice"
    assert first.json()["display_name"] == "Alice A"
    second = client.put(
        "/v1/me",
        headers=headers("alice"),
        json={"username": "alice_new", "display_name": "Alice B"},
    ).json()
    assert second["id"] == first.json()["id"]
    assert second["created_at"] == first.json()["created_at"]
    assert second["display_name"] == "Alice B"
    assert client.get("/v1/me", headers=headers("alice")).json() == second


def test_username_is_unique_case_insensitively_and_failed_update_rolls_back(client, profiles):
    response = client.put(
        "/v1/me", headers=headers("bob"), json={"username": "ALICE", "display_name": "Impostor"}
    )
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "username_taken"
    assert client.get("/v1/me", headers=headers("bob")).json() == profiles["bob"]


@pytest.mark.parametrize(
    "username", ["ab", "a" * 25, "with space", "alice@example.com", "' OR 1=1--"]
)
def test_invalid_username(client, username):
    response = client.put(
        "/v1/me", headers=headers("alice"), json={"username": username, "display_name": "Alice"}
    )
    assert response.status_code == 422


@pytest.mark.parametrize("name", ["", "   ", "a" * 81])
def test_invalid_display_name(client, name):
    assert (
        client.put(
            "/v1/me", headers=headers("alice"), json={"username": "alice", "display_name": name}
        ).status_code
        == 422
    )


def test_identity_cannot_be_supplied_in_profile_body(client):
    response = client.put(
        "/v1/me",
        headers=headers("alice"),
        json={"username": "alice", "display_name": "Alice", "uid": "firebase-uid-bob"},
    )
    assert response.status_code == 422


def test_exact_username_lookup_exposes_only_public_profile(client, profiles):
    response = client.get("/v1/users/lookup?username=BOB", headers=headers("alice"))
    assert response.json() == profiles["bob"]
    assert set(response.json()) == {"id", "username", "display_name", "created_at", "updated_at"}
    assert response.headers["cache-control"] == "no-store"
    assert (
        client.get("/v1/users/lookup?username=missing", headers=headers("alice")).status_code == 404
    )


def test_profiles_survive_restart(settings, verifier, client, profiles):
    with TestClient(create_app(settings, verifier)) as restarted:
        assert restarted.get("/v1/me", headers=headers("alice")).json() == profiles["alice"]


def test_profile_documents_keep_hashed_identity(database, profiles):
    rows = [document.to_dict() for document in database.users.stream()]
    stored = json.dumps(rows)
    assert "firebase-uid-" not in stored
    assert all(len(row["subject_hash"]) == 64 for row in rows)


def test_changed_identity_key_cannot_silently_orphan_accounts(settings, profiles):
    changed = Database(replace(settings, identity_hmac_key=b"a-different-key-" * 4))
    try:
        with pytest.raises(RuntimeError, match="identity key or Firebase project changed"):
            changed.initialize()
    finally:
        changed.close()


def test_unsupported_schema_is_rejected(database, profiles):
    database.config.update({"schema_version": 999})
    with pytest.raises(RuntimeError, match="Unsupported social database"):
        database.initialize()
