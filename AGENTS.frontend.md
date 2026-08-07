# Frontend Agent Guidance

Applies to dashboard HTML, CSS, JavaScript, Thymeleaf rendering, and browser behavior.
Read together with root `AGENTS.md`.

## Frontend Shape

- Main dashboard: `src/main/resources/static/dashboard/dashboard.html`.
- Landing page: `src/main/resources/templates/home.html`.
- Main dashboard JavaScript: `src/main/resources/static/js/dashboard.js`.
- Main theme CSS: `src/main/resources/static/css/main.css`.
- Keep the existing lightweight Thymeleaf/static approach. Do not introduce a frontend framework
  or build pipeline unless explicitly requested.
- `dashboard.js` is for progressive enhancement that does not need Thymeleaf inlining.
  Chart blocks that require `${stats}` stay in `dashboard.html`.

## Data Ownership

- Frontend renders server/database-derived portfolio values; it must not become a second accounting
  engine.
- Do not recompute portfolio P/L, FX conversion, settlement semantics, or projection totals in
  JavaScript when the backend/reporting layer already owns them.
- Position price currency is the instrument quote currency. Account/position currencies are for
  cost/cash conversion, not for relabeling a quote.

## Dashboard Surfaces

- Headline totals prefer `portfolio_kpi_summary`/server-provided KPI data and may include
  cash-flow/interest supplements.
- Account-table values come from account-level projection/statistics data.
- Open-position popup/allocation comes from `portfolio_asset_allocation`.
- If these surfaces disagree, refresh/inspect projections and data lineage before adding frontend
  compensation logic.

## Preserve Current UI Behavior

- Exclude near-zero accounts from account lists and benchmark choices.
- Benchmark tooltips show both percent and absolute P/L.
- Dividend Top 10 shows nine symbols plus `Other` when more than ten symbols exist.
- Dividend popup symbols are plain text without avatars.
- Dashboard data-quality, risk exposure, daily-performance detail, and monthly attribution remain
  derived from the current ledger/projection data.

## Frontend Change Checks

- Reuse existing CSS classes/components and nearby JavaScript patterns before adding abstractions.
- Keep browser behavior progressive; core reporting values must still originate server-side.
- For a change requiring new/changed server values, also read `AGENTS.backend.md`.
- For a change involving reporting views, projection meaning, or DB reconciliation, also read
  `AGENTS.sql.md`.
