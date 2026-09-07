# Profile module --- quick KT

> **Goal:** understand the whole-wealth read model consumed by planning.

## What it owns

Profile combines a portfolio's planning-relevant facts into a stable read
boundary. It is not a second ledger and does not own Investment or Long-Term
source persistence.

``` text
Investment public reads ----\
                             +--> ProfileSnapshotReader
Long-Term public reads ----/             |
                                         v
                              InvestmentProfile
                              (planning read model)
```

The canonical public read is intentionally one boundary:

- `ProfileSnapshotReader` provides a complete `InvestmentProfile` from one repeatable-read snapshot.
- `ProfileQueryService` constructs the canonical model directly from one valuation date.

## Where to start

- API: `profile.api.ProfileSnapshotReader`.
- Model: `profile.api.model.InvestmentProfile` and its nested economic component records.
- Implementation: `ProfileQueryService`.
- REST adapter: `profile.web.ProfileRestController`.

## Safe-change rules

- Keep Profile read-only with respect to source domains.
- Do not make Retirement reach through Profile into Investment or Long-Term infrastructure; adjust a public read contract instead.
- Distinguish summary facts from planning facts. A presentation field is not automatically a simulation input.
- Preserve portfolio scoping and each aggregate value's source lineage.

Retirement turns this model into a frozen baseline; see `docs/domain/planning-timeline.md` and `docs/domain/retirement-simulation.md`.
