from fastapi.testclient import TestClient
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.main import app, get_access_gate, get_service
from investory_rag.security import AccessGate


FACTS = {
    "currency": "USD",
    "marketPortfolioValue": "100.25",
    "longTermAssetValue": "200",
    "totalNetWorth": "300.25",
    "liquidAssets": "50",
    "illiquidAssets": None,
}


class Stub:
    def __init__(self):
        self.calls = []

    def ask_with_facts(self, question, facts):
        self.calls.append((question, facts))
        return {"answer": "300.25 USD [live:profile]", "sources": ["live:profile"]}


def test_facts_requires_internal_auth_and_accepts_allowlist_only():
    service = Stub()
    app.dependency_overrides[get_service] = lambda: service
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("internal-key"), _env_file=None)
    )
    try:
        client = TestClient(app)
        payload = {"question": "What is my total net worth?", "facts": FACTS}
        assert client.post("/ask-with-facts", json=payload).status_code == 401
        assert client.post("/ask-with-facts", json=payload, headers={
            "Authorization": "Bearer wrong"
        }).status_code == 401
        assert service.calls == []
        headers = {"Authorization": "Bearer internal-key"}
        response = client.post("/ask-with-facts", json=payload, headers=headers)
        assert response.status_code == 200
        assert response.json()["sources"] == ["live:profile"]
        assert service.calls[0][1]["totalNetWorth"] == "300.25"
        assert service.calls[0][1]["illiquidAssets"] == "unavailable"

        bad = {"question": "What is my balance?", "facts": {**FACTS, "userId": 99}}
        assert client.post("/ask-with-facts", json=bad, headers=headers).status_code == 422
    finally:
        app.dependency_overrides.clear()
