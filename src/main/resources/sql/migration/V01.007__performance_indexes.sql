-- Performance + data-integrity indexes for hot read paths.
--
-- Added in Session 7. Every index uses `IF NOT EXISTS` so it is safe to apply
-- against an existing DB that may already have one or more of these from a
-- prior manual fix.
--
-- Naming convention:
--   idx_<table>_<columns>   non-unique B-tree
--   ux_<table>_<columns>    unique B-tree

------------------------------------------------------------------
-- FK index: Postgres does NOT auto-create an index for a foreign
-- key. `findAllByBatch_IdOrderByIdAsc(batchId)` powers
-- `GET /import/batches/{id}/errors` and would otherwise full-scan
-- `import_row_error` as the audit table grows.
------------------------------------------------------------------
create index if not exists idx_import_row_error_batch_id
    on import_row_error (batch_id);

------------------------------------------------------------------
-- Cash flow + tax aggregations group by (account) and filter by
-- date in the dashboard payload. Composite (account, date) covers
-- both the equality filter and the range scan with one index.
------------------------------------------------------------------
create index if not exists idx_cash_operations_account_date
    on cash_operations (account, date);

------------------------------------------------------------------
-- Closed positions: per-account P/L grouping (TaxCalculator,
-- PortfolioService) and per-symbol historical rankings.
------------------------------------------------------------------
create index if not exists idx_closed_positions_account
    on closed_positions (account);

create index if not exists idx_closed_positions_symbol_close_time
    on closed_positions (symbol, close_time);

------------------------------------------------------------------
-- Opened positions: every sync runs
--   findAllByAccount('IBKR')          (MarketService.syncIbkrPositions)
--   removeAllByAccountNotIn(account, [...])  (XTB / IBKR importers)
-- and the dashboard groups by symbol.
------------------------------------------------------------------
create index if not exists idx_opened_positions_account
    on opened_positions (account);

create index if not exists idx_opened_positions_symbol
    on opened_positions (symbol);

------------------------------------------------------------------
-- HistoryService.saveHistory() and PortfolioService both call
-- findAllAfterDate(date) with date-only predicates.
------------------------------------------------------------------
create index if not exists idx_open_positions_history_date
    on open_positions_history (date);

------------------------------------------------------------------
-- Indicators: today these tables are write-only seed (see AGENTS.md
-- "indicator endpoints exist but implementation is still partial"),
-- but adding a unique key on (symbol, snapshot_time) now prevents
-- duplicate snapshots once `TechnicalService.updateTechnicals()` /
-- `FundamentalService.updateFundamentals()` are completed.
--
-- Run a one-shot de-dup before creating the unique indexes so the
-- migration applies cleanly against environments that may already
-- contain duplicate seed rows.
------------------------------------------------------------------
delete from fundamental_indicators f
    using fundamental_indicators d
where f.symbol = d.symbol
  and f.sync_date = d.sync_date
  and f.id < d.id;

create unique index if not exists ux_fundamental_indicators_symbol_sync_date
    on fundamental_indicators (symbol, sync_date);

delete from technical_indicators t
    using technical_indicators d
where t.symbol = d.symbol
  and t.timestamp = d.timestamp
  and t.id < d.id;

create unique index if not exists ux_technical_indicators_symbol_timestamp
    on technical_indicators (symbol, timestamp);

