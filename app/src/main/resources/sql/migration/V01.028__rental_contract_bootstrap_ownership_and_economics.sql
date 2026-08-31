SET search_path TO investory, public;

ALTER TABLE investory.long_term_asset_rental_contracts
    ADD COLUMN bootstrap_managed boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN investory.long_term_asset_rental_contracts.bootstrap_managed IS
    'True only for contracts created and replaced by the Long-Term bootstrap importer.';

CREATE OR REPLACE VIEW investory.v_long_term_asset_rental_economics AS
WITH term_totals AS (
    SELECT c.id AS contract_id,
           c.asset_id,
           c.start_date AS valid_from,
           CASE WHEN c.end_date IS NULL THEN c.terminated_date
                WHEN c.terminated_date IS NULL THEN c.end_date
                ELSE LEAST(c.end_date, c.terminated_date) END AS valid_to,
           a.current_value,
           COALESCE(c.monthly_tax_base::numeric(30, 12), a.tax_base) AS tax_base,
           COALESCE(c.rental_tax_paid_by_tenant, a.rental_tax_paid_by_tenant) AS rental_tax_paid_by_tenant,
           a.portfolio_id,
           COALESCE(SUM(CASE WHEN t.cash_flow_type IN ('RENT','PARKING_RENT','OTHER_INCOME')
                    THEN CASE WHEN t.frequency = 'MONTHLY' THEN t.amount * 12 ELSE t.amount END ELSE 0 END), 0) AS gross_rental_income,
           COALESCE(SUM(CASE WHEN t.cash_flow_type IN ('ADMIN_FEE','UTILITIES','PROPERTY_TAX','INSURANCE','OTHER_EXPENSE') AND NOT t.paid_by_tenant
                    THEN CASE WHEN t.frequency = 'MONTHLY' THEN t.amount * 12 ELSE t.amount END ELSE 0 END), 0) AS landlord_paid_costs,
           COALESCE(SUM(CASE WHEN t.cash_flow_type IN ('ADMIN_FEE','UTILITIES','PROPERTY_TAX','INSURANCE','OTHER_EXPENSE') AND t.paid_by_tenant
                    THEN CASE WHEN t.frequency = 'MONTHLY' THEN t.amount * 12 ELSE t.amount END ELSE 0 END), 0) AS tenant_paid_costs
    FROM investory.long_term_asset_rental_contracts c
    JOIN investory.long_term_assets a ON a.id = c.asset_id
    LEFT JOIN investory.long_term_asset_rental_contract_terms t ON t.contract_id = c.id
    GROUP BY c.id, c.asset_id, c.start_date, c.end_date, c.terminated_date,
             c.monthly_tax_base, c.rental_tax_paid_by_tenant,
             a.current_value, a.tax_base, a.rental_tax_paid_by_tenant, a.portfolio_id
)
SELECT e.*,
       COALESCE(p.rate, 0.085) AS rental_tax_rate,
       CASE WHEN e.rental_tax_paid_by_tenant THEN 0
            ELSE COALESCE(e.tax_base, 0) * 12 * COALESCE(p.rate, 0.085) END AS rental_tax,
       e.gross_rental_income - e.landlord_paid_costs -
       CASE WHEN e.rental_tax_paid_by_tenant THEN 0
            ELSE COALESCE(e.tax_base, 0) * 12 * COALESCE(p.rate, 0.085) END AS net_rental_income,
       CASE WHEN e.current_value = 0 THEN 0 ELSE
            (e.gross_rental_income - e.landlord_paid_costs -
             CASE WHEN e.rental_tax_paid_by_tenant THEN 0
                  ELSE COALESCE(e.tax_base, 0) * 12 * COALESCE(p.rate, 0.085) END)
             / e.current_value END AS net_yield
FROM term_totals e
LEFT JOIN investory.rental_tax_policies p
  ON p.portfolio_id = e.portfolio_id
 AND p.valid_from <= e.valid_from
 AND (p.valid_to IS NULL OR p.valid_to >= e.valid_from);
