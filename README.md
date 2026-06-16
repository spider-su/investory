# investory

Spring Boot 4.1 (Java 25) portfolio tracker that imports XTB (XLSX) and IBKR (CSV) broker statements, refreshes quotes and FX, computes analytics, and surfaces them as a REST API + Thymeleaf dashboard. Optional Telegram bot accepts statement uploads and pushes a daily digest + rule-based alerts.

## Run locally

Requirements: Java 25, Maven 3.9+, PostgreSQL.

```powershell
Set-Location "E:\projects\investory"
mvn clean package
mvn spring-boot:run
```

Tests:

```powershell
mvn test
```

Formatting (google-java-format via Spotless, not bound to `verify`):

```powershell
mvn spotless:check        # report mis-formatted files
mvn spotless:apply        # rewrite them in place
```

## Docker

Build image locally:

```powershell
Set-Location "E:\projects\investory"
docker build -t aserobaba/investory:latest .
```

Push image to Docker Hub:

```powershell
docker login
docker push aserobaba/investory:latest
```

## Docker Compose

Start app with environment from `.env`:

```powershell
Set-Location "E:\projects\investory"
docker compose up -d
```

Stop and remove containers:

```powershell
Set-Location "E:\projects\investory"
docker compose down
```

## GitHub Actions (Docker publish)

Workflow file: `.github/workflows/docker-publish.yml`

Set repository secrets before running the workflow:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN` (Docker Hub access token)

## Broker import API

Generic endpoint (preferred):

```powershell
# XTB statement (XLSX)
curl -X POST "http://localhost:8080/import/broker/xtb" `
  -u admin:change-me-admin `
  -F "file=@account_51499241_en_xlsx_2026-04-30_2026-05-31.xlsx" `
  -F "source=MANUAL"

# IBKR statement (CSV)
curl -X POST "http://localhost:8080/import/broker/ibkr" `
  -u admin:change-me-admin `
  -F "file=@U17959259.TRANSACTIONS.20250211.20260612.csv" `
  -F "source=MANUAL"
```

The response is an `ImportBatchResponse` JSON with `batchId`, `status`, `rowsTotal/Applied/Failed`, and a `duplicate` flag (the orchestrator SHA-256s the upload and short-circuits re-imports).

Legacy XTB-only endpoint `POST /import/xtb` still works for older scripts.

Import monitoring endpoints:

```powershell
curl -u admin:change-me-admin "http://localhost:8080/import/batches?limit=20"
curl -u admin:change-me-admin "http://localhost:8080/import/batches/1"
curl -u admin:change-me-admin "http://localhost:8080/import/batches/latest"
curl -u admin:change-me-admin "http://localhost:8080/import/batches/1/errors"
```

## Dashboard

`GET /dashboard` renders the Thymeleaf dashboard with KPIs, monthly bucketing, per-symbol ranking, the SPY benchmark, and an "Import statement" card that uploads broker files straight from the browser (POST `/import/broker/{broker}`).

## Telegram bot (optional)

Disabled by default. Enable with:

```env
TELEGRAM_BOT_ENABLED=true
TELEGRAM_BOT_USERNAME=your_bot
TELEGRAM_BOT_TOKEN=123:abc
TELEGRAM_CHAT_ID=123456789
```

When enabled, `config/TelegramBotConfig` registers `controllers/bot/PortfolioBot`:

- Send `/start` -> friendly greeting.
- Send an XLSX/CSV document -> the bot detects the broker by filename, imports it via `ImportOrchestratorService`, and replies with the batch summary.
- The bot itself is also the delivery channel for the daily digest and alerts (see below).

When `TELEGRAM_BOT_ENABLED=false`, the application starts cleanly with no Telegram dependencies wired; the digest and alerts still run but log their messages instead of sending them.

## Notifications & alerts

`services/notifications/NotificationService` runs at the end of the weekday market-close scheduler (22:00 `Europe/Warsaw`):

1. `sendDailyDigest()` posts a short summary (balance, total/realized/unrealized P/L, dividends, est. capital-gains tax).
2. `runAlerts()` iterates every `AlertRule` Spring bean and dispatches only those that fire.

Built-in rules (each is a small `@Component` - add new ones by implementing `AlertRule`):

| Rule | Fires when | Default threshold |
|---|---|---|
| `DrawdownAlertRule` | balance drops below tracked peak by >= threshold | `ALERT_DRAWDOWN_PCT=10` |
| `BigMoveAlertRule` | any stock's intraday move (open->price) crosses threshold | `ALERT_BIG_MOVE_PCT=5` |
| `ConcentrationAlertRule` | any single symbol exceeds threshold of open-position market value | `ALERT_CONCENTRATION_PCT=25` |
| `StaleImportAlertRule` | last `ImportBatch` is older than threshold or status `FAILED` | `ALERT_STALE_IMPORT_DAYS=7` |

Master switch: `NOTIFICATIONS_ENABLED=true|false` (default true).

## Database

PostgreSQL + Flyway. Migrations live under `src/main/resources/sql/migration/` (currently `V01.000` through `V01.006`, covering the initial schema, monitoring, data integrity, import batches, day-open prices, closed-position swap, and account summaries). The schema name is `investory`.

Connection variables:

- `DB_URL` (default `jdbc:postgresql://localhost:5432/inventory`)
- `DB_USERNAME` (default `postgres`)
- `DB_PASSWORD` (default `postgres`)

## Security

HTTP Basic auth is enabled for API endpoints (`config/SecurityConfig`).

- `GET /`, `/error`, static assets, and `/dashboard/**` are public.
- `POST`, `PUT`, `DELETE` endpoints require the `ADMIN` role.
- Other API reads require authentication.

Override default users with environment variables:

- `APP_SECURITY_ADMIN_USERNAME`
- `APP_SECURITY_ADMIN_PASSWORD`
- `APP_SECURITY_USER_USERNAME`
- `APP_SECURITY_USER_PASSWORD`

## Scheduler (weekday, `Europe/Warsaw`)

| Cron | Job |
|---|---|
| `0 0 6 * * 1-5` | FX rate refresh |
| `0 30 15 * * 1-5` | Portfolio + history snapshot (market open) |
| `0 0 22 * * 1-5` | Portfolio + history snapshot, then daily digest + alerts |

## Project layout (high level)

```
src/main/java/com/example/demo/
  GoogleAuthSpringBootApplication.java     Spring Boot entrypoint
  clients/                                 Feign clients (exchangerate.host)
  config/                                  SecurityConfig, SchedulerConfig, TelegramBotConfig
  controllers/
    bot/PortfolioBot.java                  Optional Telegram bot (long polling)
    rest/                                  REST API
    ui/HomeController.java                 Thymeleaf views (/, /dashboard)
  data/                                    Enums (BrokerType, CurrencyType, ...)
  data/repository/                         JPA entities + repositories
  services/                                Domain services (Portfolio, Market, History, Benchmark, ...)
  services/imports/                        BrokerImportParser SPI + ImportOrchestratorService
  services/notifications/                  AlertRule SPI + NotificationService + built-in rules
  services/models/                         DTOs returned to the UI/API

src/main/resources/
  application.yml                          Config (DB, security, Telegram, notifications)
  sql/migration/                           Flyway migrations
  static/                                  CSS, JS, dashboard.html
  templates/home.html                      Public landing page

src/test/java/...                          JUnit 5 + Mockito + WebMvcTest suite
src/test/manual/api.http                   Hand-curated request collection (JetBrains HTTP client)
```
