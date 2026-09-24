-- Correct persisted calculation field names and expose metric-level reconciliation status.
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
        MAX(
            COALESCE(
                (c.result_json ->> 'revenueBeforeDeductions')::numeric,
                (c.result_json ->> 'revenue')::numeric
            )
        ) FILTER (WHERE c.calculation_type = 'RYCZALT') AS calculated_revenue_from_json,
        MAX((c.result_json ->> 'calculatedTax')::numeric)
            FILTER (WHERE c.calculation_type = 'RYCZALT') AS calculated_ryczalt,
        MAX((c.result_json ->> 'calculatedVat')::numeric)
            FILTER (WHERE c.calculation_type = 'VAT') AS calculated_vat,
        MAX((c.result_json ->> 'total')::numeric)
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
        COALESCE(c.calculated_revenue_from_json, i.calculated_income_total) AS calculated_revenue,
        CASE
            WHEN c.calculated_revenue_from_json IS NOT NULL THEN 'PERSISTED_JSON'
            WHEN i.calculated_income_total IS NOT NULL THEN 'APPROVED_INCOME_INVOICES'
            ELSE 'MISSING'
        END AS revenue_source,
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
),
with_differences AS (
    SELECT
        *,
        calculated_revenue - reference_revenue AS revenue_difference,
        calculated_income_total - reference_income_total AS income_difference,
        calculated_costs_total - reference_costs_total AS costs_difference,
        calculated_ryczalt - reference_ryczalt AS ryczalt_difference,
        calculated_vat - reference_vat AS vat_difference,
        calculated_zus - reference_zus AS zus_difference
    FROM combined
)
SELECT
    profile_id,
    tax_period,
    reference_revenue,
    calculated_revenue,
    revenue_difference,
    reference_income_total,
    calculated_income_total,
    income_difference,
    reference_costs_total,
    calculated_costs_total,
    costs_difference,
    reference_ryczalt,
    calculated_ryczalt,
    ryczalt_difference,
    reference_vat,
    calculated_vat,
    vat_difference,
    reference_zus,
    calculated_zus,
    zus_difference,
    CASE
        WHEN reference_revenue IS NULL OR calculated_revenue IS NULL
          OR reference_income_total IS NULL OR calculated_income_total IS NULL
          OR reference_costs_total IS NULL OR calculated_costs_total IS NULL
          OR reference_ryczalt IS NULL OR calculated_ryczalt IS NULL
          OR reference_vat IS NULL OR calculated_vat IS NULL
          OR reference_zus IS NULL OR calculated_zus IS NULL
        THEN 'MISSING'
        WHEN abs(revenue_difference) > 1.00
          OR abs(income_difference) > 1.00
          OR abs(costs_difference) > 1.00
          OR abs(ryczalt_difference) > 1.00
          OR abs(vat_difference) > 1.00
          OR abs(zus_difference) > 1.00
        THEN 'FAIL'
        ELSE 'PASS'
    END AS reconciliation_status,
    CASE
        WHEN reference_revenue IS NULL OR calculated_revenue IS NULL THEN 'MISSING'
        WHEN abs(revenue_difference) <= 1.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS revenue_status,
    CASE
        WHEN reference_income_total IS NULL OR calculated_income_total IS NULL THEN 'MISSING'
        WHEN abs(income_difference) <= 1.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS income_status,
    CASE
        WHEN reference_costs_total IS NULL OR calculated_costs_total IS NULL THEN 'MISSING'
        WHEN abs(costs_difference) <= 1.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS costs_status,
    CASE
        WHEN reference_ryczalt IS NULL OR calculated_ryczalt IS NULL THEN 'MISSING'
        WHEN abs(ryczalt_difference) <= 1.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS ryczalt_status,
    CASE
        WHEN reference_vat IS NULL OR calculated_vat IS NULL THEN 'MISSING'
        WHEN abs(vat_difference) <= 1.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS vat_status,
    CASE
        WHEN reference_zus IS NULL OR calculated_zus IS NULL THEN 'MISSING'
        WHEN abs(zus_difference) <= 1.00 THEN 'PASS'
        ELSE 'FAIL'
    END AS zus_status,
    revenue_source
FROM with_differences;

COMMENT ON VIEW investory.ryczalt_monthly_reconciliation IS
    'Read-only monthly comparison with corrected persisted calculation fields and metric-level status.';
