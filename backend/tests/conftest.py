import base64
import json
import os
import time
from datetime import UTC, datetime
from urllib.parse import urlsplit
from uuid import uuid4

import httpx
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
