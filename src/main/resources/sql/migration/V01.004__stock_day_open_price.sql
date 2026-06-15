-- Persist the intraday market-open price separately from the latest/close price.
-- This allows open_positions_history snapshots to record a genuine
-- "market open" vs "market close" value instead of duplicating the same price.
alter table stocks
    add column if not exists day_open_price double precision;

-- Backfill existing rows so historic stocks have a sensible open price.
update stocks
set day_open_price = market_price
where day_open_price is null;

