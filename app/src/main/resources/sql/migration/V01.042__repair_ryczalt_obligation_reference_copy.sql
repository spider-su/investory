-- Keep the Ryczalt-owned comparison table an exact copy of the canonical
-- reference obligations, including fields that may have changed on conflict.
INSERT INTO investory.ryczalt_obligation_reference (
    id, profile_id, tax_period, obligation_type, due_date,
    expected_amount, paid_amount, payment_date, status, note)
SELECT id, profile_id, tax_period, obligation_type, due_date,
       expected_amount, paid_amount, payment_date, status, note
  FROM investory.accounting_reference_obligation
ON CONFLICT (id) DO UPDATE
    SET profile_id = EXCLUDED.profile_id,
        tax_period = EXCLUDED.tax_period,
        obligation_type = EXCLUDED.obligation_type,
        due_date = EXCLUDED.due_date,
        expected_amount = EXCLUDED.expected_amount,
        paid_amount = EXCLUDED.paid_amount,
        payment_date = EXCLUDED.payment_date,
        status = EXCLUDED.status,
        note = EXCLUDED.note;
