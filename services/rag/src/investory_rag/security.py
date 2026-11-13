"""Fail-closed, single-service-token protection for private internal RAG traffic.

This is an internal service credential, not end-user authorization. Java must
authorize user/portfolio access before any future financial-data integration.
"""
import hmac
from threading import BoundedSemaphore

from fastapi import HTTPException

from .config import Settings


class AccessGate:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.slots = BoundedSemaphore(settings.max_concurrent_requests)

    def authenticate(self, authorization: str | None) -> None:
        secret = self.settings.api_token
        if secret is None or not secret.get_secret_value():
            raise HTTPException(status_code=503, detail="RAG API not configured")
        scheme, _, token = (authorization or "").partition(" ")
        if scheme.lower() != "bearer" or not hmac.compare_digest(
            token.encode(), secret.get_secret_value().encode()
        ):
            raise HTTPException(
                status_code=401,
                detail="Unauthorized",
                headers={"WWW-Authenticate": "Bearer"},
            )

    def authenticate_metrics(self, authorization: str | None) -> None:
        # Distinct read-only scrape credential. It cannot invoke /ask endpoints.
        secret = self.settings.metrics_token
        if secret is None or not secret.get_secret_value():
            raise HTTPException(status_code=503, detail="RAG metrics not configured")
        scheme, _, token = (authorization or "").partition(" ")
        if scheme.lower() != "bearer" or not hmac.compare_digest(
            token.encode(), secret.get_secret_value().encode()
        ):
            raise HTTPException(
                status_code=401,
                detail="Unauthorized",
                headers={"WWW-Authenticate": "Bearer"},
            )

    def acquire(self) -> None:
        if not self.slots.acquire(blocking=False):
            raise HTTPException(status_code=429, detail="RAG service is busy")

    def release(self) -> None:
        self.slots.release()
