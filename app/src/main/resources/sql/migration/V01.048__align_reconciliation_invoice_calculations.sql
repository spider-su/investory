-- Align calculated income/costs with the same canonical period and PLN amount basis
-- used by the reconstructed reference.
CREATE OR REPLACE VIEW investory.ryczalt_monthly_reconciliation AS
WITH calculated_invoice_totals AS (
    SELECT
        i.profile_id,
        make_date(p.period_year, p.period_month, 1) AS tax_period,
        SUM(i.booked_net_pln) FILTER (WHERE i.direction = 'INCOME') AS calculated_income_total,
        SUM(i.booked_net_pln) FILTER (WHERE i.direction = 'COST') AS calculated_costs_total
    FROM investory.ryczalt_invoice i
    JOIN investory.ryczalt_period p ON p.id = i.period_id
    WHERE i.approval_status = 'APPROVED'
      AND i.booked_net_pln IS NOT NULL
    GROUP BY i.profile_id, p.period_year, p.period_month
),
reconstructed AS (
    SELECT * FROM investory.ryczalt_monthly_reconstructed_reference
),
oracle_calculations AS (
    SELECT * FROM investory.ryczalt_monthly_reconciliation_oracle
),
source_rows AS (
    SELECT
        COALESCE(r.profile_id, o.profile_id) AS profile_id,
        COALESCE(r.tax_period, o.tax_period) AS tax_period,
        r.reconstructed_revenue AS reference_revenue,
        o.calculated_revenue,
        r.reconstructed_income_total AS reference_income_total,
        ci.calculated_income_total,
        r.reconstructed_costs_total AS reference_costs_total,
        ci.calculated_costs_total,
        r.reconstructed_ryczalt AS reference_ryczalt,
        o.calculated_ryczalt,
        r.reconstructed_vat AS reference_vat,
        o.calculated_vat,
        r.reconstructed_zus AS reference_zus,
        o.calculated_zus,
        o.revenue_source,
        r.invoice_source,
        r.reconstruction_status AS reference_reconstruction_status
    FROM reconstructed r
    FULL OUTER JOIN oracle_calculations o
      ON o.profile_id = r.profile_id AND o.tax_period = r.tax_period
    LEFT JOIN calculated_invoice_totals ci
      ON ci.profile_id = COALESCE(r.profile_id, o.profile_id)
     AND ci.tax_period = COALESCE(r.tax_period, o.tax_period)
),
differences AS (
    SELECT *,
        calculated_revenue - reference_revenue AS revenue_difference,
        calculated_income_total - reference_income_total AS income_difference,
        calculated_costs_total - reference_costs_total AS costs_difference,
        calculated_ryczalt - reference_ryczalt AS ryczalt_difference,
        calculated_vat - reference_vat AS vat_difference,
        calculated_zus - reference_zus AS zus_difference
    FROM source_rows
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
        WHEN reference_reconstruction_status <> 'RECONSTRUCTED'
          OR reference_revenue IS NULL OR calculated_revenue IS NULL
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
    CASE WHEN reference_revenue IS NULL OR calculated_revenue IS NULL THEN 'MISSING'
         WHEN abs(revenue_difference) <= 1.00 THEN 'PASS' ELSE 'FAIL' END AS revenue_status,
    CASE WHEN reference_income_total IS NULL OR calculated_income_total IS NULL THEN 'MISSING'
         WHEN abs(income_difference) <= 1.00 THEN 'PASS' ELSE 'FAIL' END AS income_status,
    CASE WHEN reference_costs_total IS NULL OR calculated_costs_total IS NULL THEN 'MISSING'
         WHEN abs(costs_difference) <= 1.00 THEN 'PASS' ELSE 'FAIL' END AS costs_status,
    CASE WHEN reference_ryczalt IS NULL OR calculated_ryczalt IS NULL THEN 'MISSING'
         WHEN abs(ryczalt_difference) <= 1.00 THEN 'PASS' ELSE 'FAIL' END AS ryczalt_status,
    CASE WHEN reference_vat IS NULL OR calculated_vat IS NULL THEN 'MISSING'
         WHEN abs(vat_difference) <= 1.00 THEN 'PASS' ELSE 'FAIL' END AS vat_status,
    CASE WHEN reference_zus IS NULL OR calculated_zus IS NULL THEN 'MISSING'
         WHEN abs(zus_difference) <= 1.00 THEN 'PASS' ELSE 'FAIL' END AS zus_status,
    revenue_source,
    COALESCE(invoice_source, reference_reconstruction_status) AS reference_source
FROM differences;

COMMENT ON VIEW investory.ryczalt_monthly_reconciliation IS
    'Monthly reconciliation using canonical-period invoice calculations and official VAT obligations where available.';
