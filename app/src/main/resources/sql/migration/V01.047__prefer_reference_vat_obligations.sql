-- Official persisted VAT obligations are the reference value when available.
-- Invoice VAT remains the fallback/supporting reconstruction for periods without one.
CREATE OR REPLACE VIEW investory.ryczalt_monthly_reconstructed_reference AS
WITH native_invoice_totals AS (
    SELECT
        i.profile_id,
        make_date(p.period_year, p.period_month, 1) AS tax_period,
        SUM(i.booked_net_pln) FILTER (WHERE i.direction = 'INCOME') AS income_total,
        SUM(i.booked_net_pln) FILTER (WHERE i.direction = 'COST') AS costs_total,
        SUM(i.vat_amount) FILTER (WHERE i.direction = 'INCOME') AS output_vat,
        SUM(COALESCE(i.deductible_vat, 0)) FILTER (WHERE i.direction = 'COST') AS deductible_input_vat,
        COUNT(*) AS invoice_count
    FROM investory.ryczalt_invoice i
    JOIN investory.ryczalt_period p ON p.id = i.period_id
    WHERE i.approval_status = 'APPROVED'
      AND i.booked_net_pln IS NOT NULL
    GROUP BY i.profile_id, p.period_year, p.period_month
),
legacy_invoice_totals AS (
    SELECT profile_id, tax_period,
           SUM(COALESCE(booked_net_pln, net_amount)) AS income_total,
           SUM(vat_amount) AS output_vat,
           COUNT(*) AS invoice_count
    FROM investory.accounting_reference_invoice
    WHERE invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE')
    GROUP BY profile_id, tax_period
),
legacy_expense_totals AS (
    SELECT profile_id, tax_period,
           SUM(net_amount) AS costs_total,
           SUM(ROUND(vat_amount * vat_deduction_ratio, 2)) AS deductible_input_vat,
           COUNT(*) AS invoice_count
    FROM investory.accounting_reference_expense_invoice
    GROUP BY profile_id, tax_period
),
invoice_totals AS (
    SELECT
        COALESCE(n.profile_id, li.profile_id, le.profile_id) AS profile_id,
        COALESCE(n.tax_period, li.tax_period, le.tax_period) AS tax_period,
        COALESCE(n.income_total, li.income_total, 0) AS income_total,
        COALESCE(n.costs_total, le.costs_total, 0) AS costs_total,
        COALESCE(n.output_vat, li.output_vat, 0) AS output_vat,
        COALESCE(n.deductible_input_vat, le.deductible_input_vat, 0) AS deductible_input_vat,
        COALESCE(n.invoice_count, 0) + COALESCE(li.invoice_count, 0)
            + COALESCE(le.invoice_count, 0) AS invoice_count,
        CASE WHEN n.profile_id IS NOT NULL
             THEN 'NATIVE_APPROVED_INVOICES'
             ELSE 'LEGACY_REFERENCE_INVOICES'
        END AS invoice_source
    FROM native_invoice_totals n
    FULL OUTER JOIN legacy_invoice_totals li
      ON li.profile_id = n.profile_id AND li.tax_period = n.tax_period
    FULL OUTER JOIN legacy_expense_totals le
      ON le.profile_id = COALESCE(n.profile_id, li.profile_id)
     AND le.tax_period = COALESCE(n.tax_period, li.tax_period)
),
obligation_totals AS (
    SELECT
        profile_id,
        tax_period,
        SUM(expected_amount) FILTER (WHERE obligation_type = 'RYCZALT') AS ryczalt,
        SUM(expected_amount) FILTER (WHERE obligation_type = 'VAT') AS vat_obligation,
        SUM(expected_amount) FILTER (WHERE obligation_type = 'ZUS') AS zus,
        COUNT(*) AS obligation_count
    FROM investory.ryczalt_obligation_reference
    GROUP BY profile_id, tax_period
),
vat_adjustments AS (
    SELECT profile_id, tax_period,
           COALESCE(SUM(amount) FILTER (WHERE affects = 'OUTPUT_VAT'), 0) AS output_vat_adjustment,
           COALESCE(SUM(amount) FILTER (WHERE affects = 'INPUT_VAT'), 0) AS input_vat_adjustment,
           COALESCE(SUM(amount) FILTER (WHERE affects = 'PAYABLE_VAT'), 0) AS payable_vat_adjustment
    FROM investory.accounting_vat_adjustment
    GROUP BY profile_id, tax_period
),
bank_evidence AS (
    SELECT profile_id,
           COALESCE(related_period, date_trunc('month', booking_date)::date) AS tax_period,
           COUNT(*) AS bank_transaction_count,
           COALESCE(SUM(abs(amount)) FILTER (
               WHERE transaction_type IN ('RYCZALT_PAYMENT', 'VAT_PAYMENT', 'ZUS_PAYMENT')
           ), 0) AS tax_payment_total
    FROM investory.accounting_reference_bank_transaction
    WHERE scope = 'BUSINESS'
    GROUP BY profile_id, COALESCE(related_period, date_trunc('month', booking_date)::date)
),
periods AS (
    SELECT profile_id, tax_period FROM invoice_totals
    UNION SELECT profile_id, tax_period FROM obligation_totals
    UNION SELECT profile_id, tax_period FROM bank_evidence
)
SELECT
    p.profile_id,
    p.tax_period,
    i.income_total AS reconstructed_revenue,
    i.income_total AS reconstructed_income_total,
    i.costs_total AS reconstructed_costs_total,
    o.ryczalt AS reconstructed_ryczalt,
    COALESCE(
        o.vat_obligation,
        GREATEST(
            COALESCE(i.output_vat, 0) + COALESCE(v.output_vat_adjustment, 0)
            - COALESCE(i.deductible_input_vat, 0) - COALESCE(v.input_vat_adjustment, 0)
            + COALESCE(v.payable_vat_adjustment, 0),
            0
        )
    ) AS reconstructed_vat,
    o.zus AS reconstructed_zus,
    i.output_vat AS reconstructed_output_vat,
    i.deductible_input_vat AS reconstructed_deductible_input_vat,
    i.invoice_count,
    o.obligation_count,
    b.bank_transaction_count,
    b.tax_payment_total,
    i.invoice_source,
    CASE WHEN i.profile_id IS NULL OR o.profile_id IS NULL
         THEN 'NEEDS_REVIEW' ELSE 'RECONSTRUCTED' END AS reconstruction_status
FROM periods p
LEFT JOIN invoice_totals i ON i.profile_id = p.profile_id AND i.tax_period = p.tax_period
LEFT JOIN obligation_totals o ON o.profile_id = p.profile_id AND o.tax_period = p.tax_period
LEFT JOIN vat_adjustments v ON v.profile_id = p.profile_id AND v.tax_period = p.tax_period
LEFT JOIN bank_evidence b ON b.profile_id = p.profile_id AND b.tax_period = p.tax_period;
