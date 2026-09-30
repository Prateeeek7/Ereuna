"""Accounts and authentication.

Email + password accounts stored in the app database (app.db.store). Passwords
are hashed with scrypt and a per-user salt. Sessions are stateless signed tokens
(HMAC-SHA256 over a small JSON payload with an expiry), sent by the app as
`Authorization: Bearer <token>`.
"""

import asyncio
import base64
import hashlib
import hmac
import json
import re
import secrets
import time
import uuid
from dataclasses import dataclass
from typing import Optional

from fastapi import Depends, Header, HTTPException, status

from app.config import settings
from app.db import store

TOKEN_TTL_SECONDS = 30 * 24 * 3600
_EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


@dataclass(frozen=True)
class User:
    id: str
    email: str
    name: str
    created_at: float


# ──────────────────────────────────────────────────────────
# Signing key
# ──────────────────────────────────────────────────────────


async def _secret() -> bytes:
    """Token signing key: AUTH_SECRET if set, otherwise a random key generated
    once and kept in the database, so sessions survive restarts and redeploys."""
    if settings.auth_secret:
        return settings.auth_secret.encode()
    value = await store.get_or_create_setting("auth_secret", lambda: secrets.token_hex(32))
    return value.encode()


# ──────────────────────────────────────────────────────────
# Passwords
# ──────────────────────────────────────────────────────────


def _hash_password(password: str, salt: bytes) -> str:
    digest = hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=32)
    return digest.hex()


def validate_signup(name: str, email: str, password: str) -> Optional[str]:
    """Human-readable problem with the sign-up form, or None."""
    if not name.strip():
        return "Please enter your name."
    if not _EMAIL_RE.match(email.strip()):
        return "Please enter a valid email address."
    if len(password) < 8:
        return "Password must be at least 8 characters."
    if password.lower() == password or not any(c.isdigit() for c in password):
        return "Password must include an uppercase letter and a number."
    return None


def _user(row: dict) -> User:
    return User(id=row["id"], email=row["email"], name=row["name"], created_at=row["created_at"])


async def create_user(name: str, email: str, password: str) -> User:
    email = email.strip().lower()
    salt = secrets.token_bytes(16)
    user = User(id=f"usr_{uuid.uuid4().hex[:16]}", email=email, name=name.strip(), created_at=time.time())
    password_hash = await asyncio.to_thread(_hash_password, password, salt)
    inserted = await store.insert_user({
        "id": user.id,
        "email": user.email,
        "name": user.name,
        "password_hash": password_hash,
        "salt": salt.hex(),
        "created_at": user.created_at,
    })
    if not inserted:
        raise ValueError("An account with this email already exists.")
    return user


async def authenticate(email: str, password: str) -> Optional[User]:
    row = await store.find_user_by_email(email.strip().lower())
    if row is None:
        # Spend the same time as a real check so response timing doesn't reveal accounts.
        await asyncio.to_thread(_hash_password, password, b"\x00" * 16)
        return None
    candidate = await asyncio.to_thread(_hash_password, password, bytes.fromhex(row["salt"]))
    if not hmac.compare_digest(candidate, row["password_hash"]):
        return None
    return _user(row)


async def delete_account(user_id: str) -> None:
    await store.delete_user(user_id)
    _user_cache.pop(user_id, None)


# Every signed-in request looks up its user; with a remote database that is a
# network round trip each time, so accounts are kept in memory for a minute.
_USER_CACHE_SECONDS = 60.0
_user_cache: dict[str, tuple[float, User]] = {}


async def get_user(user_id: str) -> Optional[User]:
    hit = _user_cache.get(user_id)
    if hit and time.monotonic() - hit[0] < _USER_CACHE_SECONDS:
        return hit[1]
    row = await store.find_user_by_id(user_id)
    if row is None:
        _user_cache.pop(user_id, None)
        return None
    user = _user(row)
    if len(_user_cache) > 10_000:
        _user_cache.clear()
    _user_cache[user_id] = (time.monotonic(), user)
    return user


# ──────────────────────────────────────────────────────────
# Tokens
# ──────────────────────────────────────────────────────────


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _unb64(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


async def issue_token(user: User, now: Optional[float] = None) -> str:
    payload = {"sub": user.id, "exp": int((now or time.time()) + TOKEN_TTL_SECONDS)}
    body = _b64(json.dumps(payload, separators=(",", ":")).encode())
    sig = _b64(hmac.new(await _secret(), body.encode(), hashlib.sha256).digest())
    return f"{body}.{sig}"


async def verify_token(token: str, now: Optional[float] = None) -> Optional[str]:
    """User id for a valid, unexpired token; otherwise None."""
    key = await _secret()
    try:
        body, sig = token.split(".", 1)
        expected = _b64(hmac.new(key, body.encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(sig, expected):
            return None
        payload = json.loads(_unb64(body))
        if payload.get("exp", 0) < (now or time.time()):
            return None
        return payload.get("sub")
    except Exception:
        return None


# ──────────────────────────────────────────────────────────
# FastAPI dependency
# ──────────────────────────────────────────────────────────


async def require_user(authorization: Optional[str] = Header(default=None)) -> User:
    """Current user from the Bearer token, or 401."""
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Sign in required.",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if not authorization or not authorization.lower().startswith("bearer "):
        raise unauthorized
    user_id = await verify_token(authorization.split(" ", 1)[1].strip())
    if not user_id:
        raise unauthorized
    user = await get_user(user_id)
    if user is None:
        raise unauthorized
    return user


CurrentUser = Depends(require_user)
