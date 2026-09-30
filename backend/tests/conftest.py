"""Shared fixtures: every test gets its own empty database, and API tests run as
a signed-in test user unless they opt out."""

import pytest

from app.auth import User, require_user
from app.config import settings
from app.db import session, store
from app.main import app

TEST_USER = User(id="usr_test", email="test@example.org", name="Test User", created_at=0.0)


@pytest.fixture(autouse=True)
def fresh_database(tmp_path, monkeypatch):
    """A throwaway SQLite database per test; never the real DATABASE_URL."""
    monkeypatch.setattr(settings, "database_url", f"sqlite+aiosqlite:///{tmp_path / 'test.db'}")
    monkeypatch.setattr(settings, "auth_db_path", str(tmp_path / "no-legacy-users.db"))
    session._engine = None  # SQLite engines use NullPool: nothing to dispose
    store.reset_schema_state()
    yield
    session._engine = None
    store.reset_schema_state()


@pytest.fixture(autouse=True)
def signed_in_user(request):
    if request.node.get_closest_marker("real_auth"):
        yield None
        return
    app.dependency_overrides[require_user] = lambda: TEST_USER
    yield TEST_USER
    app.dependency_overrides.pop(require_user, None)


def pytest_configure(config):
    config.addinivalue_line("markers", "real_auth: use the real Bearer-token authentication")
