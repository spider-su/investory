# Investory MCP server

Investory exposes an optional, read-only MCP server inside the existing Spring Boot application.
The server uses Spring AI's official MCP server starter and the Streamable HTTP transport. It does
not add a deployment, database table, or separate service.

## Enable locally

Start Investory with `INVESTORY_MCP_ENABLED=true`. The default is `false`; when disabled, the MCP
server and its tools are not registered. Run the normal application with its configured local
database and authentication settings:

```bash
INVESTORY_MCP_ENABLED=true ./mvnw -pl app -am spring-boot:run
```

The Streamable HTTP endpoint is `http://localhost:8080/mcp`. It handles the standard MCP
initialization, capability negotiation, tool discovery, tool calls, JSON-RPC errors, and concurrent
client requests through the SDK transport.

## Authentication and authorization

Every request needs HTTP Basic or a bearer token accepted by Investory. Do not use anonymous access.
For clients, use a dedicated Investory account and its least-privileged role. `ADMIN` can read any
portfolio. `PROFILE_OWNER` and read-only `PROFILE_USER` can use tools only for portfolios where
they have active profile membership. Membership is checked again for every tool call, including
when an MCP session is reused.

Obtain a bearer token using the application's existing login endpoint:

```bash
curl --fail-with-body http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"YOUR_USERNAME","password":"YOUR_PASSWORD"}'
```

Use the returned `accessToken` as `Authorization: Bearer YOUR_TOKEN`. The endpoint preserves the
application's CSRF behavior: an MCP request with a Basic or bearer Authorization header is exempt
from browser CSRF checks; cookie-only browser requests are not.

The endpoint uses the application's existing Basic and bearer authentication. OAuth browser login
cookies are not a supported MCP client credential flow. TLS and normal Investory network exposure
controls are still required outside local development. Never log or commit credentials or tokens.

## Tools

All tools take one required positive integer argument, `portfolioId`. Results are JSON-serialized
Investment API read models, in portfolio base currency where the API defines monetary values.

| Tool | Description |
| --- | --- |
| `get_portfolio_dashboard` | Current dashboard summary, performance, positions, cash flow, risk, and data quality |
| `get_portfolio_performance` | Historical performance KPI and return context |
| `get_investment_ytd` | Current calendar-year investment result |

Example `tools/call` request:

```json
{
  "name": "get_portfolio_performance",
  "arguments": { "portfolioId": 123 }
}
```

The MCP adapter delegates to `InvestmentDashboardApi` through the replaceable
`InvestmentMcpClient`. Financial calculations stay in Investment. Invalid arguments, unknown tools,
authorization denials, and internal failures are returned using MCP/JSON-RPC errors; internal
exception and database details are not returned to clients.

## MCP Inspector

Start the Inspector web UI with `npx -y @modelcontextprotocol/inspector`, select Streamable HTTP, and
connect to `http://localhost:8080/mcp`. Add an `Authorization` header containing either:

- `Basic BASE64_USERNAME_COLON_PASSWORD`, or
- `Bearer YOUR_TOKEN` from `/api/v1/auth/login`.

The Inspector CLI can list tools and call one directly:

```bash
npx -y @modelcontextprotocol/inspector --cli http://localhost:8080/mcp \
  --transport http --method tools/list \
  --header "Authorization: Bearer $INVESTORY_TOKEN"

npx -y @modelcontextprotocol/inspector --cli http://localhost:8080/mcp \
  --transport http --method tools/call --tool-name get_portfolio_performance \
  --tool-arg 'portfolioId=123' \
  --header "Authorization: Bearer $INVESTORY_TOKEN"
```

After connecting, initialize the session, list tools, then call one of the three tool names above
with an authorized `portfolioId`. The automated module test also uses the official MCP Java client
against a real Streamable HTTP server and verifies initialization, discovery, invocation,
serialization, unauthenticated rejection, and cross-profile denial.

## Architecture

```text
Streamable HTTP transport (Spring AI MCP SDK)
  -> InvestmentMcpTools
  -> InvestmentMcpClient
  -> InvestmentDashboardApi
```

The app composition layer implements `InvestmentMcpAuthorization` with the established
`AuthorizationService`. The adapter does not depend on REST controllers or persistence. See
[`docs/architecture/modularization.md`](../../docs/architecture/modularization.md) and
[`docs/architecture/security.md`](../../docs/architecture/security.md).

## Troubleshooting

- `401`: provide valid Basic or bearer credentials for an active Investory account.
- `403` or a tool authorization error: verify the account's role and active membership in the
  requested profile/portfolio.
- MCP endpoint returns `404`: confirm `INVESTORY_MCP_ENABLED=true` and that the client uses `/mcp`.
- Tool reports portfolio data unavailable: check Investory's application/database health and logs;
  internal details are intentionally omitted from MCP responses.
- Browser-cookie clients can receive CSRF rejection. Use Basic or bearer Authorization headers.
