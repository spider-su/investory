"""Smoke test for an isolated deployment of the published Java image."""

import base64
import json
import re
import sys
import time
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DOC = "domain/portfolio-accounting.md"
MARKET = "market:alpha-vantage:MSFT"
QUOTE = {"price": "123.45", "currency": "USD", "tradingDate": "2025-12-30"}
SOURCE = "https://www.alphavantage.co/"
PARAPHRASE = "Could you tell me which investments I currently own?"


class RagFixture(BaseHTTPRequestHandler):
    def request_body(self):
        if self.headers.get("Transfer-Encoding") != "chunked":
            return self.rfile.read(int(self.headers["Content-Length"]))
        body = bytearray()
        while size := int(self.rfile.readline().split(b";", 1)[0], 16):
            body.extend(self.rfile.read(size))
            self.rfile.readline()
        self.rfile.readline()
        return body

    def do_POST(self):
        if self.headers.get("Authorization") != "Bearer fixture-rag-token":
            self.send_error(401)
            return
        body = json.loads(self.request_body())
        if (
            self.path == "/ask"
            and body == {"question": "explain internal transfers"}
        ):
            response = {"answer": f"Internal transfers are neutral [{DOC}]", "sources": [DOC]}
        elif self.path == "/route" and body == {"question": PARAPHRASE}:
            response = {"tools": ["HOLDINGS"], "symbol": None}
        elif self.path == "/market-data" and body == {"tool": "GLOBAL_QUOTE", "symbol": "MSFT"}:
            response = {
                "provider": "Alpha Vantage", "tool": "GLOBAL_QUOTE", "symbol": "MSFT",
                "source": SOURCE, "data": "symbol,price,latestDay\nMSFT,123.45,2025-12-30",
            }
        else:
            self.send_error(502)
            return
        encoded = json.dumps(response).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def request(base, path, payload=None, auth=None, expected=200, raw=False):
    headers = {}
    if auth:
        headers["Authorization"] = auth
    if payload is not None:
        headers["Content-Type"] = "application/json"
    req = Request(
        base + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers,
    )
    try:
        with urlopen(req, timeout=45) as response:
            require(response.status == expected, f"{path} returned {response.status}, expected {expected}")
            return response.read().decode() if raw else json.load(response, parse_float=Decimal)
    except HTTPError as error:
        require(error.code == expected, f"{path} returned {error.code}, expected {expected}")


def wait_for_readiness(base):
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            if request(base, "/actuator/health/readiness")["status"] == "UP":
                return
        except (OSError, AssertionError, URLError):
            pass
        time.sleep(2)
    raise SystemExit("Published image never became ready")


def check(base):
    wait_for_readiness(base)
    user = "Basic " + base64.b64encode(b"happy.investor:fixture-password").decode()
    scraper = "Bearer fixture-prometheus-token"
    path = "/api/v1/portfolios/2"
    outside = "/api/v1/portfolios/1/assistant/ask"
    dashboard = path + "/investment/dashboard/query"
    selection = {"accountIds": [], "benchmarkAccountsSubmitted": False}
    full_dashboard = request(base, dashboard, {**selection, "period": "MAX"}, user)
    maximum = full_dashboard["overview"]
    benchmark = full_dashboard["performance"]["benchmark"]
    ytd = request(base, dashboard, {**selection, "period": "YTD"}, user)["overview"]
    profile = request(base, path + "/profile", auth=user)
    holdings = maximum["portfolioStructure"]["topHoldings"][:5]

    def amount(answer, value, currency):
        text = re.sub(r"\[[^\]]+\]", "", answer["answer"])
        numbers = {Decimal(n) for n in re.findall(r"[-+]?\d+(?:\.\d+)?", text)}
        require(Decimal(value) in numbers and currency in text, "amount or currency differs from canonical data")

    def cited(answer, *sources):
        missing = set(sources) - set(answer["sources"])
        require(not missing, "missing citations: " + ", ".join(sorted(missing)))

    def quote_status():
        return request(base, path + "/rag/research/quote-status?symbol=MSFT.US", auth=user)

    questions = json.loads(Path(__file__).with_name("questions.json").read_text())
    context = None
    for index, question in enumerate(questions, 1):
        payload = {"question": question}
        if index == 2:
            payload["context"] = context
        if index == 7:
            before_alias = quote_status()
        now = time.monotonic()
        response = request(base, path + "/assistant/ask", payload, user)
        try:
            inline = set(re.findall(r"\[([^\[\]]+)\]", response["answer"]))
            require(inline == set(response["sources"]), "inline and structured citations differ")
            if index in (1, 2):
                amount(response, maximum["totalProfit"], maximum["baseCurrency"])
                require("MAX" in response["answer"], "MAX label missing")
                context = response["context"]
                require(context["portfolioId"] == 2 and context["period"] == ("MAX" if index == 1 else "YTD"), "incorrect scoped context")
            if index in (2, 3):
                amount(response, ytd["totalProfit"], ytd["baseCurrency"])
                require("YTD" in response["answer"], "YTD label missing")
            if index in (1, 2, 3):
                cited(response, "live:investment-performance")
            if index in (4, 8):
                cited(response, "live:investment-structure")
                require(bool(holdings), "synthetic holdings missing")
                for holding in holdings:
                    require(holding["symbol"] + ":" in response["answer"], "holding missing")
                    amount(response, holding["value"], maximum["baseCurrency"])
            if index in (4, 6):
                cited(response, "live:profile")
                amount(response, profile["totalNetWorth"], profile["currency"])
            if index == 4:
                cited(response, DOC)
            if index == 5:
                cited(response, "live:retirement-plan")
                if benchmark["benchmarkAvailable"] and benchmark["portfolioPerformanceAvailable"]:
                    cited(response, "live:investment-benchmark")
                    amount(response, benchmark["benchmarkPl"], maximum["baseCurrency"])
                else:
                    require(
                        "not have enough verified data" in response["answer"]
                        and "live:investment-benchmark" not in response["sources"],
                        "missing benchmark data was not acknowledged",
                    )
            if index in (6, 7):
                cited(response, MARKET)
                market = response["marketData"]
                require(market["quote"] == QUOTE and market["source"] == SOURCE and not market.get("data"), "incorrect typed quote")
                require(market["symbol"] == "MSFT", "alias failed to resolve")
            if index == 7:
                after_alias = quote_status()
                require(
                    market["cache"]["status"] == "CACHED"
                    and before_alias["remainingBudget"] == after_alias["remainingBudget"] == 1
                    and after_alias["budgetLimit"] == 20,
                    "cached alias consumed quota or first quote missed budget accounting",
                )
            if index == 8:
                coverage = response["marketStatus"]
                require(
                    not response.get("marketData")
                    and "temporarily unavailable" in response["answer"]
                    and coverage["coverage"] == "PROVIDER"
                    and coverage["providerSymbol"] == "AAPL"
                    and coverage["symbolCooldownUntil"]
                    and quote_status()["remainingBudget"] == 0,
                    "failed holding quote did not preserve facts or enter cooldown",
                )
        except (AssertionError, KeyError, TypeError, ValueError) as error:
            raise AssertionError(f"Question {index}: {error}") from None
        print(f"Question {index}: PASS ({time.monotonic() - now:.2f}s)", flush=True)
        request(base, outside, payload, user, expected=403)
        if index == 2:
            forged = {**context, "portfolioId": 1}
            request(base, path + "/assistant/ask", {**payload, "context": forged}, user, expected=400)

    for question, citation in (
        (PARAPHRASE, "live:investment-structure"),
        ("Show my financial overview", "live:profile"),
        ("Show my long-term portfolio", "live:longterm"),
        ("Show my retirement simulation", "live:retirement-simulation"),
    ):
        scoped = request(base, path + "/assistant/ask", {"question": question}, user)
        cited(scoped, citation)
        request(base, outside, {"question": question}, user, expected=403)
    print("Hybrid fallback and cross-domain MCP reads: PASS")

    request(base, path + "/assistant/ask", {"question": questions[0]}, expected=401)
    exhausted = request(base, path + "/assistant/ask", {"question": "Get latest price of ticker AMZN"}, user)
    require(not exhausted.get("marketData") and "quota" in exhausted["answer"] and quote_status()["remainingBudget"] == 0, "budget did not block a third provider attempt")

    request(base, "/actuator/prometheus", auth=user, expected=403)
    request(base, "/actuator/prometheus", auth="Bearer invalid", expected=401)
    request(base, "/api/v1/auth/me", auth=scraper, expected=401)
    metrics = request(base, "/actuator/prometheus", auth=scraper, raw=True)
    require("investory_assistant_request_seconds_bucket" in metrics, "assistant latency histogram missing")
    require("investory_assistant_routes_total" in metrics, "assistant route metric missing")
    require('investory_assistant_routes_total{route="JAVA_LONGTERM"}' in metrics, "long-term route metric missing")
    for event in ("CACHE_HIT", "PROVIDER_FAILURE", "BUDGET_EXHAUSTED"):
        require(f'investory_market_events_total{{event="{event}"}}' in metrics, f"{event} metric missing")
    require("investory_market_provider_seconds" in metrics, "provider latency metric missing")
    require(all(q not in metrics for q in questions), "private request text leaked into metrics")
    print("Scoped authorization and Prometheus exposition: PASS")


if __name__ == "__main__":
    if sys.argv[1] == "--fixture":
        ThreadingHTTPServer(("0.0.0.0", 8090), RagFixture).serve_forever()
    else:
        check(sys.argv[1])
