# Integrations module

This module owns external-system adapters and their runtime management. Package ownership is vertical:

- `management`: integration metadata, base plugin SPI, configuration, persistence, and scheduling;
- `market`, `fx`, and `export`: Investment port adapters grouped by provider;
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

Management contracts must not depend on persistence or provider implementations. Notification
application code must not depend directly on Telegram.

## Management contract

The persisted `integration_instances.enabled` flag is the only provider enablement
flag. Provider configuration must not contain an `enabled` field. A persisted
disabled instance shadows environment configuration; environment settings are
bootstrap-only fallback when no persisted instance exists.

The management UI exposes connection tests as transient, read-only probes. Test
payloads and secrets are never persisted by the test operation. Persisted jobs are
currently deliberately scoped to the executable `refresh-prices` and
`refresh-rates` handlers; new jobs must add a handler before being declared.
