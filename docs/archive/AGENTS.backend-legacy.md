# Backend Agent Guidance

Applies to Java/backend work. Read together with root `AGENTS.md`.

## Project Shape

- Spring Boot 4.1 monolith for portfolio and investment tracking; Maven targets Java 25.
- Main flow: broker XLSX/CSV import -> normalized portfolio data -> FX/market sync ->
  daily/monthly projections -> dashboard/APIs/export/Telegram/OpenAI.
- Main package roles:
  - `controllers`: UI, import/export/admin REST, Telegram, Ghostfolio compatibility.
  - `services`: portfolio analytics, market/FX sync, projections, tax, cash flow, manual prices.
  - `services/imports`: broker parser SPI, XTB/IBKR importers, Yahoo export.
  - `services/openai`: Telegram AI, dashboard context, scheduled reports.
  - `services/portfolio`: deterministic Telegram commands.
  - `services/notifications`: digest and alert rules.
  - `infrastructure`: enums, JPA entities/repositories, projection/view entities.
  - `clients`: native `java.net.http` clients.
  - `config`: security, scheduling, Thymeleaf, Telegram, AI scheduling.

## Build And Test

- No Maven wrapper. Use Maven directly: `mvn test`, `mvn clean package`, `mvn spring-boot:run`.
- Local DB runs must use:
  `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
  Without `local`, the app may bind the empty default datasource and surface misleading Flyway
  errors; treat that first as an environment/run issue.
- Java 25+ is required. Check `java -version` and `mvn -version`; do not document machine-specific
  JDK paths.
- Spotless is not lifecycle-bound. Run `mvn spotless:check` or `mvn spotless:apply` explicitly.
- Keep Lombok in `maven-compiler-plugin` `annotationProcessorPaths`.
- Spring Boot 4 test rules:
  - `@WebMvcTest` is
    `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
  - Security MVC slices need
    `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})`.
  - Use `@MockitoBean`, not removed `@MockBean`.
- Keep the deliberate Telegram dependency-management pins unless their advisories are rechecked:
  `commons-io 2.21.0`, `commons-lang3 3.20.0`, `commons-beanutils 1.11.0`.
- Manual API smoke requests live in `src/test/manual/api.http`.
- Run targeted tests first. Report the verified result, not a hard-coded test count.

## Runtime And Security

- Schema changes belong to Flyway SQL, not Hibernate DDL. Read `AGENTS.sql.md` before changing
  persistence structure or reporting SQL.
- HTTP Basic uses in-memory `ADMIN` and `USER` credentials from `app.security.*`.
- Root/dashboard/error/static resources are public; mutating routes require `ROLE_ADMIN`;
  CSRF is currently disabled.
- Ghostfolio has a separate permissive security chain only under the `ghostfolio` profile.
  Do not weaken default security for compatibility.
- Production requires explicit `APP_SECURITY_*` overrides; do not rely on local defaults.
- Telegram wiring is conditional on `app.telegram.enabled=true`.
- OpenAI is configured by `app.openai.*`, disabled by default, and uses bounded dashboard context.
- Notifications are controlled by `app.notifications.enabled`.

## Import And Asset Identity

- Import flow: `ImportController` -> `ImportOrchestratorService.importFile()` ->
  `BrokerImportParser`.
- Current parsers: `XtbBrokerImportParser`, `IbkrBrokerImportParser`.
- XTB uses `XtbImportV2Service`; do not restore the legacy importer.
- IBKR supports activity statements and transaction-only CSVs. Activity statement Open Positions
  and Net Asset Value sections replace reconstructed open rows and update account summaries.
- Imports are SHA-256 deduplicated; duplicates must not mutate portfolio rows.
- Failure audit uses `ImportBatchAuditWriter` with `REQUIRES_NEW`; failed `import_history` rows
  survive parser rollback. Raw payload is capped at 8 KB with truncation metadata.
- Telegram document imports use the same orchestrator with `ImportSourceType.TELEGRAM`.
- Asset identity rules:
  - `assets.id` is canonical application identity.
  - `assets.symbol` is canonical display/provider-routing symbol, normally `TICKER.EXCHANGE`.
  - `assets.ticker`, `assets.ibkr`, and `assets.yahoo` are provider/broker mappings.
  - `positions.source_asset_symbol` and `cash_operations.source_asset_symbol` preserve provenance;
    raw broker symbols are never canonical identity.

## Market, FX, And Projection Services

- `MarketService.fullPortfolioUpdate()` refreshes prices, updates open-position valuation,
  re-values IBKR account data, then refreshes projections/views.
- TwelveData access lives in `clients/market/TwelveDataService`; symbols come from `assets`.
- Quote sync uses `MarketService.CHUNK_SIZE = 8`; `app.market.chunk-pause-ms` defaults to 120 s.
- `app.market.skip-non-us-listings=true` by default; preserve `REMX.UK` normalization unless
  provider mapping changes.
- `AssetPriceFallbackService` may use historical prices but must preserve provenance/quality.
- FX refresh fetches USD-base rates from exchangerate.host and derives EUR/PLN crosses locally.
- Currency conversion tries direct/inverse cached historical rates, then warns and returns the
  unconverted amount if no rate exists.
- `PortfolioProjectionService.recalculateAll()` rebuilds projections/reporting snapshots.
  Correct raw/import data and refresh projections instead of patching dashboard math.

## Dashboard Server-Side Contract

- `HomeController`/`PortfolioService` provide dashboard values. Do not reproduce accounting logic
  in JavaScript.
- Header totals, account-table totals, and open-position allocation are separate reporting surfaces;
  keep them aligned after projection, price, FX, or classification changes.
- `PortfolioCommandService` uses the same dashboard context as AI; keep command values aligned with
  dashboard rendering.
- Detailed data-quality issue provenance is behind
  `app.portfolio.data-quality-issues-enabled=false` by default because reconstruction is expensive.
- Yahoo export emits cash as `USDT-USD` and supports account filtering via
  `app.export.yahoo.accounts`.

## Telegram, AI, Notifications

- Telegram rejects unauthorized chats unless `app.telegram.chat-id` matches.
- Deterministic portfolio commands run before OpenAI fallback.
- Scheduled AI reports require both AI analysis and Telegram enabled. Use supplied portfolio
  context, state missing data, and do not fabricate facts or direct buy/sell instructions.
- Active alerts: concentration, drawdown, stale import. Drawdown peak equity is in memory and
  resets on restart.

## Backend Change Checks

- Before deleting or renaming an endpoint/entity/service, inspect callers, tests, migrations,
  dashboard assets, Ghostfolio compatibility, schedulers, reset/export paths, and integrations.
- For a backend task that changes schema, reporting lineage, or live-data semantics, also read
  `AGENTS.sql.md`.
- For a backend task that changes dashboard-visible behavior, also read `AGENTS.frontend.md`.
