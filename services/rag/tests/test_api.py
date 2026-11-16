from fastapi.testclient import TestClient
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.main import app, get_access_gate, get_service_provider
from investory_rag.security import AccessGate


class StubService:
    def ask(self, question: str):
        return {"answer": "Default reserve is five years [retirement.md].", "sources": ["retirement.md"]}


def test_health_without_api_key():
    assert TestClient(app).get("/health").json() == {"status": "ok"}


def test_ask_requires_service_token():
    app.dependency_overrides[get_service_provider] = lambda: lambda: StubService()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("test-secret"), _env_file=None)
    )
    try:
        client = TestClient(app)
        assert client.post("/ask", json={"question": "Reserve?"}).status_code == 401
        assert client.post("/ask", json={"question": "Reserve?"}, headers={
            "Authorization": "Bearer wrong",
        }).status_code == 401
        valid = {"Authorization": "Bearer test-secret"}
        result = client.post("/ask", headers=valid, json={"question": "What is the reserve?"})
        assert result.status_code == 200
        assert result.json()["sources"] == ["retirement.md"]
        assert client.post("/ask", headers=valid, json={"question": ""}).status_code == 422
    finally:
        app.dependency_overrides.clear()


def test_unconfigured_api_rejects_every_request():
    app.dependency_overrides[get_service_provider] = lambda: lambda: StubService()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=None, _env_file=None)
    )
    try:
        assert TestClient(app).post(
            "/ask", json={"question": "Hi"}, headers={"Authorization": "Bearer abc"}
        ).status_code == 503
    finally:
        app.dependency_overrides.clear()


def test_document_search_is_private_bounded_and_retrieval_only():
    class SearchService:
        def search(self, question, limit):
            assert question == "Explain contributions"
            assert limit == 2
            return [{"source": "accounting.md", "text": "External flow", "score": 0.8}]
    app.dependency_overrides[get_service_provider] = lambda: lambda: SearchService()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("test-secret"), _env_file=None)
    )
    try:
        client = TestClient(app)
        body = {"question": "Explain contributions", "limit": 2}
        assert client.post("/search", json=body).status_code == 401
        headers = {"Authorization": "Bearer test-secret"}
        response = client.post("/search", headers=headers, json=body)
        assert response.status_code == 200
        assert response.json()["matches"][0]["source"] == "accounting.md"
        assert client.post("/search", headers=headers,
                           json={**body, "limit": 9}).status_code == 422
    finally:
        app.dependency_overrides.clear()
