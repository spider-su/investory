-- Read-only monthly comparison of the persisted accounting reference oracle and
-- persisted native Ryczalt calculations. No calculation or source data is changed.
CREATE OR REPLACE VIEW investory.ryczalt_monthly_reconciliation AS
WITH reference_months AS (
    SELECT
        profile_id,
        tax_period,
        revenue AS reference_revenue,
        revenue AS reference_income_total,
        expenses AS reference_costs_total,
        ryczalt AS reference_ryczalt,
        vat_payable AS reference_vat,
        zus AS reference_zus
    FROM investory.accounting_reference_month
),
native_invoices AS (
    SELECT
        profile_id,
        date_trunc('month', accounting_date)::date AS tax_period,
        SUM(net_amount) FILTER (WHERE direction = 'INCOME') AS calculated_income_total,
        SUM(net_amount) FILTER (WHERE direction = 'COST') AS calculated_costs_total
    FROM investory.ryczalt_invoice
    WHERE approval_status = 'APPROVED'
    GROUP BY profile_id, date_trunc('month', accounting_date)::date
),
native_calculations AS (
    SELECT
        p.profile_id,
        make_date(p.period_year, p.period_month, 1) AS tax_period,
        MAX((c.result_json ->> 'revenueBeforeDeductions')::numeric)
            FILTER (WHERE c.calculation_type = 'RYCZALT') AS calculated_revenue,
        MAX((c.result_json ->> 'calculatedTax')::numeric)
            FILTER (WHERE c.calculation_type = 'RYCZALT') AS calculated_ryczalt,
        MAX((c.result_json ->> 'calculatedVat')::numeric)
            FILTER (WHERE c.calculation_type = 'VAT') AS calculated_vat,
        MAX((c.result_json ->> 'totalZus')::numeric)
            FILTER (WHERE c.calculation_type = 'ZUS') AS calculated_zus
    FROM investory.ryczalt_calculation c
    JOIN investory.ryczalt_period p ON p.id = c.period_id
    WHERE c.is_current = TRUE
    GROUP BY p.profile_id, p.period_year, p.period_month
),
combined AS (
    SELECT
        COALESCE(r.profile_id, i.profile_id, c.profile_id) AS profile_id,
        COALESCE(r.tax_period, i.tax_period, c.tax_period) AS tax_period,
        r.reference_revenue,
        c.calculated_revenue,
        r.reference_income_total,
        i.calculated_income_total,
        r.reference_costs_total,
        i.calculated_costs_total,
        r.reference_ryczalt,
        c.calculated_ryczalt,
        r.reference_vat,
        c.calculated_vat,
        r.reference_zus,
        c.calculated_zus
    FROM reference_months r
    FULL OUTER JOIN native_invoices i
      ON i.profile_id = r.profile_id
     AND i.tax_period = r.tax_period
    FULL OUTER JOIN native_calculations c
      ON c.profile_id = COALESCE(r.profile_id, i.profile_id)
     AND c.tax_period = COALESCE(r.tax_period, i.tax_period)
)
SELECT
    profile_id,
    tax_period,
    reference_revenue,
    calculated_revenue,
    calculated_revenue - reference_revenue AS revenue_difference,
    reference_income_total,
    calculated_income_total,
    calculated_income_total - reference_income_total AS income_difference,
    reference_costs_total,
    calculated_costs_total,
    calculated_costs_total - reference_costs_total AS costs_difference,
    reference_ryczalt,
    calculated_ryczalt,
    calculated_ryczalt - reference_ryczalt AS ryczalt_difference,
    reference_vat,
    calculated_vat,
    calculated_vat - reference_vat AS vat_difference,
    reference_zus,
    calculated_zus,
    calculated_zus - reference_zus AS zus_difference,
    CASE
        WHEN calculated_revenue IS NULL OR reference_revenue IS NULL
          OR calculated_income_total IS NULL OR reference_income_total IS NULL
          OR calculated_costs_total IS NULL OR reference_costs_total IS NULL
          OR calculated_ryczalt IS NULL OR reference_ryczalt IS NULL
          OR calculated_vat IS NULL OR reference_vat IS NULL
          OR calculated_zus IS NULL OR reference_zus IS NULL
        THEN 'MISSING'
        WHEN abs(calculated_revenue - reference_revenue) > 1.00
          OR abs(calculated_income_total - reference_income_total) > 1.00
          OR abs(calculated_costs_total - reference_costs_total) > 1.00
          OR abs(calculated_ryczalt - reference_ryczalt) > 1.00
          OR abs(calculated_vat - reference_vat) > 1.00
          OR abs(calculated_zus - reference_zus) > 1.00
        THEN 'FAIL'
        ELSE 'PASS'
    END AS reconciliation_status
FROM combined;

COMMENT ON VIEW investory.ryczalt_monthly_reconciliation IS
    'Read-only monthly comparison of accounting reference values, approved native invoices, and current persisted Ryczalt/VAT/ZUS calculations.';
