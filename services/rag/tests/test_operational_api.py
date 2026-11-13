import time
from concurrent.futures import ThreadPoolExecutor
from threading import Event

from fastapi.testclient import TestClient
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.main import app, get_access_gate, get_service
from investory_rag.security import AccessGate


class Stub:
    def ask(self, question):
        return {"answer": "Documented [a.md]", "sources": ["a.md"]}


class SlowStub:
    def __init__(self, started, release):
        self.started = started
        self.release = release

    def ask(self, question):
        self.started.set()
        self.release.wait(timeout=3)
        return {"answer": "Documented [a.md]", "sources": ["a.md"]}


def test_authenticated_metrics_expose_only_fixed_labels():
    app.dependency_overrides[get_service] = lambda: Stub()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("inference-secret"),
                 metrics_token=SecretStr("metrics-secret"), _env_file=None)
    )
    try:
        client = TestClient(app)
        assert client.get("/metrics").status_code == 401
        assert client.get("/metrics", headers={"Authorization": "Bearer inference-secret"}).status_code == 401
        headers = {"Authorization": "Bearer metrics-secret"}
        assert client.post("/ask", json={"question": "SECRET_BALANCE_999"},
                           headers={"Authorization": "Bearer inference-secret"}).status_code == 200
        response = client.get("/metrics", headers=headers)
        assert response.status_code == 200
        text = response.text
        assert "investory_rag_requests_total" in text
        assert "investory_rag_request_seconds" in text
        assert "SECRET_BALANCE_999" not in text
        assert "metrics-secret" not in text
        assert "inference-secret" not in text
    finally:
        app.dependency_overrides.clear()


def test_timeout_keeps_concurrency_slot_until_background_worker_completes():
    started, release = Event(), Event()
    app.dependency_overrides[get_service] = lambda: SlowStub(started, release)
    shared_gate = AccessGate(
        Settings(api_token=SecretStr("timeout-secret"),
                 request_deadline_seconds=0.05, max_concurrent_requests=1,
                 _env_file=None)
    )
    app.dependency_overrides[get_access_gate] = lambda: shared_gate
    try:
        with TestClient(app) as client, ThreadPoolExecutor(max_workers=2) as pool:
            headers = {"Authorization": "Bearer timeout-secret"}
            def post():
                return client.post("/ask", json={"question": "reserve withdrawal"},
                                   headers=headers)
            first = pool.submit(post)
            assert started.wait(timeout=2)
            try:
                # Parallel request must not enter while first provider worker is active.
                assert post().status_code == 429
                assert first.result(timeout=2).status_code == 504
            finally:
                release.set()
    finally:
        app.dependency_overrides.clear()
