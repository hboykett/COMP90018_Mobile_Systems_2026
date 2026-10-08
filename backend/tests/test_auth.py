from unittest.mock import Mock

import pytest
from firebase_admin import auth, exceptions
from google.auth.exceptions import DefaultCredentialsError

from social_api.auth import FirebaseTokenVerifier
from social_api.config import Settings
from social_api.errors import ApiError


@pytest.fixture
def firebase_verifier(monkeypatch):
    monkeypatch.delenv("FIREBASE_AUTH_EMULATOR_HOST", raising=False)
    monkeypatch.setattr("firebase_admin.credentials.ApplicationDefault", Mock())
    monkeypatch.setattr("firebase_admin.initialize_app", Mock(return_value=object()))
    return FirebaseTokenVerifier("test-project")


def test_verifier_uses_firebase_token_and_revocation_checks(firebase_verifier, monkeypatch):
    verify = Mock(return_value={"uid": "verified-user"})
    monkeypatch.setattr(auth, "verify_id_token", verify)
    assert firebase_verifier.verify("token").uid == "verified-user"
    verify.assert_called_once_with("token", app=firebase_verifier._app, check_revoked=True)


@pytest.mark.parametrize(
    "error",
    [
        auth.InvalidIdTokenError("invalid"),
        auth.ExpiredIdTokenError("expired", cause=None),
        auth.RevokedIdTokenError("revoked"),
        auth.UserDisabledError("disabled"),
        auth.UserNotFoundError("deleted"),
        ValueError("malformed"),
    ],
)
def test_rejected_firebase_tokens_return_401(firebase_verifier, monkeypatch, error):
    monkeypatch.setattr(auth, "verify_id_token", Mock(side_effect=error))
    with pytest.raises(ApiError) as caught:
        firebase_verifier.verify("bad-token")
    assert caught.value.status == 401
    assert caught.value.code == "invalid_token"


def test_auth_service_outage_is_not_reported_as_invalid_password(firebase_verifier, monkeypatch):
    monkeypatch.setattr(
        auth, "verify_id_token", Mock(side_effect=exceptions.UnavailableError("offline"))
    )
    with pytest.raises(ApiError) as caught:
        firebase_verifier.verify("token")
    assert caught.value.status == 503


def test_missing_uid_claim_is_rejected(firebase_verifier, monkeypatch):
    monkeypatch.setattr(auth, "verify_id_token", Mock(return_value={}))
    with pytest.raises(ApiError) as caught:
        firebase_verifier.verify("token")
    assert caught.value.status == 401


def test_production_verifier_rejects_emulator_mode(monkeypatch):
    monkeypatch.setenv("FIREBASE_AUTH_EMULATOR_HOST", "localhost:9099")
    with pytest.raises(ValueError, match="FIREBASE_AUTH_EMULATOR_HOST"):
        FirebaseTokenVerifier("test-project")


def test_missing_admin_credentials_fail_at_startup(monkeypatch):
    monkeypatch.delenv("FIREBASE_AUTH_EMULATOR_HOST", raising=False)
    credential = Mock()
    credential.get_credential.side_effect = DefaultCredentialsError("missing credentials")
    monkeypatch.setattr(
        "firebase_admin.credentials.ApplicationDefault", Mock(return_value=credential)
    )
    with pytest.raises(ValueError, match="Application Default Credentials"):
        FirebaseTokenVerifier("test-project")


@pytest.mark.parametrize("key", ["", "not-hex", "abc123"])
def test_configuration_requires_a_strong_key(monkeypatch, key):
    monkeypatch.setenv("FIREBASE_PROJECT_ID", "test-project")
    monkeypatch.setenv("SOCIAL_IDENTITY_HMAC_KEY", key)
    with pytest.raises(ValueError, match="SOCIAL_IDENTITY_HMAC_KEY"):
        Settings.from_environment()


def test_configuration_requires_a_project(monkeypatch):
    monkeypatch.delenv("FIREBASE_PROJECT_ID", raising=False)
    monkeypatch.setenv("SOCIAL_IDENTITY_HMAC_KEY", "ab" * 32)
    with pytest.raises(ValueError, match="FIREBASE_PROJECT_ID"):
        Settings.from_environment()
