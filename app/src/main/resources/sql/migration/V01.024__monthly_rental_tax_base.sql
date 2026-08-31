COMMENT ON COLUMN investory.long_term_assets.tax_base IS
    'Optional monthly real-estate rental-tax base. Annual rental tax is tax_base * 12 * effective rate.';

UPDATE investory.rental_tax_policies
SET rate = 0.085
WHERE portfolio_id = 1
  AND valid_from = DATE '2025-01-01'
  AND valid_to IS NULL
  AND rate = 0.08;
