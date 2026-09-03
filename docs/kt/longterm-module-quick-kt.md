# Long-Term Assets module --- quick KT

> **Goal:** understand manual, non-brokerage assets and their planning-facing projections.

## What it owns

Long-Term manages real estate, rental contracts, bonds, deposits, cash
reserves, valuation periods, lifecycle, and rental/bond tax economics. It does
not use Investment persistence and does not perform retirement funding.

``` text
commands
  |
  v
LongTermAssetCommandService --> asset + detail + rental persistence
  |
  +--> LongTermAssetLifecycleService
  +--> economics / annual snapshots
  +--> LongTermAssetProjectionReader
                                  |
                                  v
                         Retirement planning inputs
```

## Where to start

- Public boundary: `LongTermAssetsApi`, split into `LongTermAssetReadApi` and `LongTermAssetWriteApi`.
- Planning reads: `LongTermAssetProjectionReader` and `LongTermAssetAnnualSnapshotReader`.
- Commands and calculations: `longterm.application.service.*`.
- Persistence: `longterm.infrastructure.*`; entities must not cross the public API boundary.
- Lifecycle: `LongTermAssetLifecycleService.activeOn(...)` matters for date-scoped historical and planning reads.

## Domain shape

- Real-estate assets can have dated valuation periods and rental contracts.
- Bonds and deposits expose value, interest treatment, and cash-flow facts.
- Cash reserves are explicit planning assets, not brokerage cash.
- Annual snapshots preserve historical facts; projection inputs describe facts available on a requested date.

## Safe-change rules

- Use lifecycle periods for historical reads; do not select the newest row merely because it is newest.
- Validate that dated periods do not overlap.
- Keep tax and rental semantics in module services/API models, not UI adapters or retirement formulas.
- Schema changes use append-only Flyway migrations under `app`; align the fast test snapshot.

Canonical detail: `docs/domain/long-term-assets.md`.
