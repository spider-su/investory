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
  cash; retirement reserve includes only non-negative brokerage cash and Long-Term cash reserves
  currently available for funding.
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
