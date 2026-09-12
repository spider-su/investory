# Integrations module

This module owns external-system adapters and their runtime management. Package ownership is vertical:

- `management`: integration metadata, base plugin SPI, configuration, persistence, and scheduling;
- `market`, `fx`, `export`, and `importing`: external provider/file adapters grouped by provider;
- `ksef`: KSeF 2.0 e-invoicing authentication, invoice metadata query, and structured invoice download;
- `telegram`: Telegram bot, commands, and notification delivery;
- `notifications`: provider-neutral notification application and persistence;
- `ai.openai`: OpenAI client and portfolio-analysis orchestration;
- `health`: integration-related health indicators.

Dependency direction:

```text
provider adapter -> feature SPI/model -> management SPI/model
management application -> management SPI/model + persistence
notification application -> notification delivery interface
telegram delivery -> notification delivery interface
integrations -> investment.api / investment.port
```

Broker file adapters (`importing.ibkr` and `importing.xtb`) are registered here and implement the
`investment.port.importing.BrokerImportParser` port. Investment retains import orchestration,
import audit, and canonical ledger writes; the adapter boundary keeps those responsibilities out
of the integrations module.

KSeF is deliberately isolated in `integrations.ksef`. Accounting must not depend on KSeF transport,
authentication, or MF API DTOs. `KsefInvoiceService` exposes the managed integration boundary for
incoming invoice discovery and canonical structured-invoice download. Mapping FA(3) into accounting
facts belongs to accounting/import orchestration, not the KSeF transport.

The KSeF plugin currently supports KSeF 2.0 TEST, DEMO, and PRODUCTION endpoints and 2026 KSeF-token
authentication. The token is stored through the normal encrypted integration-secret persistence.
Certificate/XAdES authentication should replace token authentication before token retirement; keep
that change behind the same `ksef` boundary.

Management contracts must not depend on persistence or provider implementations. Notification
application code must not depend directly on Telegram.

## Job execution

`IntegrationJobScheduler` owns polling, due-time calculation, PostgreSQL advisory locking, and
execution state. It delegates every declared job through `IntegrationJobHandlerRegistry`; startup
validation requires exactly one handler for each plugin-declared job and duplicate keys fail fast.
Job handlers call public Investment APIs or ports. Financial calculations stay in Investment, while
notification-specific audit and delivery orchestration stays in its handler.

Provider routing is explicit: `ConfiguredMarketDataProvider` uses Yahoo for current quotes and
Yahoo Finance for historical daily/monthly closes. FX configuration currently resolves the
NBP adapter. Provider-specific configuration remains JSON and secrets remain encrypted persistence
values.

## Management contract

The persisted `integration_instances.enabled` flag is the only provider enablement
flag. Provider configuration must not contain an `enabled` field. A persisted
disabled instance shadows environment configuration; environment settings are
bootstrap-only fallback when no persisted instance exists.

The management UI exposes connection tests as transient, read-only probes. Test
payloads and secrets are never persisted by the test operation. Persisted jobs are
currently deliberately scoped to the executable `refresh-prices` and
`refresh-rates` handlers; new jobs must add a handler before being declared.
