# Cloud deck database schema (Issue #18)

**Backend:** Cloud Firestore in Firebase project `com-comp90018-flashcards`  
**Auth:** Firebase Auth (`uid` = deck `ownerId`, same as local Room)  
**Architecture:** Firebase Auth and Cloud Firestore, with direct Android deck access and a Python social API for profiles, friendships, and share permissions. See [backend/README.md](../backend/README.md).

This document is the missing write-up for [SPIKE #17](https://github.com/hboykett/COMP90018_Mobile_Systems_2026/issues/17) and the schema provisioned for [#18](https://github.com/hboykett/COMP90018_Mobile_Systems_2026/issues/18).

## Scope

| In scope (#18) | Out of scope (later issues) |
|---|---|
| Firestore project wiring, rules, indexes | Async local ↔ cloud sync (#19) |
| Deck + card document shape | Upload / download UI (#20) |
| Thin Android remote data source | Friends / publish endpoints (#22–#23) |
| Visibility `private` / `public` | Favourites list |

Per-user spaced-repetition progress stays in **local Room** until a later sync design. Do not store SM-2 / FSRS fields on shared deck documents.

The table above describes the original #18 scope. The social API now adds friend sharing without changing the deck/card fields or the `private` / `public` visibility values.

## Collections

```text
decks/{deckId}
  cards/{cardId}
  shares/{recipientFirebaseUid}  # server-managed permissions
social_backends/{namespace}/...  # profiles, friendships, private ID mappings
```

### `decks/{deckId}`

| Field | Type | Notes |
|---|---|---|
| `name` | string | Display name |
| `ownerId` | string | Firebase Auth uid |
| `visibility` | string | `"private"` or `"public"` (publish for search / Save-a-copy) |
| `updatedAt` | timestamp | UTC; used for ordering and conflict hints |
| `cardCount` | number | Denormalised count for catalogue lists |

Document id = same UUID as local `DeckEntity.deckId` so sync can match rows later.

### `decks/{deckId}/cards/{cardId}`

| Field | Type | Notes |
|---|---|---|
| `front` | string | |
| `back` | string | |
| `position` | number | Order within the deck |
| `frontImageUri` | string? | Optional; may be local or remote URI later |
| `backImageUri` | string? | Optional |

Document id = same UUID as local `CardEntity.cardId`.

## Security rules (summary)

Committed in [`firestore.rules`](../firestore.rules):

- Signed-in users only (no anonymous access).
- Owner can create / update / delete their decks and cards.
- Any signed-in user can read decks (and their cards) when `visibility == "public"`.
- Private decks are readable by the owner or an explicitly shared recipient whose friendship is still accepted.
- Sharing grants read-only access to both the deck and its cards. Only the API may issue or revoke grants, after checking ownership and friendship.
- Removing a friendship revokes private access; a new friendship requires a new share. Public access remains available to all signed-in users.
- Social documents and share grants cannot be read or written directly by clients.
- `ownerId` cannot be changed on update.

## Indexes

Committed in [`firestore.indexes.json`](../firestore.indexes.json):

- `visibility` + `updatedAt` — public catalogue
- `ownerId` + `updatedAt` — “my cloud decks”
- `sender_hash` + `status` + `created_at` + `id` — outgoing friend requests
- `recipient_hash` + `status` + `created_at` + `id` — incoming friend requests
- `members` (array contains) + `status` + `updated_at` + `id` — accepted friends

All five indexes and both access policies are deployed through the root `firebase.json`. Backend emulator tests also use this configuration; no separate backend rules/index files should be deployed.

## Provisioning checklist

1. Firebase Console → project `com-comp90018-flashcards` → **Build → Firestore Database** → Create database (start in production mode if rules will be deployed immediately).
2. Install [Firebase CLI](https://firebase.google.com/docs/cli) and log in.
3. From the repo root:

```bash
firebase use com-comp90018-flashcards
firebase deploy --only firestore:rules,firestore:indexes
```

4. Ensure `app/google-services.json` is present (gitignored) so the Android app can reach the same project.

## Android client

- Dependency: `com.google.firebase:firebase-firestore` (Firebase BOM already in the app).
- Models / stub: `com.comp90018.flashcards.data.remote` — CRUD helpers only; no offline sync loop yet.
- Existing `getDeck(deckId)` and `listCards(deckId)` can read explicitly shared private decks after a grant is issued through the API. Friends/sharing UI, shared-deck discovery, and HTTP integration remain to be connected.
