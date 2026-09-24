-- Keep the previous comparison definition available as an internal calculation source,
-- then expose the same public view with reconstructed monthly reference values.
ALTER VIEW investory.ryczalt_monthly_reconciliation
    RENAME TO ryczalt_monthly_reconciliation_oracle;

CREATE VIEW investory.ryczalt_monthly_reconciliation AS
WITH reconstructed AS (
    SELECT *
    FROM investory.ryczalt_monthly_reconstructed_reference
),
calculated AS (
    SELECT *
    FROM investory.ryczalt_monthly_reconciliation_oracle
),
with_differences AS (
    SELECT
        r.profile_id,
        r.tax_period,
        r.reconstructed_revenue AS reference_revenue,
        c.calculated_revenue,
        c.revenue_source,
        r.reconstructed_income_total AS reference_income_total,
        c.calculated_income_total,
        r.reconstructed_costs_total AS reference_costs_total,
        c.calculated_costs_total,
        r.reconstructed_ryczalt AS reference_ryczalt,
        c.calculated_ryczalt,
        r.reconstructed_vat AS reference_vat,
        c.calculated_vat,
        r.reconstructed_zus AS reference_zus,
        c.calculated_zus,
        r.invoice_source,
        r.reconstruction_status AS reference_reconstruction_status
    FROM reconstructed r
    FULL OUTER JOIN calculated c
      ON c.profile_id = r.profile_id
     AND c.tax_period = r.tax_period
),
with_differences_calculated AS (
    SELECT
        *,
        calculated_revenue - reference_revenue AS revenue_difference,
        calculated_income_total - reference_income_total AS income_difference,
        calculated_costs_total - reference_costs_total AS costs_difference,
        calculated_ryczalt - reference_ryczalt AS ryczalt_difference,
        calculated_vat - reference_vat AS vat_difference,
        calculated_zus - reference_zus AS zus_difference
    FROM with_differences
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
    revenue_source,
    COALESCE(invoice_source, reference_reconstruction_status) AS reference_source
FROM with_differences_calculated;

COMMENT ON VIEW investory.ryczalt_monthly_reconciliation IS
    'Monthly reconciliation using reconstructed invoice and tax-obligation references.';
