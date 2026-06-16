# AGENTS.md

## Project snapshot
- Spring Boot 4.1 monolith for portfolio/investment tracking (`pom.xml`, Java 25 via `<java.version>`).
- Main flow: broker XLSX/CSV import -> normalize DB state -> market/FX sync -> analytics -> REST + Thymeleaf dashboard + optional Telegram notifications.
- Package boundaries: `controllers` (REST + UI + Telegram bot entrypoints), `services` (orchestration + external APIs, plus the `TaxCalculator` / `CashFlowAggregator` helpers used by `PortfolioService`), `services/imports` (broker parser SPI), `services/notifications` (digest + alert rules), `data/repository` (JPA entities/repos), `clients` (Feign), `config` (security, scheduling, Telegram).
- `data` holds shared enums (`BrokerType`, `CurrencyType`, `ImportBatchStatus`, `ImportSourceType`), while `services/models` holds DTOs returned by the REST/UI layer (`Portfolio`, `Benchmark`, `InstrumentPerformance`, ...).

## System flow (read this first)
- Import flow (preferred): `POST /import/broker/{broker}` -> `ImportController` -> `ImportOrchestratorService.importFile()` -> `BrokerImportParser` impl (`XtbBrokerImportParser` or `IbkrBrokerImportParser`) -> writes `cash_operations`, `closed_positions`, `opened_positions`, `account_summaries`, and an `import_batch` audit row.
- Legacy XTB-only endpoint `POST /import/xtb` still works and goes through the same orchestrator (kept for older HTTP scripts).
- Telegram import flow: send an XLSX/CSV document to the bot -> `PortfolioBot.onUpdateReceived` detects broker from filename -> calls `ImportOrchestratorService.importFile(..., ImportSourceType.TELEGRAM, chatId)` -> bot replies with the batch summary.
- UI import flow: dashboard "Import statement" card -> `POST /import/broker/{broker}` (multipart) -> inline JS shows the import summary.
- Stock flow: `POST /import/stock/create` seeds `stocks` from open positions, `POST /import/stock/sync` runs quote refresh only (`MarketService.updateStocks()`), then `POST /portfolio/sync` runs `MarketService.fullPortfolioUpdate()` (quote refresh + IBKR re-pricing + history snapshot).
- Analytics flow: `PortfolioService.calculateTotalProfitLoss()` builds dashboard payload (total P/L, monthly buckets, symbol ranking, open-position flow, balance, capital-gains tax with loss carry-forward).
- Benchmark flow: `BenchmarkService.calculate()` builds the cumulative SPY-vs-portfolio curve (daily-cached monthly closes via TwelveData).
- History flow: `HistoryService.saveHistory()` writes daily open/close snapshots into `open_positions_history` and `portfolio_history`.
- Notifications flow: `NotificationService.sendDailyDigest()` (one-line summary) + `runAlerts()` (iterates all `AlertRule` beans). Telegram delivery is optional - when the bot is disabled, messages are logged instead.
- Scheduler flow: `SchedulerConfig` runs weekday jobs: FX at `06:00` (no explicit zone, so JVM default timezone applies), portfolio update at `15:30`, portfolio update + daily digest + alerts at `22:00` (`Europe/Warsaw` for the two market jobs).

## External integrations
- TwelveData is called directly in `TwelveDataService` (`/quote`, `/macd`, `/rsi`, `/time_series`); key comes from `app.api.twelve-data-key`.
- FX rates use Feign `ExchangeRateClient` -> `https://api.exchangerate.host/live`; key comes from `app.api.exchange-rate-key`.
- Yahoo is optional (`YahooFinanceService` for PE, `YahooExportService` for CSV export).
- Telegram bot (`controllers/bot/PortfolioBot`) is registered by `config/TelegramBotConfig` only when `app.telegram.enabled=true` (default `false`); both classes are `@ConditionalOnProperty`. When disabled, the rest of the app starts cleanly with no bot dependencies wired.

## Database and config conventions
- PostgreSQL + Flyway; migrations live in `src/main/resources/sql/migration/` (currently `V01.000` - `V01.006`).
- Keep schema consistent as `investory` (`spring.jpa.properties.hibernate.default_schema` and `spring.flyway.schemas` in `application.yml`).
- Runtime DB URL is provided via environment/profile configuration.
- Currency domain is fixed to `USD`, `EUR`, `PLN` (`CurrencyType`); conversions go through `CurrencyRateService.convertToBaseCurrency(...)`.
- Notifications config block (`app.notifications.*`) tunes alert thresholds; `app.notifications.enabled=false` silences both digest and rules without removing the beans.

## Project-specific patterns and gotchas
- Symbols are persisted as `TICKER.EXCHANGE` (for example `AAPL.US`); `MarketService` derives ticker with substring before `.`. IBKR symbols are bare tickers and are detected by `account="IBKR"`.
- Import parsers implement `BrokerImportParser`; add a new broker by creating a `@Component` for that interface plus an entry in `BrokerType`.
- XTB parser is header-driven: `XtbImportService.getColumnIndexes()` depends on exact broker labels (for example `Position`, `Gross P/L`, `Open time`).
- `XtbImportService` keeps import state in `static` fields (`headerRowNum`, `columnIndexes`, `account`, `currency`); avoid parallel XTB imports unless refactored.
- IBKR import (`IbkrImportService.importStatement`) still treats the CSV as a flat ledger for cash operations and FIFO closed trades, but it also supports richer Activity Statement files: when `Open Positions` / `Net Asset Value` sections are present it replaces IBKR `opened_positions` from the broker snapshot and upserts the `IBKR` row in `account_summaries`; otherwise it reconstructs open holdings and ending cash from transactions only. Synthetic negative ids keep re-imports idempotent.
- `ImportOrchestratorService.importFile` checksums the upload (SHA-256); a previously-APPLIED batch with the same hash is short-circuited and returned as `duplicate=true`.
- `MarketService.updateStocks()` intentionally rate-limits: chunks of 8 symbols with `Thread.sleep(120_000)` between chunks; failures inside a chunk are logged and skipped (next run picks up).
- `fullPortfolioUpdate()` runs `createStocks` -> `updateStocks` -> `syncIbkrPositions` -> `historyService.saveHistory()`. `syncStocks()` is intentionally commented out (XTB positions keep their imported snapshot).
- Both importers persist `account_summaries` (`XtbImportService.importAccountSummary(...)`, `IbkrImportService.importNav(...)` / `upsertIbkrSummary(...)`). `PortfolioService` and `BenchmarkService` prefer these broker-reported equity snapshots for `balance` / invested-capital calculations before falling back to open-position market value.
- Alert rules are pluggable: each rule is a `@Component` implementing `AlertRule` (single `Optional<String> evaluate()` method). `NotificationService` injects `List<AlertRule>` and dispatches only fired ones; per-rule `try/catch` so one broken rule cannot stop the others.
- `DrawdownAlertRule` keeps the peak equity in an in-memory field; it resets on every restart by design (no persistence yet).
- Security uses HTTP Basic against an in-memory user store (`SecurityConfig`): `GET /`, `/error`, static assets, `/dashboard/**` are public; all `POST/PUT/DELETE` require `ROLE_ADMIN`; everything else needs authentication. CSRF is disabled. OAuth2 config is present in `application.yml` but inactive.
- Thymeleaf setup is nonstandard: resolver points to `classpath:/static/dashboard/` (for `dashboard.html`), while `/` returns `templates/home.html`. Dashboard has an "Import statement" card that POSTs to `/import/broker/{broker}` and shows the resulting `ImportBatchResponse` inline.
- Indicator endpoints exist, but the implementation is still partial: `TechnicalService.updateTechnicals()` is empty, `TechnicalService.getTechnicalBySymbol()` / `FundamentalService.getBySymbol()` return empty maps, and `FundamentalService.updateFundamentals()` currently seeds rows without populating metrics because `fundamentalIndicatorBySymbol` is never filled.

## Build wiring gotchas (Boot 4.1 / Java 25 specific)
- **Lombok must be declared in `<annotationProcessorPaths>`** of `maven-compiler-plugin` (see `pom.xml`). Boot 4.1's BOM pins maven-compiler-plugin 3.15.0, which no longer auto-discovers annotation processors from the project classpath. Without the explicit path, every `@Slf4j`/`@Data`/`@Getter` annotation silently turns into "cannot find symbol" errors.
- **`@WebMvcTest` no longer auto-applies `springSecurity()` to MockMvc** in Boot 4.x. Without re-wiring it, every `@WithMockUser` test gets `401 Unauthorized` because the SecurityContext doesn't reach the request. The fix lives in `src/test/java/com/example/demo/config/MockMvcSecurityTestConfig.java`: a `@TestConfiguration` that registers a `MockMvcBuilderCustomizer` applying `SecurityMockMvcConfigurers.springSecurity()`. Every controller `@WebMvcTest` does `@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})`.
- **`@MockBean` is removed** in Boot 4.x; tests use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`.
- **`@WebMvcTest` import moved**: now `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (was `...test.autoconfigure.web.servlet.WebMvcTest`). The webmvc test slice is in the new `spring-boot-starter-webmvc-test` starter, which the pom uses instead of `spring-boot-starter-test`.
- **Vulnerable telegrambots transitives are pinned in `<dependencyManagement>`**: `commons-io 2.21.0`, `commons-lang3 3.20.0`, `commons-beanutils 1.11.0`. Don't downgrade these — they clear three CVEs in the `telegrambots:10.0.0` dependency graph.
- **The Maven runtime must match the project's Java level.** On a machine where `java -version` is still Java 21, `mvn test` can fail before executing any tests with `class file version 69.0 ... only recognizes ... up to 65.0`. Point `JAVA_HOME` / `PATH` at Java 25+ and prefer `mvn clean test` after switching runtimes so stale class files are rebuilt.

## Developer workflow
- No Maven wrapper detected; use Maven directly (`mvn ...`). Maven 3.9.9 ships with IntelliJ at `<IDEA_HOME>/plugins/maven/lib/maven3/bin/mvn.cmd` and works on Java 25/26.
- Build, test, run:
  - `mvn clean package`
  - `mvn test`
  - `mvn spring-boot:run`
- Code formatting via Spotless (google-java-format 1.25.2), **not** bound to a phase to avoid breaking existing builds. Use `mvn spotless:apply` / `mvn spotless:check` on demand.
- Manual API smoke calls live in `src/test/manual/api.http` (JetBrains HTTP Client).
- Useful endpoints: `POST /import/broker/{broker}`, `POST /import/stock/create`, `POST /import/stock/sync`, `POST /portfolio/sync`, `POST /portfolio/history`, `POST /currency/refresh`, `GET /portfolio/total-profit-loss`, `GET /portfolio/benchmark`, `GET /import/batches`, `GET /import/batches/{id}`, `GET /import/batches/latest`, `GET /import/batches/{id}/errors`.
- Test suite is JUnit 5 + Mockito + Spring `@WebMvcTest`. **29 test classes / 114 tests** cover services, alert rules, controllers, security, the scheduler, the `TaxCalculator` / `CashFlowAggregator` helpers, and notification rules. 11 of the 114 are parameterized `XtbImportServiceTest` cases that use `Assumptions.assumeTrue` so missing real broker XLSX files in `src/test/resources/` make them skip rather than fail.
