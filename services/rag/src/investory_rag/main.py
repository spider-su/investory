"""Authenticated internal HTTP API. No financial data is served here."""
from functools import lru_cache
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from decimal import Decimal

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


def require_gate(
    authorization: Annotated[str | None, Header()] = None,
    gate: AccessGate = Depends(get_access_gate),
) -> AccessGate:
    # Authenticate before resolving the OpenAI-backed service dependency.
    gate.authenticate(authorization)
    return gate


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/ask", response_model=AskResponse)
def ask(
    request: AskRequest,
    gate: AccessGate = Depends(require_gate),
    service: RagService = Depends(get_service),
) -> dict:
    gate.acquire()
    try:
        return service.ask(request.question)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    finally:
        gate.release()


class ProfileFacts(BaseModel):
    """Only allowlisted aggregate fields sent from the authorized Java adapter."""
    currency: str = Field(pattern="^(USD|EUR|PLN)$")
    marketPortfolioValue: Decimal
    longTermAssetValue: Decimal
    totalNetWorth: Decimal
    liquidAssets: Decimal
    illiquidAssets: Decimal

    model_config = {"extra": "forbid"}


class AskWithFactsRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    facts: ProfileFacts
    model_config = {"extra": "forbid"}


@app.post("/ask-with-facts", response_model=AskResponse)
def ask_with_facts(
    request: AskWithFactsRequest,
    gate: AccessGate = Depends(require_gate),
    service: RagService = Depends(get_service),
) -> dict:
    gate.acquire()
    try:
        # Explicit allowlist; do not log, persist, or embed aggregate financial facts.
        facts = {key: str(value) for key, value in request.facts.model_dump().items()}
        return service.ask_with_facts(request.question, facts)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    finally:
        gate.release()
