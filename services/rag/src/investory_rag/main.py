"""Internal HTTP API; aggregate facts remain transient and Java-authorized."""
import asyncio
from time import perf_counter
from functools import lru_cache
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Response
from pydantic import BaseModel, Field
from decimal import Decimal

from .config import get_settings
from .security import AccessGate
from .service import RagService
from .metrics import REQUESTS, LATENCY, generate_latest, CONTENT_TYPE_LATEST

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


async def run_bounded(endpoint: str, gate: AccessGate, operation):
    """Timeout HTTP waiting without freeing capacity while the model is still running.

    asyncio cancellation cannot stop an in-progress provider call in a thread.
    The worker owns the semaphore until it *actually finishes*, preventing
    timeouts from multiplying unbounded background inference work.
    """
    started = perf_counter()
    outcome = "success"
    try:
        gate.acquire()
    except HTTPException:
        REQUESTS.labels(endpoint=endpoint, outcome="rejected").inc()
        raise

    def worker():
        try:
            return operation()
        finally:
            gate.release()

    try:
        return await asyncio.wait_for(
            asyncio.to_thread(worker), timeout=gate.settings.request_deadline_seconds
        )
    except TimeoutError as exc:
        outcome = "timeout"
        raise HTTPException(status_code=504, detail="RAG request deadline exceeded") from exc
    except ValueError as exc:
        outcome = "invalid"
        raise HTTPException(status_code=422, detail="Invalid RAG question") from exc
    except Exception as exc:
        outcome = "upstream_error"
        raise HTTPException(status_code=502, detail="RAG service unavailable") from None
    finally:
        REQUESTS.labels(endpoint=endpoint, outcome=outcome).inc()
        LATENCY.labels(endpoint=endpoint).observe(perf_counter() - started)


@app.get("/metrics")
def metrics(gate: AccessGate = Depends(require_gate)) -> Response:
    # Protected by the same internal token; no identity, questions or amounts.
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/ask", response_model=AskResponse)
async def ask(
    request: AskRequest,
    gate: AccessGate = Depends(require_gate),
    service: RagService = Depends(get_service),
) -> dict:
    return await run_bounded("documents", gate, lambda: service.ask(request.question))


class ProfileFacts(BaseModel):
    """Only allowlisted aggregate fields sent from the authorized Java adapter."""
    currency: str = Field(pattern="^(USD|EUR|PLN)$")
    marketPortfolioValue: Decimal | None
    longTermAssetValue: Decimal | None
    totalNetWorth: Decimal | None
    liquidAssets: Decimal | None
    illiquidAssets: Decimal | None

    model_config = {"extra": "forbid"}


class AskWithFactsRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    facts: ProfileFacts
    model_config = {"extra": "forbid"}


@app.post("/ask-with-facts", response_model=AskResponse)
async def ask_with_facts(
    request: AskWithFactsRequest,
    gate: AccessGate = Depends(require_gate),
    service: RagService = Depends(get_service),
) -> dict:
    facts = {
        key: "unavailable" if value is None else str(value)
        for key, value in request.facts.model_dump().items()
    }
    return await run_bounded(
        "facts", gate, lambda: service.ask_with_facts(request.question, facts)
    )
