"""Private deployed acceptance; prints statuses only, never answers or credentials."""
import argparse
import asyncio
from datetime import timedelta
import json
import os

import httpx


def validate_answer(payload: dict) -> None:
    if not isinstance(payload.get("answer"), str) or not payload["answer"].strip():
        raise ValueError("RAG returned no answer")
    sources = payload.get("sources")
    if not isinstance(sources, list) or "live:profile" not in sources:
        raise ValueError("RAG returned no live-fact provenance")
    if not all(isinstance(source, str) and source.strip() for source in sources):
        raise ValueError("RAG returned invalid sources")


async def verify(base_url: str, portfolio: int, denied_portfolio: int, token: str) -> dict:
    from mcp import ClientSession
    from mcp.client.streamable_http import streamable_http_client

    headers = {"Authorization": f"Bearer {token}"}
    async with httpx.AsyncClient(headers=headers, timeout=90) as http:
        async with streamable_http_client(f"{base_url}/mcp", http_client=http) as streams:
            async with ClientSession(streams[0], streams[1],
                                     read_timeout_seconds=timedelta(seconds=90)) as session:
                await session.initialize()
                names = {tool.name for tool in (await session.list_tools()).tools}
                expected = {"get_portfolio_dashboard", "get_portfolio_performance", "get_investment_ytd"}
                if not expected <= names:
                    raise ValueError("MCP tool discovery incomplete")
                result = await session.call_tool("get_portfolio_performance", {"portfolioId": portfolio})
                if result.isError or not result.structuredContent:
                    raise ValueError("MCP financial tool failed")
                denied = await session.call_tool("get_portfolio_performance", {"portfolioId": denied_portfolio})
                if not denied.isError:
                    raise ValueError("MCP portfolio isolation failed")

        path = f"{base_url}/api/v1/portfolios/{portfolio}/rag/ask"
        answer = await http.post(path, json={"question": "State my current net worth and currency from the supplied facts."})
        answer.raise_for_status()
        validate_answer(answer.json())
        denied = await http.post(f"{base_url}/api/v1/portfolios/{denied_portfolio}/rag/ask",
                                 json={"question": "What is my net worth?"})
        if denied.status_code != 403:
            raise ValueError("RAG portfolio isolation failed")
    async with httpx.AsyncClient(timeout=15) as anonymous:
        for path in ["/mcp", f"/api/v1/portfolios/{portfolio}/rag/ask"]:
            response = await anonymous.post(base_url + path, json={"question": "test"})
            if response.status_code not in (401, 403):
                raise ValueError("Anonymous access was not rejected")
    return {"mcp": "PASS", "rag": "PASS", "isolation": "PASS", "anonymous": "PASS"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--portfolio", required=True, type=int)
    parser.add_argument("--denied-portfolio", required=True, type=int)
    args = parser.parse_args()
    token = os.environ.get("INVESTORY_TOKEN")
    if not token:
        parser.error("Set INVESTORY_TOKEN for a non-admin profile member")
    if min(args.portfolio, args.denied_portfolio) <= 0 or args.portfolio == args.denied_portfolio:
        parser.error("Supply two different positive portfolio IDs")
    try:
        result = asyncio.run(verify(args.base_url.rstrip("/"), args.portfolio, args.denied_portfolio, token))
    except Exception:
        # HTTP/SDK exceptions may contain bodies, URLs or private financial data.
        print(json.dumps({"acceptance": "FAIL", "detail": "Inspect private server health and configuration"}))
        raise SystemExit(1) from None
    print(json.dumps(result))


if __name__ == "__main__":
    main()
