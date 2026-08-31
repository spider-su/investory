# Profile module

`profile` composes a read-only whole-wealth view from Investment and Long-Term facts.

## Dependency direction

```text
investment.api --\
                  -> profile.api -> retirement / web-ui
longterm.api ----/
```

- `profile.api` contains separate summary and planning query ports plus immutable economic models.
- `ProfileSummaryReader` is the summary boundary used by REST and display consumers.
- `ProfilePlanningReader` is the planning-input boundary. `ProfileReader` remains only as a
  deprecated compatibility aggregate for existing Retirement callers during migration.
- `profile.application` implements aggregation through Investment and Long-Term public APIs.
- Profile owns no source-domain persistence and writes no Investment or Long-Term tables.
- Brokerage portfolio accounting remains in Investment.
- Page formatting remains in `adapters/web-ui`.
- `profile.web` owns the canonical `GET /api/v1/portfolios/{portfolioId}/profile` REST facade. Its
  explicit response DTO publishes whole-wealth summaries and never exposes tenant contact data or
  Retirement planning inputs. The UI reaches the application API through a replaceable client
  interface whose current implementation performs a direct in-process call.
- Brokerage and Long-Term source totals are both portfolio-scoped. Allocation reconciliation keeps
  source classifications unchanged and exposes any source-total delta in
  `ProfileAllocationReconciliation`.
- Profile composition reads one coherent Long-Term profile snapshot. The Long-Term adapter reuses
  one summary-row read for totals, allocation, and annual-income facts; detailed projection inputs
  remain one separate batched read.
