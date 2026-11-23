# Stateful financial assistant conversations (draft)

This document describes the opt-in conversation API introduced by the four-commit
stateful-assistant PR. The existing `POST /api/v1/portfolios/{portfolioId}/assistant/ask`
endpoint is unchanged.

## Design boundaries

- **Java** remains authoritative for authentication, portfolio ownership, financial
  calculations and the allowlisted MCP execution path.
- **Python RAG** remains responsible for documentation retrieval and constrained
  semantic intent classification. Documentation embeddings live in PostgreSQL/pgvector.
- **PostgreSQL relational tables** store structured conversation state and what-if
  assumptions, not prompts, model completions, secrets, full portfolios or embeddings.
- **Internal MCP** is invoked through the existing Java orchestrator; this PR does not
  create arbitrary autonomous tool execution or grant an LLM SQL access.
- **No mutation of actual retirement plans, balances or trading positions** occurs
  through the conversation endpoints.

## Database

Migration `V01.016__assistant_conversations.sql` introduces:

- `investory.assistant_conversation`: UUID, scoped portfolio and authenticated
  principal, current report topic/asset/period, selected-plan placeholder,
  optimistic version, creation/update and expiry timestamps.
- `investory.assistant_scenario_revision`: revision number, baseline saved-plan
  name and user-proposed annual expenses. No computed projection is stored.

All conversation lookups and updates require a matching portfolio and principal.
An unknown or cross-user conversation returns 404. Report-context updates use an
optimistic state version so concurrent writes cannot overwrite each other silently.

The default expiry is 30 days. A subsequent cleanup job should delete expired
rows and associated revisions; expiry is enforced by reads even before cleanup.

## API

Each request requires the usual authenticated portfolio permissions.

### Create a conversation

```http
POST /api/v1/portfolios/21/assistant/conversations
```

Response contains `id`, `portfolioId`, `version` and structured context.

### Ask and continue

```http
POST /api/v1/portfolios/21/assistant/conversations/{conversationId}/ask
Content-Type: application/json

{"question":"What was my all-time investment profit?"}
```

The controller calls the **existing** `FinancialAiOrchestratorController.ask`.
That code performs portfolio authorization, limits, deterministic routing,
constrained LLM fallback and internal MCP reads. When it returns a verified,
unambiguous follow-up context, the conversation record is updated.

A second call may use:

```json
{"question":"Compare that with YTD"}
```

For mixed answers or unsupported questions no new report context is stored.
Only report-period follow-ups currently participate in persisted memory.
Arbitrary entity references and complex multi-turn planning are **not implemented**.

### Scenario drafts (not yet simulations)

```http
POST /api/v1/portfolios/21/assistant/conversations/{conversationId}/scenario-revisions
Content-Type: application/json

{
  "plan": "My existing saved plan",
  "annualLivingExpenses": 180000.00,
  "annualDiscretionaryExpenses": 24000.00
}
```

The API validates the selected plan using the canonical read-only retirement
contract and stores a revision. Response status: `202 Accepted` with
`status=DRAFT_NOT_SIMULATED`.

```http
GET /api/v1/portfolios/21/assistant/conversations/{conversationId}/scenario-revisions
```

**Never present these drafts as recalculated forecasts.** The current public
`PortfolioResearchClient.retirementSimulation(portfolioId)` returns saved
simulation results; it does not accept overrides. The next implementation
must add an explicit read-only what-if use case to the retirement domain,
apply overrides to an isolated copy of the assumptions, run the canonical
simulation, and return sourced comparison results without saving a plan.

## Multi-tool orchestration

Already implemented in the existing assistant: constrained routing can select
multiple supported reads, such as general profile and saved retirement
simulation, and Java executes them with portfolio scope and provenance.
This PR reuses that execution path; it does **not** introduce an unbounded
agentic loop or arbitrary ordered tool plans.

Future work should introduce a typed `AssistantPlan` with:
- a strict allowlist of operations and validated arguments;
- a maximum number of steps and a per-request deadline;
- deterministic ordering / dependencies;
- authorization rechecked at each tool invocation;
- bounded external calls and fail-closed handling of missing data;
- structured outputs and provenance, not LLM-calculated amounts.

## Test and acceptance requirements before marking PR ready

1. Run Java compilation and relevant assistant unit/integration suites.
2. Apply migration on disposable PostgreSQL and on a restored production backup.
3. Test conversation creation, restart persistence, expired IDs, cross-portfolio
   and cross-user access, missing authentication and stale concurrent version.
4. Verify MAX -> YTD and compare follow-ups, error/abstention context behavior.
5. Verify Web and Telegram remain backward compatible.
6. Test plans absent/unavailable, invalid expense values, and draft versioning.
7. Confirm no raw prompts, personal financial snapshots, authentication tokens
   or untrusted provider payloads are stored or logged.
8. Verify that a draft is explicitly marked uncomputed and cannot be confused
   with a canonical simulation result.
9. Inspect production metrics and trace scope without adding sensitive labels.

Deployment must follow the ordinary GitOps flow. Do not enable new client
flows or claim what-if projections are ready until their canonical calculation,
end-to-end tests and monitoring have passed.
