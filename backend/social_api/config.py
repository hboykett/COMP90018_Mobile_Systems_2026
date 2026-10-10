import os
import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Settings:
    firebase_project_id: str
    identity_hmac_key: bytes = field(repr=False)
    firestore_database: str = "(default)"
    firestore_namespace: str = "default"

    def __post_init__(self):
        if len(self.identity_hmac_key) < 32:
            raise ValueError("SOCIAL_IDENTITY_HMAC_KEY must contain at least 32 random bytes.")
        if not self.firebase_project_id.strip():
            raise ValueError("FIREBASE_PROJECT_ID is required.")
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", self.firestore_namespace) or re.fullmatch(
            r"__.*__", self.firestore_namespace
        ):
            raise ValueError("SOCIAL_FIRESTORE_NAMESPACE must be 1–80 letters, digits, '_' or '-'.")
        if not self.firestore_database or "/" in self.firestore_database:
            raise ValueError("FIRESTORE_DATABASE_ID must be a database ID.")

    @classmethod
    def from_environment(cls):
        try:
            key = bytes.fromhex(os.environ.get("SOCIAL_IDENTITY_HMAC_KEY", ""))
        except ValueError as error:
            raise ValueError("SOCIAL_IDENTITY_HMAC_KEY must be a hex-encoded secret.") from error
        return cls(
            firebase_project_id=os.environ.get("FIREBASE_PROJECT_ID", ""),
            identity_hmac_key=key,
            firestore_database=os.environ.get("FIRESTORE_DATABASE_ID", "(default)"),
            firestore_namespace=os.environ.get("SOCIAL_FIRESTORE_NAMESPACE", "default"),
        )
