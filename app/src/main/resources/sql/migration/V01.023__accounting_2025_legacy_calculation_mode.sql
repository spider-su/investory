-- 2025 is reconstructed from confirmed historical facts. It must not opt into the
-- operational effective-profile path, which derives deductions from bank payment facts.
DELETE FROM investory.accounting_tax_profile_period
 WHERE profile_id = 1
   AND valid_from = DATE '2025-01-01'
   AND valid_to = DATE '2025-12-01';
