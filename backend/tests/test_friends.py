import json
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

import pytest
from conftest import headers, respond, send
from fastapi.testclient import TestClient

from social_api.app import create_app


def friend_list(client, user):
    response = client.get("/v1/friends", headers=headers(user))
    assert response.status_code == 200
    return response.json()


def test_complete_friendship_lifecycle(client, profiles):
    sent = send(client, profiles)
    assert sent.status_code == 200
    request = sent.json()
    assert request["status"] == "pending"
    assert friend_list(client, "alice") == []
    assert friend_list(client, "bob") == []
    incoming = client.get("/v1/friend-requests", headers=headers("bob")).json()
    assert incoming == [request]
    outgoing = client.get("/v1/friend-requests?direction=outgoing", headers=headers("alice")).json()
    assert outgoing == [request]
    assert client.get("/v1/friend-requests", headers=headers("charlie")).json() == []
    accepted = respond(client, request["id"])
    assert accepted.status_code == 200
    assert accepted.json()["status"] == "accepted"
    assert friend_list(client, "alice")[0]["user"] == profiles["bob"]
    assert friend_list(client, "bob")[0]["user"] == profiles["alice"]
    assert friend_list(client, "charlie") == []
    assert client.get("/v1/friend-requests", headers=headers("bob")).json() == []
    assert send(client, profiles).json()["error"]["code"] == "already_friends"


def test_duplicate_send_and_accept_are_safe_to_retry(client, profiles):
    first = send(client, profiles).json()
    assert send(client, profiles).json() == first
    accepted = respond(client, first["id"]).json()
    assert respond(client, first["id"]).json() == accepted
    assert len(friend_list(client, "alice")) == 1


def test_crossed_request_requires_explicit_acceptance(client, profiles):
    send(client, profiles)
    crossed = send(client, profiles, sender="bob", recipient="alice")
    assert crossed.status_code == 409
    assert crossed.json()["error"]["code"] == "incoming_request_exists"
    assert friend_list(client, "alice") == []


def test_self_request_and_unknown_recipient_are_rejected(client, profiles):
    assert send(client, profiles, recipient="alice").status_code == 422
    response = client.post(
        "/v1/friend-requests", headers=headers("alice"), json={"recipient_id": str(uuid4())}
    )
    assert response.status_code == 404


@pytest.mark.parametrize(
    ("user", "action", "status"),
    [
        ("alice", "accept", 403),
        ("alice", "decline", 403),
        ("bob", "cancel", 403),
        ("charlie", "accept", 404),
        ("charlie", "decline", 404),
        ("charlie", "cancel", 404),
    ],
)
def test_request_action_permissions(client, profiles, user, action, status):
    request_id = send(client, profiles).json()["id"]
    assert respond(client, request_id, user, action).status_code == status
    assert friend_list(client, "alice") == []
    assert len(client.get("/v1/friend-requests", headers=headers("bob")).json()) == 1


@pytest.mark.parametrize(("user", "action"), [("alice", "cancel"), ("bob", "decline")])
def test_closed_requests_allow_resend_but_old_actions_are_rejected(client, profiles, user, action):
    request_id = send(client, profiles).json()["id"]
    response = respond(client, request_id, user, action)
    assert response.status_code == 200
    assert respond(client, request_id, user, action).json() == response.json()
    assert respond(client, request_id).status_code == 409
    resent = send(client, profiles).json()
    assert resent["id"] != request_id
    assert respond(client, request_id).status_code == 404
    assert respond(client, resent["id"]).status_code == 200


def test_unfriend_removes_both_sides_and_allows_a_new_request(client, profiles):
    request_id = send(client, profiles).json()["id"]
    respond(client, request_id)
    path = f"/v1/friends/{profiles['alice']['id']}"
    assert client.delete(path, headers=headers("bob")).status_code == 204
    assert client.delete(path, headers=headers("bob")).status_code == 204
    assert friend_list(client, "alice") == []
    assert friend_list(client, "bob") == []
    assert respond(client, request_id).status_code == 409
    assert send(client, profiles).json()["status"] == "pending"


def test_unrelated_user_cannot_remove_someone_elses_friendship(client, profiles):
    request_id = send(client, profiles).json()["id"]
    respond(client, request_id)
    client.delete(f"/v1/friends/{profiles['alice']['id']}", headers=headers("charlie"))
    assert len(friend_list(client, "alice")) == 1


def test_removing_nonfriend_does_not_cancel_pending_request(client, profiles):
    sent = send(client, profiles).json()
    client.delete(f"/v1/friends/{profiles['bob']['id']}", headers=headers("alice"))
    assert client.get("/v1/friend-requests", headers=headers("bob")).json() == [sent]


def test_pagination(client, profiles):
    respond(client, send(client, profiles).json()["id"])
    respond(client, send(client, profiles, recipient="charlie").json()["id"], user="charlie")
    page_one = client.get("/v1/friends?limit=1", headers=headers("alice")).json()
    page_two = client.get("/v1/friends?limit=1&offset=1", headers=headers("alice")).json()
    assert page_one[0]["user"]["username"] == "charlie"
    assert page_two[0]["user"]["username"] == "bob"
    requests = client.get(
        "/v1/friend-requests?direction=outgoing&status=accepted&limit=1", headers=headers("alice")
    ).json()
    assert len(requests) == 1


@pytest.mark.parametrize("query", ["limit=0", "limit=101", "offset=-1", "offset=10001"])
def test_invalid_pagination(client, profiles, query):
    assert client.get(f"/v1/friends?{query}", headers=headers("alice")).status_code == 422


def test_simultaneous_crossed_requests_create_only_one_pending_pair(client, profiles, database):
    with ThreadPoolExecutor(max_workers=2) as pool:
        calls = [
            pool.submit(send, client, profiles, "alice", "bob"),
            pool.submit(send, client, profiles, "bob", "alice"),
        ]
        assert sorted(call.result().status_code for call in calls) == [200, 409]
    rows = [document.to_dict() for document in database.relationships.stream()]
    assert len(rows) == 1
    assert rows[0]["status"] == "pending"
    assert "firebase-uid-" not in json.dumps([dict(row) for row in rows])


def test_accept_racing_with_cancel_has_only_one_winner(client, profiles):
    request_id = send(client, profiles).json()["id"]
    with ThreadPoolExecutor(max_workers=2) as pool:
        calls = [
            pool.submit(respond, client, request_id, "bob", "accept"),
            pool.submit(respond, client, request_id, "alice", "cancel"),
        ]
        assert sorted(call.result().status_code for call in calls) == [200, 409]
    assert len(friend_list(client, "alice")) == len(friend_list(client, "bob"))


def test_friendships_survive_restart(client, profiles, settings, verifier):
    respond(client, send(client, profiles).json()["id"])
    with TestClient(create_app(settings, verifier)) as restarted:
        assert friend_list(restarted, "alice")[0]["user"] == profiles["bob"]


def test_client_cannot_forge_request_sender(client, profiles):
    response = client.post(
        "/v1/friend-requests",
        headers=headers("charlie"),
        json={"recipient_id": profiles["bob"]["id"], "sender_id": profiles["alice"]["id"]},
    )
    assert response.status_code == 422
