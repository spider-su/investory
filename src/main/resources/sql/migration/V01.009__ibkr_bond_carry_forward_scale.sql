UPDATE investory.asset_price_history
SET open_price = open_price / 10,
    high_price = high_price / 10,
    low_price = low_price / 10,
    close_price = close_price / 10,
    quality_class = 'STALE_CARRY_FORWARD_PERCENT_OF_PAR',
    price_scale_factor = 1,
    scale_reason = 'IBKR direct bond carry-forward quote is percent of par'
WHERE asset_id = (
    SELECT id
    FROM investory.assets
    WHERE symbol = 'US91282CKB62'
      AND asset_type = 'BOND'
)
  AND source = 'CARRY_FORWARD'
  AND quality_class NOT LIKE '%PERCENT_OF_PAR%';
