# External integrations

This document classifies Investory's external integrations by responsibility. It defines authority and failure boundaries, not provider-specific API details. Exact URLs, credentials, schedules, and implementation classes remain source/configuration concerns.

## Market data

**TwelveData** is the configured primary market quote/time-series provider for supported listings. Provider data is normalized into Investory's canonical price-history model before it becomes reporting input. Provider payloads are evidence, not a separate source of portfolio accounting truth.

Unsupported listings, provider-plan restrictions, and explicit exclusions may require manual prices. Do not silently substitute a different listing or currency merely to make valuation succeed.

**Yahoo Finance** is not the primary canonical quote source. It is used for export/adapter behavior and may provide fallback/reference functionality where explicitly implemented. The Yahoo export snapshot participates in C7 reconciliation against the adapter payload.

## FX data

The configured exchange-rate integration supplies external FX observations. Investory derives and resolves rates according to `../domain/fx-normalization.md`. Estimated-but-usable rates, stale data, missing data, direction, and cross-rate semantics are owned by that domain contract rather than by the provider adapter.

A provider response must never override fail-closed FX semantics.

## Telegram

Telegram is an optional notification/command transport. When disabled or unavailable, core accounting, portfolio reporting, long-term assets, retirement planning, and reconciliation remain valid and independently usable.

Telegram delivery failure is an adapter failure, not a financial-state failure. Notification persistence/deduplication semantics must remain separate from accounting facts.

## OpenAI

OpenAI-backed analysis/reporting is optional and downstream. It may receive explicitly prepared portfolio context for analysis but is not an accounting, pricing, planning, or reconciliation authority. Generated text must not write or silently change canonical financial facts.

Disabling or losing access to OpenAI must not prevent deterministic retirement simulation or Retirement Analysis from running.

## Integration secrets

Integration credentials are configuration/secrets, not domain data. Persisted integration secrets require the configured master key. Never log tokens, API keys, chat identifiers, or decrypted secret values.

## Failure rules

- External adapters fail visibly; they do not invent replacement financial facts.
- Canonical normalization happens inside Investory before provider observations are consumed by reporting.
- Optional adapters must not become hidden dependencies of core accounting or deterministic planning.
- Provider-specific retry/rate-limit behavior belongs in the adapter implementation and tests.
- When two surfaces expose the same financial metric, reconciliation should compare them rather than assuming they agree.

## Testing

Use deterministic transport-level fixtures for external HTTP contracts where practical. Tests should cover successful parsing plus representative provider failures/rate limits without requiring live credentials.
