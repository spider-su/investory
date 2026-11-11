"""Authenticated internal HTTP API. No financial data is served here."""
from functools import lru_cache
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from .config import get_settings
from .security import AccessGate
from .service import RagService

app = FastAPI(title="Investory Document RAG", version="0.2.0")


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)


class AskResponse(BaseModel):
    answer: str
    sources: list[str]


@lru_cache
def get_service() -> RagService:
    return RagService.build(get_settings())


@lru_cache
def get_access_gate() -> AccessGate:
    return AccessGate(get_settings())


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/ask", response_model=AskResponse)
def ask(
    request: AskRequest,
    authorization: Annotated[str | None, Header()] = None,
    service: RagService = Depends(get_service),
    gate: AccessGate = Depends(get_access_gate),
) -> dict:
    gate.authenticate(authorization)
    gate.acquire()
    try:
        return service.ask(request.question)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    finally:
        gate.release()
