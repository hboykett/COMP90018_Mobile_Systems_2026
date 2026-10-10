import os
from urllib.parse import urlsplit
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from social_api.app import create_app
from social_api.auth import Identity
from social_api.config import Settings
from social_api.database import Database
from social_api.errors import ApiError


class TestTokenVerifier:
    __test__ = False

    def verify(self, token: str) -> Identity:
        if token not in {"alice", "bob", "charlie"}:
            raise ApiError(401, "invalid_token", "Sign in again to continue.")
        return Identity(uid=f"firebase-uid-{token}")


def headers(user):
    return {"Authorization": f"Bearer {user}"}


@pytest.fixture
def settings():
    if not os.environ.get("FIRESTORE_EMULATOR_HOST"):
        pytest.fail(
            "Run the integration tests through 'npm run test:emulator'. No live database is used."
        )
    host = urlsplit("http://" + os.environ["FIRESTORE_EMULATOR_HOST"]).hostname
    if host not in {"localhost", "127.0.0.1", "::1"}:
        pytest.fail("Integration tests require a local Firestore emulator.")
    return Settings(
        firebase_project_id="demo-social-api",
        identity_hmac_key=b"test-only-key-" * 4,
        firestore_namespace=f"test-{uuid4().hex}",
    )


@pytest.fixture
def database(settings):
    database = Database(settings)
    try:
        yield database
    finally:
        database.close()


@pytest.fixture
def verifier():
    return TestTokenVerifier()


@pytest.fixture
def client(settings, verifier):
    with TestClient(create_app(settings, verifier)) as test_client:
        yield test_client


@pytest.fixture
def profiles(client):
    result = {}
    for username in ("alice", "bob", "charlie"):
        response = client.put(
            "/v1/me",
            headers=headers(username),
            json={"username": username, "display_name": username.title()},
        )
        assert response.status_code == 200
        result[username] = response.json()
    return result


def send(client, profiles, sender="alice", recipient="bob"):
    return client.post(
        "/v1/friend-requests",
        headers=headers(sender),
        json={"recipient_id": profiles[recipient]["id"]},
    )


def respond(client, request_id, user="bob", action="accept"):
    return client.post(
        f"/v1/friend-requests/{request_id}/respond",
        headers=headers(user),
        json={"action": action},
    )
