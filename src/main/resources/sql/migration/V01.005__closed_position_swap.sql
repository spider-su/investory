-- Capture overnight financing (swap) on closed trades so realized P/L includes it.
-- XTB lists Swap separately from Gross P/L and Commission on both the stock and the
-- MT (CFD) closed-position sheets; without it, leveraged positions held overnight
-- report an inaccurate realized result.
alter table closed_positions
    add column if not exists swap double precision;

