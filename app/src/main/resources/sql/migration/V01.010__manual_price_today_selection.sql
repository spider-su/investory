-- Manual observations accepted by the supported path are forward-only and must
-- outrank provider quotes on the same effective date in the existing selectors.
-- Do not alter earlier manual observations: that would reprice historical reports.
UPDATE investory.asset_price_history
   SET quality_score = 100
 WHERE source = 'MANUAL'
   AND price_origin = 'MANUAL'
   AND price_date >= CURRENT_DATE;
