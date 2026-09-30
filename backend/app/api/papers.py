"""Papers API routes."""

from fastapi import APIRouter, HTTPException

from app.api.state import state_manager
from app.auth import CurrentUser, User
from app.models.paper import Paper

router = APIRouter()


@router.get("/papers/{paper_id}", response_model=Paper)
async def get_paper(paper_id: str, user: User = CurrentUser) -> Paper:
    """Retrieve a single paper with its extraction and evidence."""
    paper = await state_manager.get_paper(paper_id, user.id)
    if not paper:
        raise HTTPException(status_code=404, detail=f"Paper '{paper_id}' not found")
    return paper
