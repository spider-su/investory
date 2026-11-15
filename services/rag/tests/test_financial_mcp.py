from fastapi.testclient import TestClient
from pydantic import SecretStr
import pytest

from investory_rag.config import Settings
from investory_rag.financial_mcp import FinancialMcp, MarketLookup, ENDPOINT
from investory_rag.main import app, get_access_gate, get_financial_mcp
from investory_rag.security import AccessGate


def settings(**overrides):
    return Settings(_env_file=None, api_token=SecretStr("service-key"),
                    financial_mcp_enabled=True,
                    financial_mcp_access_token=SecretStr("oauth-token"), **overrides)


@pytest.mark.parametrize("tool", ["UNKNOWN", "DELETE_ORDER", "list_tools"])
def test_unapproved_tools_are_rejected_before_network(tool):
    from fastapi import HTTPException
    with pytest.raises(HTTPException) as err:
        FinancialMcp(settings()).validate(MarketLookup(tool=tool, symbol="AAPL"))
    assert err.value.status_code == 422


@pytest.mark.parametrize("symbol", ["../../etc/passwd", "aapl", "AAPL;cat", "AAPL IBM"])
def test_invalid_symbol_is_rejected(symbol):
    from fastapi import HTTPException
    with pytest.raises(HTTPException) as err:
        FinancialMcp(settings()).validate(MarketLookup(tool="GLOBAL_QUOTE", symbol=symbol))
    assert err.value.status_code == 422


def test_disabled_and_unconfigured_fail_closed():
    from fastapi import HTTPException
    for s in (Settings(_env_file=None, financial_mcp_enabled=False),
              Settings(_env_file=None, financial_mcp_enabled=True)):
        with pytest.raises(HTTPException) as err:
            FinancialMcp(s).validate(MarketLookup(tool="GLOBAL_QUOTE", symbol="MSFT"))
        assert err.value.status_code == 503


def test_endpoint_is_fixed_https_not_user_defined():
    assert ENDPOINT == "https://mcp.alphavantage.co/mcp"


def test_market_api_authorization_and_allowlist(monkeypatch):
    calls = []
    class Fake:
        def validate(self, request):
            return FinancialMcp(settings()).validate(request)

        def lookup(self, request):
            calls.append(request)
            return {"provider": "Alpha Vantage", "symbol": request.symbol,
                    "tool": request.tool, "source": "https://www.alphavantage.co/",
                    "data": '{"price": "101.5"}'}
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(settings())
    app.dependency_overrides[get_financial_mcp] = lambda: Fake()
    try:
        with TestClient(app) as client:
            path = "/market-data"
            item = {"tool": "GLOBAL_QUOTE", "symbol": "MSFT"}
            assert client.post(path, json=item).status_code == 401
            headers = {"Authorization": "Bearer service-key"}
            invalid = client.post(path, json={**item, "portfolioId": 123}, headers=headers)
            assert invalid.status_code == 422
            good = client.post(path, json=item, headers=headers)
            assert good.status_code == 200 and good.json()["symbol"] == "MSFT"
            assert len(calls) == 1
    finally:
        app.dependency_overrides.clear()
