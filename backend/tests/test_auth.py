"""Accounts: sign up, sign in, tokens, protected routes, per-user libraries."""

import asyncio

import pytest
from fastapi.testclient import TestClient

from app import auth
from app.config import settings
from app.main import app

pytestmark = pytest.mark.real_auth


@pytest.fixture(autouse=True)
def generated_signing_key(monkeypatch):
    monkeypatch.setattr(settings, "auth_secret", "")


def signup(client, email="ada@example.org", password="Lovelace1843", name="Ada"):
    return client.post("/v1/auth/signup", json={"name": name, "email": email, "password": password})


def test_signup_signin_and_me():
    client = TestClient(app)
    r = signup(client)
    assert r.status_code == 201
    token = r.json()["token"]
    assert r.json()["user"]["email"] == "ada@example.org"

    me = client.get("/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200 and me.json()["name"] == "Ada"

    r2 = client.post("/v1/auth/signin", json={"email": "ADA@example.org ", "password": "Lovelace1843"})
    assert r2.status_code == 200 and r2.json()["user"]["id"] == r.json()["user"]["id"]


def test_duplicate_email_and_weak_password_rejected():
    client = TestClient(app)
    assert signup(client).status_code == 201
    dup = signup(client)
    assert dup.status_code == 409
    weak = signup(client, email="b@example.org", password="short")
    assert weak.status_code == 422
    assert "8 characters" in weak.json()["detail"]
    bad_email = signup(client, email="not-an-email")
    assert bad_email.status_code == 422


def test_wrong_password_is_generic_401():
    client = TestClient(app)
    signup(client)
    r = client.post("/v1/auth/signin", json={"email": "ada@example.org", "password": "Wrong12345"})
    assert r.status_code == 401
    unknown = client.post("/v1/auth/signin", json={"email": "nobody@example.org", "password": "Wrong12345"})
    assert unknown.status_code == 401
    assert r.json()["detail"] == unknown.json()["detail"]  # doesn't reveal which accounts exist


def test_passwords_are_not_stored_in_plain_text():
    from app.db import store

    client = TestClient(app)
    signup(client)
    row = asyncio.run(store.find_user_by_email("ada@example.org"))
    assert "Lovelace1843" not in row["password_hash"] and len(row["password_hash"]) == 64


def test_protected_routes_require_a_valid_token():
    client = TestClient(app)
    assert client.get("/v1/library/maps").status_code == 401
    assert client.get("/v1/library/maps", headers={"Authorization": "Bearer forged.token"}).status_code == 401
    assert client.get("/health").status_code == 200


@pytest.mark.asyncio
async def test_tokens_expire_and_are_tamper_proof():
    user = auth.User(id="usr_x", email="x@example.org", name="X", created_at=0)
    token = await auth.issue_token(user, now=1_000)
    assert await auth.verify_token(token, now=1_000 + 60) == "usr_x"
    assert await auth.verify_token(token, now=1_000 + auth.TOKEN_TTL_SECONDS + 1) is None
    body, sig = token.split(".")
    assert await auth.verify_token(body + "x." + sig, now=1_060) is None


def test_libraries_are_per_user():
    from app.api.state import state_manager
    from app.models.map import MapFilters, ResearchMap

    asyncio.run(state_manager.store_map(ResearchMap(id="map_auth_test", topic="t", normalized_topic="t", filters=MapFilters())))
    client = TestClient(app)
    a = signup(client, email="a@example.org").json()["token"]
    b = signup(client, email="b@example.org").json()["token"]
    assert client.post("/v1/library/maps/map_auth_test", headers={"Authorization": f"Bearer {a}"}).status_code == 201
    a_maps = client.get("/v1/library/maps", headers={"Authorization": f"Bearer {a}"}).json()
    b_maps = client.get("/v1/library/maps", headers={"Authorization": f"Bearer {b}"}).json()
    assert [m["id"] for m in a_maps] == ["map_auth_test"]
    assert b_maps == []


def test_searching_a_topic_adds_the_map_to_the_library():
    from app.api.state import state_manager
    from app.models.map import MapFilters, ResearchMap

    filters = MapFilters()
    asyncio.run(state_manager.store_map(
        ResearchMap(id="map_cached_topic", topic="Cached topic", normalized_topic="cached topic", filters=filters), filters))
    client = TestClient(app)
    token = signup(client).json()["token"]
    h = {"Authorization": f"Bearer {token}"}
    r = client.post("/v1/maps", json={"topic": "Cached topic"}, headers=h)
    assert r.json()["status"] == "ready" and r.json()["map_id"] == "map_cached_topic"
    assert [m["id"] for m in client.get("/v1/library/maps", headers=h).json()] == ["map_cached_topic"]


def test_daily_map_limit(monkeypatch):
    from app.api import maps as maps_api

    async def no_pipeline(**kwargs):
        return None

    monkeypatch.setattr(maps_api, "execute_retrieval_pipeline", no_pipeline)
    monkeypatch.setattr(settings, "max_maps_per_user_per_day", 2)
    client = TestClient(app)
    token = signup(client, email="limit@example.org").json()["token"]
    h = {"Authorization": f"Bearer {token}"}
    codes = [client.post("/v1/maps", json={"topic": f"topic number {i}"}, headers=h).status_code for i in range(3)]
    assert codes == [202, 202, 429]


def test_delete_account_needs_password_and_removes_private_data():
    from app.api.state import state_manager
    from app.db import store
    from app.models.map import ResearchMap
    from app.models.paper import Paper

    client = TestClient(app)
    r = signup(client)
    token, user_id = r.json()["token"], r.json()["user"]["id"]
    headers = {"Authorization": f"Bearer {token}"}

    paper = Paper(id="up_del1", title="My own paper", year=2024, source="upload")
    private = ResearchMap(id="map_del1", topic="t", normalized_topic="t", papers=[paper],
                          paper_ids=[paper.id], source="upload", owner_id=user_id)
    shared = ResearchMap(id="map_shared1", topic="shared topic", normalized_topic="shared topic")
    asyncio.run(state_manager.store_map(private))
    asyncio.run(state_manager.store_map(shared))
    assert client.post("/v1/library/maps/map_del1", headers=headers).status_code == 201

    assert client.post("/v1/auth/delete", json={"password": "Wrong12345"}, headers=headers).status_code == 403
    assert client.post("/v1/auth/delete", json={"password": "Lovelace1843"}, headers=headers).status_code == 204

    assert client.get("/v1/auth/me", headers=headers).status_code == 401
    assert client.post("/v1/auth/signin", json={"email": "ada@example.org", "password": "Lovelace1843"}).status_code == 401
    assert asyncio.run(store.get_map("map_del1")) is None
    assert asyncio.run(store.get_map("map_shared1")) is not None
    assert asyncio.run(store.list_saved(store.saved_maps, user_id, "map_id")) == []
