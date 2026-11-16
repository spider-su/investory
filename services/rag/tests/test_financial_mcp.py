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


def test_api_key_is_an_alternative_to_oauth():
    config = Settings(_env_file=None, financial_mcp_enabled=True,
                      financial_mcp_api_key=SecretStr("provider-key"))
    assert FinancialMcp(config).validate(MarketLookup(tool="GLOBAL_QUOTE", symbol="MSFT")) == {"symbol": "MSFT"}


@pytest.mark.parametrize("mode", ["key", "oauth"])
@pytest.mark.parametrize("payload,expected_status", [
    ('{"symbol":"MSFT"}', None),
    ('{"error":{"type":"rate_limit","message":"premium endpoint"}}', 424),
    ('{"Information":"provider quota exceeded"}', 429),
    ('{"Information":"Standard API rate limit exceeded. Upgrade to a premium plan."}', 429),
    ('{}', 422),
    ('{"Global Quote":{}}', 422),
])
def test_api_key_uses_header_and_never_url(monkeypatch, mode, payload, expected_status):
    from contextlib import asynccontextmanager
    from types import SimpleNamespace
    from unittest.mock import AsyncMock
    observed = {}
    @asynccontextmanager
    async def transport(url, headers):
        observed.update(url=url, headers=headers)
        yield (object(), object(), None)
    class Session:
        def __init__(self, *args): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        initialize = AsyncMock()
        async def call_tool(self, tool, arguments):
            observed.update(tool=tool, arguments=arguments)
            return SimpleNamespace(isError=False, content=[SimpleNamespace(type="text", text=payload)])
    import mcp
    import mcp.client.streamable_http
    monkeypatch.setattr(mcp, "ClientSession", Session)
    monkeypatch.setattr(mcp.client.streamable_http, "streamablehttp_client", transport)
    config = Settings(_env_file=None, financial_mcp_enabled=True,
                      financial_mcp_api_key=SecretStr("provider-key") if mode == "key" else None,
                      financial_mcp_access_token=SecretStr("oauth-token"))
    if expected_status:
        from fastapi import HTTPException
        with pytest.raises(HTTPException) as error:
            FinancialMcp(config).lookup(MarketLookup(tool="GLOBAL_QUOTE", symbol="MSFT"))
        assert error.value.status_code == expected_status
        assert "provider-key" not in error.value.detail
    else:
        result = FinancialMcp(config).lookup(MarketLookup(tool="GLOBAL_QUOTE", symbol="MSFT"))
        assert result["provider"] == "Alpha Vantage"
    assert observed == {"url": ENDPOINT, "headers": {"X-API-Key": "provider-key"} if mode == "key" else {"Authorization": "Bearer oauth-token"}, "tool": "GLOBAL_QUOTE", "arguments": {"symbol": "MSFT"}}


def test_market_entitlement_survives_bounded_worker_without_leaking_details():
    from fastapi import HTTPException
    class MissingEntitlement:
        def validate(self, request): return {"symbol": request.symbol}
        def lookup(self, request): raise HTTPException(424, "private upstream diagnostic")
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(settings())
    app.dependency_overrides[get_financial_mcp] = lambda: MissingEntitlement()
    try:
        with TestClient(app) as client:
            response = client.post("/market-data", json={"tool":"COMPANY_OVERVIEW","symbol":"MSFT"},
                                   headers={"Authorization":"Bearer service-key"})
            assert response.status_code == 424
            assert response.json() == {"detail":"Financial provider entitlement unavailable"}
    finally:
        app.dependency_overrides.clear()
