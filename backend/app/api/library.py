"""Library API routes — saved maps and papers."""

from fastapi import APIRouter, HTTPException

from app.api.state import state_manager
from app.auth import CurrentUser, User
from app.models.map import ResearchMap
from app.models.paper import Paper

router = APIRouter()


@router.get("/library/maps", response_model=list[ResearchMap])
async def list_saved_maps(user: User = CurrentUser) -> list[ResearchMap]:
    """List saved maps for the current user."""
    return await state_manager.get_saved_maps(user.id)


@router.post("/library/maps/{map_id}", status_code=201)
async def save_map(map_id: str, user: User = CurrentUser) -> dict:
    """Save a map to the user's library."""
    success = await state_manager.save_map(user.id, map_id)
    if not success:
        raise HTTPException(status_code=404, detail=f"Research map '{map_id}' not found")
    return {"status": "saved", "map_id": map_id}


@router.delete("/library/maps/{map_id}", status_code=204)
async def remove_saved_map(map_id: str, user: User = CurrentUser) -> None:
    """Remove a map from the user's library."""
    await state_manager.remove_saved_map(user.id, map_id)


@router.get("/library/papers", response_model=list[Paper])
async def list_saved_papers(user: User = CurrentUser) -> list[Paper]:
    """List saved papers for the current user."""
    return await state_manager.get_saved_papers(user.id)


@router.post("/library/papers/{paper_id}", status_code=201)
async def save_paper(paper_id: str, user: User = CurrentUser) -> dict:
    """Save a paper to the user's library."""
    success = await state_manager.save_paper(user.id, paper_id)
    if not success:
        raise HTTPException(status_code=404, detail=f"Paper '{paper_id}' not found")
    return {"status": "saved", "paper_id": paper_id}


@router.delete("/library/papers/{paper_id}", status_code=204)
async def remove_saved_paper(paper_id: str, user: User = CurrentUser) -> None:
    """Remove a saved paper from the user's library."""
    await state_manager.remove_saved_paper(user.id, paper_id)
