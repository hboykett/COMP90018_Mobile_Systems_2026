from social_api.database import Database
from social_api.errors import ApiError
from social_api.service import now


class DeckService:
    """Authorize Admin SDK reads/writes against the same policy as firestore.rules."""

    def __init__(self, database: Database):
        self.database = database
        self.decks = database.client.collection("decks")

    def share(self, uid: str, deck_id: str, recipient_id: str):
        deck_ref = self.decks.document(deck_id)
        owner_hash = self.database.subject_hash(uid)

        def grant(transaction):
            self._owned_deck(deck_ref, uid, transaction)
            if not self.database.users.document(owner_hash).get(transaction=transaction).exists:
                raise ApiError(404, "profile_required", "Create your social profile first.")
            recipient = self._recipient(recipient_id, transaction)
            recipient_hash = recipient["subject_hash"]
            if recipient_hash == owner_hash:
                raise ApiError(422, "self_share", "You already own this deck.")
            pair_id = self.database.pair_id(owner_hash, recipient_hash)
            friendship = (
                self.database.relationships.document(pair_id).get(transaction=transaction).to_dict()
            )
            if friendship is None or friendship["status"] != "accepted":
                raise ApiError(403, "friendship_required", "Share with an accepted friend.")
            ref = deck_ref.collection("shares").document(recipient["firebase_uid"])
            existing = ref.get(transaction=transaction).to_dict()
            row = {
                "owner_id": uid,
                "recipient_id": recipient_id,
                "namespace": self.database.root.id,
                "pair_id": pair_id,
                "relationship_id": friendship["id"],
                "owner_hash": owner_hash,
                "recipient_hash": recipient_hash,
            }
            # Retrying the same share is idempotent; a new friendship needs a new grant.
            row["created_at"] = (
                existing["created_at"]
                if existing and all(existing.get(key) == value for key, value in row.items())
                else now()
            )
            transaction.set(ref, row)
            return {
                "deck_id": deck_id,
                "recipient_id": recipient_id,
                "created_at": row["created_at"],
            }

        return self.database.run(grant)

    def revoke_share(self, uid: str, deck_id: str, recipient_id: str):
        deck_ref = self.decks.document(deck_id)

        def revoke(transaction):
            self._owned_deck(deck_ref, uid, transaction)
            recipient = self._recipient(recipient_id, transaction)
            # Revocation remains possible after unfriending and is safe to retry.
            transaction.delete(deck_ref.collection("shares").document(recipient["firebase_uid"]))

        self.database.run(revoke)

    def get_deck(self, uid: str, deck_id: str):
        def read(transaction):
            deck = self._readable_deck(self.decks.document(deck_id), uid, transaction)
            return {**deck, "deckId": deck_id}

        return self.database.run(read, read_only=True)

    def list_cards(self, uid: str, deck_id: str, limit: int, offset: int):
        deck_ref = self.decks.document(deck_id)

        def read(transaction):
            self._readable_deck(deck_ref, uid, transaction)
            query = (
                deck_ref.collection("cards")
                .order_by("position")
                .order_by("__name__")
                .limit(limit)
                .offset(offset)
            )
            return [
                {**card.to_dict(), "cardId": card.id}
                for card in query.stream(transaction=transaction)
            ]

        return self.database.run(read, read_only=True)

    @staticmethod
    def _deck(ref, transaction):
        deck = ref.get(transaction=transaction).to_dict()
        if deck is None:
            raise ApiError(404, "deck_not_found", "The deck does not exist or is inaccessible.")
        return deck

    def _owned_deck(self, ref, uid, transaction):
        deck = self._deck(ref, transaction)
        if deck.get("ownerId") != uid:
            raise ApiError(403, "owner_required", "Only the deck owner can change sharing.")
        return deck

    def _recipient(self, recipient_id, transaction):
        recipient = (
            self.database.user_ids.document(recipient_id).get(transaction=transaction).to_dict()
        )
        if recipient is None:
            raise ApiError(404, "user_not_found", "The profile does not exist.")
        if not recipient.get("firebase_uid"):
            raise ApiError(
                409,
                "recipient_profile_refresh_required",
                "Ask your friend to open their social profile once before sharing.",
            )
        return recipient

    def _readable_deck(self, ref, uid, transaction):
        deck = self._deck(ref, transaction)
        if deck.get("ownerId") == uid or deck.get("visibility") == "public":
            return deck
        share = ref.collection("shares").document(uid).get(transaction=transaction).to_dict()
        if share and share.get("owner_id") == deck.get("ownerId"):
            friendship = (
                self.database.client.collection("social_backends")
                .document(share["namespace"])
                .collection("relationships")
                .document(share["pair_id"])
                .get(transaction=transaction)
                .to_dict()
            )
            if (
                friendship
                and friendship.get("status") == "accepted"
                and friendship.get("id") == share.get("relationship_id")
                and share["owner_hash"] != share["recipient_hash"]
                and {share["owner_hash"], share["recipient_hash"]}
                <= set(friendship.get("members", []))
            ):
                return deck
        raise ApiError(404, "deck_not_found", "The deck does not exist or is inaccessible.")
