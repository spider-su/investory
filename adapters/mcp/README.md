# Investory MCP (draft)

Optional, embedded, read-only MCP JSON-RPC adapter. Disabled by default with
`app.mcp.enabled=false`; enable using `INVESTORY_MCP_ENABLED=true`.

POST `/mcp` with an authenticated **ADMIN** account. The draft deliberately restricts
access to ADMIN until profile-membership authorization is wired into the MCP
boundary. HTTP Basic or bearer Authorization headers are supported; MCP POST
requests with an Authorization header bypass browser CSRF checks. Requests
using browser cookies alone retain normal CSRF protection. Do not expose this endpoint publicly without reviewing transport
authentication and deployment policy.

Supported MCP methods: `initialize`, `ping`, `tools/list`, `tools/call`,
and `notifications/initialized`. Tools: `get_portfolio_dashboard`,
`get_portfolio_performance`, `get_investment_ytd`.
Each tool requires a positive `portfolioId`.

The implementation uses direct dependency injection of Investment's public
`InvestmentDashboardApi` through `InvestmentMcpClient`; it does not make
loopback HTTP calls or duplicate financial calculations. A remote REST-backed
implementation can replace the in-process client later.

This is a deliberately minimal **stateless JSON-RPC MCP draft**, not a
full-featured MCP SDK server. Streaming responses, sessions, resource and prompt
capabilities, resumability, and OAuth authorization-server discovery are not
implemented. Before wider client interoperability or external exposure, migrate
the transport to the official MCP Java SDK and add profile-level authorization.

Example:

```bash
curl -u "$INVESTORY_USERNAME:$INVESTORY_PASSWORD" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  http://localhost:8080/mcp
```
