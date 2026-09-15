-- Restore the 2025 external-system contribution facts used by historical
-- ryczałt reconstruction. These are source facts, not values derived from
-- the effective UoP/ZUS resolver.

INSERT INTO investory.accounting_tax_profile_period
    (profile_id, valid_from, valid_to, jdg_active, ryczalt_rate,
     vat_registered, vat_eu_registered, zus_regime, voluntary_sickness)
VALUES
    (1, DATE '2025-01-01', DATE '2025-12-01', TRUE, 0.12,
     TRUE, TRUE, 'JDG', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO investory.accounting_poc_tax_input
    (profile_id, tax_period, input_type, amount, note)
SELECT 1, period, 'SOCIAL_CONTRIBUTION_PAID', 1518.9800,
       'External-system paid deductible social contribution fact; retained independently from UoP/ZUS accrual resolution.'
  FROM generate_series(DATE '2025-03-01', DATE '2025-12-01', INTERVAL '1 month') period
ON CONFLICT (profile_id, tax_period, input_type) DO NOTHING;

INSERT INTO investory.accounting_poc_tax_input
    (profile_id, tax_period, input_type, amount, note)
SELECT 1, period, 'HEALTH_CONTRIBUTION_PAID', 1384.9700,
       'External-system paid health contribution fact; ryczałt uses the statutory 50% deductible portion.'
  FROM generate_series(DATE '2025-03-01', DATE '2025-12-01', INTERVAL '1 month') period
ON CONFLICT (profile_id, tax_period, input_type) DO NOTHING;

UPDATE investory.accounting_poc_tax_input
   SET note = CASE input_type
       WHEN 'SOCIAL_CONTRIBUTION_PAID' THEN
         'External-system paid deductible social contribution fact; retained independently from UoP/ZUS accrual resolution.'
       WHEN 'HEALTH_CONTRIBUTION_PAID' THEN
         'External-system paid health contribution fact; ryczałt uses the statutory 50% deductible portion.'
       ELSE note
       END
 WHERE profile_id = 1
   AND tax_period BETWEEN DATE '2025-03-01' AND DATE '2025-12-01'
   AND input_type IN ('SOCIAL_CONTRIBUTION_PAID', 'HEALTH_CONTRIBUTION_PAID');
