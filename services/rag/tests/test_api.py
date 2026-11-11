from fastapi.testclient import TestClient

from investory_rag.main import app, get_service


class StubService:
    def ask(self, question: str):
        return {"answer": "Default reserve is five years [retirement.md].", "sources": ["retirement.md"]}


def test_health_without_api_key():
    client = TestClient(app)
    assert client.get("/health").json() == {"status": "ok"}


def test_ask_uses_dependency_override_without_openai():
    app.dependency_overrides[get_service] = lambda: StubService()
    try:
        client = TestClient(app)
        result = client.post("/ask", json={"question": "What is the reserve?"})
        assert result.status_code == 200
        assert result.json()["sources"] == ["retirement.md"]
        assert client.post("/ask", json={"question": ""}).status_code == 422
    finally:
        app.dependency_overrides.clear()
