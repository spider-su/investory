import time

from fastapi.testclient import TestClient
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.main import app, get_access_gate, get_service
from investory_rag.security import AccessGate


class Stub:
    def ask(self, question):
        return {"answer": "Documented [a.md]", "sources": ["a.md"]}


class SlowStub:
    def ask(self, question):
        time.sleep(0.2)
        return {"answer": "Documented [a.md]", "sources": ["a.md"]}


def test_authenticated_metrics_expose_only_fixed_labels():
    app.dependency_overrides[get_service] = lambda: Stub()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("metrics-secret"), _env_file=None)
    )
    try:
        client = TestClient(app)
        assert client.get("/metrics").status_code == 401
        headers = {"Authorization": "Bearer metrics-secret"}
        assert client.post("/ask", json={"question": "SECRET_BALANCE_999"},
                           headers=headers).status_code == 200
        response = client.get("/metrics", headers=headers)
        assert response.status_code == 200
        text = response.text
        assert "investory_rag_requests_total" in text
        assert "investory_rag_request_seconds" in text
        assert "SECRET_BALANCE_999" not in text
        assert "metrics-secret" not in text
    finally:
        app.dependency_overrides.clear()


def test_timeout_keeps_concurrency_slot_until_background_worker_completes():
    app.dependency_overrides[get_service] = lambda: SlowStub()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("timeout-secret"),
                 request_deadline_seconds=0.02, max_concurrent_requests=1,
                 _env_file=None)
    )
    try:
        client = TestClient(app)
        headers = {"Authorization": "Bearer timeout-secret"}
        first = client.post("/ask", json={"question": "reserve withdrawal"},
                            headers=headers)
        assert first.status_code == 504
        second = client.post("/ask", json={"question": "reserve withdrawal"},
                             headers=headers)
        assert second.status_code == 429
        time.sleep(0.3)
        assert client.post("/ask", json={"question": "reserve withdrawal"},
                           headers=headers).status_code == 504
    finally:
        app.dependency_overrides.clear()
