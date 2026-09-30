"""FastAPI application factory."""

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth as auth_api, jobs, library, maps, papers
from app.auth import require_user
from app.config import settings
from app.db import store
from app.db.session import describe_database, reset_engine

# Pipeline stages log provider choice, pass rates and fallbacks at INFO.
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
# Per-request client logs are noise at INFO.
logging.getLogger("httpx").setLevel(logging.WARNING)


logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    # Connect and create tables at boot, so a bad DATABASE_URL fails here with a
    # clear error instead of on the first sign-in.
    await store.ensure_schema()
    logger.info("Database ready: %s", describe_database())
    yield
    await reset_engine()


def create_app() -> FastAPI:
    """Create and configure the FastAPI application."""
    application = FastAPI(
        title="Ereuna",
        description="Turn a research topic into a structured research map.",
        version="0.1.0",
        lifespan=lifespan,
    )

    application.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    signed_in = [Depends(require_user)]
    application.include_router(auth_api.router, prefix="/v1", tags=["auth"])
    application.include_router(maps.router, prefix="/v1", tags=["maps"], dependencies=signed_in)
    application.include_router(jobs.router, prefix="/v1", tags=["jobs"], dependencies=signed_in)
    application.include_router(papers.router, prefix="/v1", tags=["papers"], dependencies=signed_in)
    application.include_router(library.router, prefix="/v1", tags=["library"], dependencies=signed_in)

    @application.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    return application


app = create_app()
