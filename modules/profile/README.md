# Profile module

`profile` composes a read-only whole-wealth view from Investment and Long-Term facts.

This README is the module boundary summary; the maintainer walkthrough is
[`docs/kt/profile-module-quick-kt.md`](../../docs/kt/profile-module-quick-kt.md).

## Dependency direction

```text
Investment API ───┐
                  ├── Profile API ──► Retirement / Web UI
LongTerm API ─────┘
```

- `profile.api` exposes the immutable whole-profile read model and its snapshot reader.
- `ProfileSnapshotReader` is the required consumer boundary for a complete `InvestmentProfile`.
- The old summary/planning ports, wrapper records, and `ProfileComposition` were removed. The query
  service constructs the canonical read model directly.
- `profile.application` implements aggregation through Investment and Long-Term public APIs.
- Profile owns no source-domain persistence and writes no Investment or Long-Term tables.
- Brokerage portfolio accounting remains in Investment.
- Page formatting remains in `adapters/web-ui`.
- `profile.web` owns the canonical `GET /api/v1/portfolios/{portfolioId}/profile` REST facade. The
  controller maps the public `InvestmentProfile` read model to a dedicated `ProfileResponse`; the
  backward-compatible JSON contract never exposes tenant contact data or Retirement implementation
  inputs. The UI reaches the application API
  through a replaceable client interface whose current implementation performs a direct in-process
  call.
- Brokerage and Long-Term source totals are both portfolio-scoped. Allocation reconciliation keeps
  source classifications unchanged and exposes any source-total delta in
  `ProfileAllocationReconciliation`.
- Brokerage value means total equity, including signed cash. Investment capital excludes brokerage
  cash; positive brokerage cash contributes to retirement reserve exactly once. The reserve is
  `max(brokerage market cash, 0)` plus Long-Term `LIQUID_CASH` assets marked available for funding.
  Negative brokerage cash remains reflected in signed brokerage equity, but is not an asset
  allocation or retirement funding source.
- Long-Term summary and annual-snapshot money carries its declared source currency and is converted
  into Profile display currency before aggregation. Long-Term asset rows and projections are
  converted from each row's currency at the same boundary.
- Brokerage YTD income is annualized over its observed calendar-year period for annual projections;
  Investment's canonical summary keeps its annual expected result separate from its YTD result.
  Long-Term income is annual, so combined annual income uses annualized/annual values.
- Personal assets contribute to net worth and allocation, but not investment-yield denominators or
  retirement capital. Assets locked until maturity remain illiquid and outside current reserve.
- The Long-Term adapter itself returns one coherent source snapshot containing totals, allocation,
  annual-income facts, and projection inputs. Profile owns a new `REPEATABLE_READ` transaction for
  every complete read so an outer consumer transaction cannot weaken snapshot isolation.
- `ProfileAssetProjection.rentalIncomeGrowthRate` is a compatibility baseline value. Profile emits
  zero because rental growth is a Retirement scenario assumption; Retirement applies its selected
  scenario rate when creating effective simulation assumptions.
- The persisted contract test covers empty, brokerage-only, Long-Term-only, and mixed portfolios,
  and verifies repeated reads do not change source tables.
