# Shared module

`shared` contains small, dependency-light contracts and value semantics reused by business
modules. It is not an application service and must not depend on Investment, Long-Term, Profile,
Retirement, or infrastructure.

## Main boundaries

- `portfolio`: portfolio identity and context contracts.
- `currency`: money, currency, and conversion semantics.
- `assets`: shared asset identity and classification vocabulary.
- `policy`: cross-module policy contracts.
- `projection`, `time`, `presentation`, and `util`: reusable technical/application support.
- `notifications`: channel-neutral notification contracts.

Keep shared types narrow. Add a type here only when it expresses stable vocabulary or isolates a
third-party dependency used by more than one module. Business rules and persistence belong to the
owning module.

See [`docs/architecture/modularization.md`](../../docs/architecture/modularization.md) and the
domain contracts under [`docs/domain/`](../../docs/domain/).
