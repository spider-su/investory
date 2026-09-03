# REST API conventions

Investory REST endpoints are internal HTTP adapters used by API clients and external tools. The
server-rendered UI normally calls public module APIs through in-process clients. REST routes are
pragmatic and versioned, but they do not promise an external-client compatibility layer.

## Current contract

- Business REST controllers expose class-level routes below `/api/v1`.
- Existing operations keep their established status codes; resource contracts may use `201 Created`
  or `204 No Content` where that better describes the result.
- Invalid input, malformed request bodies, unsupported commands, and import failures return `400`.
- Missing portfolio-scoped resources return `404` through typed application exceptions.
- Unexpected failures return `500` with a generic message. Server details stay in server logs.
- Errors use the JSON shape `{status, message, path, timestamp}`.
- Resource identifiers for updates come from the URL. Update request bodies contain mutable fields.
- `portfolioId` remains a query/body value where the existing application contract uses it, unless a
  resource-specific contract declares a portfolio-scoped path.

The application-level contract is implemented by `RestApiExceptionHandler`. Controller-specific
handlers should not introduce new status codes or error shapes.

### Long-Term Assets freeze contract

Long-Term Assets uses portfolio-scoped resources below
`/api/v1/portfolios/{portfolioId}/long-term-assets`. Portfolio, asset, contract, period, and policy
identifiers come only from URL paths. Request bodies contain mutable values and are validated before
commands cross the application boundary.

HTTP and HTML form fields that represent rates use percentage-point names such as
`annualInterestRatePercent` and accept values from `0` to `100`. Public application commands retain
canonical decimal rates from `0` to `1`; conversion happens once at the adapter boundary.

Create operations return `201 Created` with a resource `Location`; updates that return a current read
model use `200 OK`; commands without a useful response body and deletes use `204 No Content`. The
generic asset edit route is `PATCH` because omitted fields preserve their current values; it uses a
dedicated nullable patch request rather than the full create request. Subtype routes provide
complete create/update workflows. Every HTTP rate field is percentage points and is
named with a `Percent` suffix, while application commands use decimal fractions.
server-rendered UI does not call these endpoints in-process: its in-process clients inject public
module APIs such as `LongTermAssetsApi`. MVC controllers must not depend on REST controllers.

### Profile summary contract

`GET /api/v1/portfolios/{portfolioId}/profile` returns `200 OK` with a summary-only response. The
`portfolioId` path value must be a positive integer; invalid values return `400` using the common
`{status, message, path, timestamp}` error shape. The response never includes retirement planning
inputs, `longTermPlanningState`, `longTermAssets`, rental contracts, or tenant contact fields.

The response contains `portfolioId`, `currency`, `marketPortfolioValue`, `longTermAssetValue`,
`totalNetWorth`, `liquidAssets`, `illiquidAssets`, `allocations`, `currentRentalIncome`,
`currentBondIncome`, `retirementReserve`, `investmentCapital`, `income`, and
`allocationReconciliation`. Allocation rows contain `bucket`, `value`, `percentage`, `liquidity`,
and `assetHorizon`. Income contains the seven fields from `ProfileIncomeSummary`. Reconciliation
contains `shortTerm`, `longTerm`, and `balanced`; each source total contains `classifiedValue`,
`authoritativeValue`, `delta`, and `balanced`.

Allocation and reconciliation enum values are serialized by their enum names. There are no partial
profile endpoints; summary and planning data are separate application contracts.

### Reconciliation report

`GET /api/v1/investment/reconciliation?portfolioId={portfolioId}` returns a portfolio-scoped current-state/current-valuation
diagnostic report and accepts no reconciliation control parameters. Historical reports, golden
rebuilds, and private-archive verification are release tooling, not REST modes. The server-rendered
page may retain a `portfolioId` only as navigation context; it does not scope this report.

### Retirement simulation freeze contract

Retirement plans and projections use portfolio-scoped resources below
`/api/v1/retirement/portfolios/{portfolioId}`. Plan creation and update have separate request
contracts: only creation accepts an optional baseline; updates preserve the reviewed baseline.
Plan events are created with `POST .../plans/{planId}/events` and updated or deleted with the event
identifier in the URL. Revision snapshots carry that logical event identifier across revisions.

Projection and analysis requests contain typed assumptions/deltas. Analysis never accepts an
already-calculated `RetirementProjectionContext`; the server loads the projection from the
portfolio and plan identity. Editor preview keeps its legacy form encoding only inside the
application adapter; its HTTP fields use numeric JSON values and validation rejects incomplete
requests.

## Deliberate pragmatism

Springdoc publishes generated OpenAPI metadata and Swagger UI for discovery. The generated document
describes current internal HTTP adapters; it is not a separate compatibility promise. Hypermedia and
pagination wrappers remain optional and should be added only for a concrete client need.
