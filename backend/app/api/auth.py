"""Auth API: sign up, sign in, current user."""

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

from app.auth import User, authenticate, create_user, delete_account, issue_token, validate_signup, CurrentUser

router = APIRouter()


class SignUpRequest(BaseModel):
    name: str
    email: str
    password: str


class SignInRequest(BaseModel):
    email: str
    password: str


class DeleteAccountRequest(BaseModel):
    password: str


class UserOut(BaseModel):
    id: str
    name: str
    email: str


class AuthResponse(BaseModel):
    token: str
    user: UserOut


def _out(user: User) -> UserOut:
    return UserOut(id=user.id, name=user.name, email=user.email)


@router.post("/auth/signup", response_model=AuthResponse, status_code=201)
async def sign_up(body: SignUpRequest) -> AuthResponse:
    problem = validate_signup(body.name, body.email, body.password)
    if problem:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=problem)
    try:
        user = await create_user(body.name, body.email, body.password)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    return AuthResponse(token=await issue_token(user), user=_out(user))


@router.post("/auth/signin", response_model=AuthResponse)
async def sign_in(body: SignInRequest) -> AuthResponse:
    user = await authenticate(body.email, body.password)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect email or password.")
    return AuthResponse(token=await issue_token(user), user=_out(user))


@router.get("/auth/me", response_model=UserOut)
async def me(user: User = CurrentUser) -> UserOut:
    return _out(user)


@router.post("/auth/delete", status_code=204)
async def delete_me(body: DeleteAccountRequest, user: User = CurrentUser) -> None:
    """Permanently delete the signed-in account. The password is asked again so a
    leaked token alone can't remove an account."""
    if await authenticate(user.email, body.password) is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Incorrect password.")
    await delete_account(user.id)
