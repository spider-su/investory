# Profile module --- quick KT

> **Goal:** understand the whole-wealth read model consumed by planning.

## What it owns

Profile combines a portfolio's planning-relevant facts into a stable read
boundary. It is not a second ledger and does not own Investment or Long-Term
source persistence.

``` text
Investment public reads ----\
                             +--> ProfileComposition.load(...)
Long-Term public reads ----/             |
                                         v
                              InvestmentProfile
                              (planning read model)
```

The current public split is intentional:

- `ProfileSummaryReader` provides whole-wealth summary facts.
- `ProfilePlanningReader` provides planning inputs.
- `ProfileComposition` combines them when a consumer needs an `InvestmentProfile`; inject the two
  narrow readers rather than a single aggregate port.

## Where to start

- API: `profile.api.ProfileSummaryReader`, `ProfilePlanningReader`, and `ProfileComposition`.
- Models: `profile.api.model.ProfileSummary`, `ProfilePlanning`, and `InvestmentProfile`.
- Implementation: `ProfileQueryService`.
- REST adapter: `profile.web.ProfileRestController`.

## Safe-change rules

- Keep Profile read-only with respect to source domains.
- Do not make Retirement reach through Profile into Investment or Long-Term infrastructure; adjust a public read contract instead.
- Distinguish summary facts from planning facts. A presentation field is not automatically a simulation input.
- Preserve portfolio scoping and each aggregate value's source lineage.

Retirement turns this model into a frozen baseline; see `docs/domain/planning-timeline.md` and `docs/domain/retirement-simulation.md`.
