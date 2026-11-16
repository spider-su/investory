"""Read-only, fixed-provider financial MCP access.

Do not forward questions, portfolio snapshots or arbitrary tool parameters to MCP.
Remote responses are untrusted market data, not instructions to the model.
"""
import asyncio
import json
import re
from dataclasses import dataclass
from typing import Any

from fastapi import HTTPException
from pydantic import BaseModel, Field

from .config import Settings

ENDPOINT = "https://mcp.alphavantage.co/mcp"
# Only public-symbol lookups. Never expose arbitrary tool discovery/invocation.
TOOLS = frozenset({
    "GLOBAL_QUOTE",
    "COMPANY_OVERVIEW",
    "INCOME_STATEMENT",
    "EARNINGS",
    "EARNINGS_CALENDAR",
})
SYMBOL = re.compile(r"[A-Z0-9][A-Z0-9.^-]{0,19}\Z")
MAX_RESULT_BYTES = 16384


class MarketLookup(BaseModel):
    tool: str
    symbol: str = Field(min_length=1, max_length=20)
    model_config = {"extra": "forbid"}


@dataclass
class FinancialMcp:
    settings: Settings

    def validate(self, request: MarketLookup) -> dict[str, str]:
        if not self.settings.financial_mcp_enabled:
            raise HTTPException(status_code=503, detail="Financial MCP is disabled")
        if request.tool not in TOOLS:
            raise HTTPException(status_code=422, detail="Unsupported financial tool")
        # A symbol is public market information, not an account or portfolio id.
        if not SYMBOL.fullmatch(request.symbol):
            raise HTTPException(status_code=422, detail="Invalid public ticker symbol")
        if not any(value and value.get_secret_value() for value in (
            self.settings.financial_mcp_api_key, self.settings.financial_mcp_access_token
        )):
            raise HTTPException(status_code=503, detail="Financial MCP credential unavailable")
        return {"symbol": request.symbol}

    def lookup(self, request: MarketLookup) -> dict[str, Any]:
        args = self.validate(request)
        # The bounded API executor owns its capacity slot until this call finishes.
        return asyncio.run(self._call(request.tool, args))

    async def _call(self, tool: str, arguments: dict[str, str]) -> dict[str, Any]:
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client

        # The provider supports X-API-Key; raw keys never go in a URL or OAuth Bearer header.
        key = self.settings.financial_mcp_api_key
        token = self.settings.financial_mcp_access_token
        if key and key.get_secret_value():
            headers = {"X-API-Key": key.get_secret_value()}
        else:
            assert token is not None  # validated before calling
            headers = {"Authorization": f"Bearer {token.get_secret_value()}"}
        try:
            async with asyncio.timeout(self.settings.financial_mcp_timeout_seconds):
                async with streamablehttp_client(ENDPOINT, headers=headers) as streams:
                    async with ClientSession(streams[0], streams[1]) as session:
                        await session.initialize()
                        response = await session.call_tool(tool, arguments)
        except Exception as exc:
            # Do not echo provider errors (may include URL, token or raw request).
            raise HTTPException(status_code=502, detail="Financial market data unavailable") from None
        if response.isError:
            raise HTTPException(status_code=502, detail="Financial provider rejected lookup")
        parts = [item.text for item in response.content
                 if getattr(item, "type", None) == "text"]
        payload = "\n".join(parts)
        if not payload or len(payload.encode("utf-8")) > MAX_RESULT_BYTES:
            raise HTTPException(status_code=502, detail="Financial provider returned invalid data")
        # Tool transports may report success while the data API returns an error envelope.
        try:
            decoded = json.loads(payload)
        except json.JSONDecodeError:
            decoded = None  # Valid provider data may be CSV.
        if isinstance(decoded, dict) and any(
            key.lower() in {"error", "error message", "information", "note"} for key in decoded
        ):
            if "premium" in json.dumps(decoded).lower():
                raise HTTPException(status_code=424, detail="Financial provider entitlement unavailable")
            raise HTTPException(status_code=502, detail="Financial provider returned an error")
        if decoded == {} or decoded == []:
            raise HTTPException(status_code=502, detail="Financial provider returned no data")
        # Never accept provider-supplied source identifiers as verified citations.
        return {
            "provider": "Alpha Vantage",
            "tool": tool,
            "symbol": arguments["symbol"],
            "source": "https://www.alphavantage.co/",
            "data": payload,
        }
