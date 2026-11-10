"""HTTP interface; run with uvicorn investory_rag.main:app."""
from functools import lru_cache

from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, Field

from .config import get_settings
from .service import RagService

app = FastAPI(title="Investory Document RAG", version="0.1.0")


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)


class AskResponse(BaseModel):
    answer: str
    sources: list[str]


@lru_cache
def get_service() -> RagService:
    return RagService.build(get_settings())


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/ask", response_model=AskResponse)
def ask(request: AskRequest, service: RagService = Depends(get_service)) -> dict:
    try:
        return service.ask(request.question)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
