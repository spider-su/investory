from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi import HTTPException
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.security import AccessGate


def gate(token=None):
    return AccessGate(Settings(api_token=SecretStr(token) if token else None, _env_file=None))


def test_fails_closed_without_token():
    with pytest.raises(HTTPException) as e:
        gate().authenticate("Bearer anything")
    assert e.value.status_code == 503


def test_rejects_wrong_token_and_accepts_valid_token():
    access = gate("abc123")
    for value in (None, "Bearer incorrect", "Basic abc123", "Bearer"):
        with pytest.raises(HTTPException) as e:
            access.authenticate(value)
        assert e.value.status_code == 401
    access.authenticate("Bearer abc123")


def test_concurrency_limit():
    access = gate("valid")
    access.acquire()
    with pytest.raises(HTTPException) as e:
        access.acquire()
    assert e.value.status_code == 429
    access.release()
    access.acquire()
    access.release()


def test_metrics_route_fails_closed_if_credentials_are_reused():
    access = AccessGate(Settings(api_token=SecretStr("same-secret"),
                                 metrics_token=SecretStr("same-secret"), _env_file=None))
    with pytest.raises(HTTPException) as error:
        access.authenticate_metrics("Bearer same-secret")
    assert error.value.status_code == 503
