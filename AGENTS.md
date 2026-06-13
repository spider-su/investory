# AGENTS.md

## Project snapshot
- Spring Boot 3.2 monolith for portfolio/investment tracking (`pom.xml`, Java 17 via parent defaults).
- Main flow: broker XLSX import -> normalize DB state -> market/FX sync -> analytics -> REST + Thymeleaf dashboard.
- Package boundaries: `controllers` (entrypoints), `services` (orchestration + external APIs), `data/repository` (JPA entities/repos), `clients` (Feign).

## System flow (read this first)
- Import flow: `POST /import/xtb` -> `ImportController` -> `XtbImportService.importXtbExport()` writes `cash_operations`, `closed_positions`, `opened_positions`.
- Stock flow: `POST /import/stock/create` seeds `stocks` from open positions, then `POST /portfolio/sync` runs `MarketService.fullPortfolioUpdate()` (quote refresh + history snapshot).
- Analytics flow: `PortfolioService.calculateTotalProfitLoss()` builds dashboard payload (total P/L, monthly buckets, symbol ranking, open-position flow).
- History flow: `HistoryService.saveHistory()` writes daily open/close snapshots into `open_positions_history` and `portfolio_history`.
- Scheduler flow: `SchedulerConfig` runs weekday jobs: FX at `06:00`, portfolio update at `15:30` and `22:00` (`Europe/Warsaw`).

## External integrations
- TwelveData is called directly in `TwelveDataService` (`/quote`, `/macd`, `/rsi`, `/time_series`); key is hardcoded in service.
- FX rates use Feign `ExchangeRateClient` -> `https://api.exchangerate.host/live`; key is hardcoded in `CurrencyRateUpdaterService`.
- Yahoo is optional (`YahooFinanceService` for PE, `YahooExportService` for CSV export).
- Telegram bot code exists (`controllers/bot/PortfolioBot`) but bot registration in `GoogleAuthSpringBootApplication` is commented.

## Database and config conventions
- PostgreSQL + Flyway; migrations are in `src/main/resources/sql/migration/` (`V01.000__Initial_schema.sql`, `V01.001__monitoring.sql`).
- Keep schema consistent as `investory` (`spring.jpa.properties.hibernate.default_schema` and `spring.flyway.schemas` in `application.yml`).
- Runtime DB URL is provided via environment/profile configuration.
- Currency domain is fixed to `USD`, `EUR`, `PLN` (`CurrencyType`); conversions go through `CurrencyRateService.convertToBaseCurrency(...)`.

## Project-specific patterns and gotchas
- Symbols are persisted as `TICKER.EXCHANGE` (for example `AAPL.US`); `MarketService` derives ticker with substring before `.`.
- Import parser is header-driven: `XtbImportService.getColumnIndexes()` depends on exact broker labels (for example `Position`, `Gross P/L`, `Open time`).
- `XtbImportService` keeps import state in `static` fields (`headerRowNum`, `columnIndexes`, `account`, `currency`); avoid parallel imports unless refactored.
- `MarketService.updateStocks()` intentionally rate-limits: chunks of 8 symbols with `Thread.sleep(120_000)` between chunks.
- `fullPortfolioUpdate()` updates quotes and history only (`syncStocks()` is currently commented out).
- Security is effectively open: `WebSecurityConfig` permits `/**` and disables CSRF; OAuth config is present but inactive.
- Thymeleaf setup is nonstandard: resolver points to `classpath:/static/dashboard/` (for `dashboard.html`), while `/` returns `templates/home.html`.

## Developer workflow
- No Maven wrapper detected; use Maven directly.
- Build and run:
  - `mvn clean package`
  - `mvn spring-boot:run`
- Useful manual endpoints: `POST /import/xtb`, `POST /import/stock/create`, `POST /portfolio/sync`, `POST /currency/refresh`, `GET /portfolio/total-profit-loss`.
- No `src/test` suite currently exists; validate changes with targeted endpoint calls and DB checks.

