SET search_path TO investory, public;

CREATE TABLE investory.long_term_asset_rental_contracts (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    start_date date NOT NULL,
    end_date date,
    terminated_date date,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (terminated_date IS NULL OR terminated_date >= start_date),
    CHECK (terminated_date IS NULL OR end_date IS NULL OR terminated_date <= end_date)
);

CREATE TABLE investory.long_term_asset_rental_contract_terms (
    id bigserial PRIMARY KEY,
    contract_id bigint NOT NULL REFERENCES investory.long_term_asset_rental_contracts(id) ON DELETE CASCADE,
    cash_flow_type varchar(32) NOT NULL CHECK (cash_flow_type IN ('RENT', 'PARKING_RENT', 'ADMIN_FEE', 'UTILITIES', 'PROPERTY_TAX', 'INSURANCE', 'OTHER_INCOME', 'OTHER_EXPENSE')),
    amount numeric(30,12) NOT NULL CHECK (amount >= 0),
    frequency varchar(16) NOT NULL CHECK (frequency IN ('MONTHLY', 'ANNUAL')),
    paid_by_tenant boolean NOT NULL DEFAULT false
);

CREATE INDEX ix_rental_contracts_asset_dates
    ON investory.long_term_asset_rental_contracts(asset_id, start_date, end_date);
CREATE INDEX ix_rental_contract_terms_contract
    ON investory.long_term_asset_rental_contract_terms(contract_id, cash_flow_type);

CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE investory.long_term_asset_rental_contracts
    ADD CONSTRAINT ex_rental_contracts_asset_period
    EXCLUDE USING gist (
        asset_id WITH =,
        daterange(start_date, COALESCE(LEAST(end_date, terminated_date), 'infinity'::date), '[]') WITH &&
    );

-- Existing rows are evidence of rental history. Split at every legacy boundary so
-- differently dated terms can be migrated without creating overlapping contracts.
INSERT INTO investory.long_term_asset_rental_contracts(asset_id, start_date, end_date)
WITH bounds AS (
    SELECT f.asset_id, f.valid_from AS boundary FROM investory.long_term_asset_cash_flows f
    UNION
    SELECT f.asset_id, f.valid_to + 1 FROM investory.long_term_asset_cash_flows f WHERE f.valid_to IS NOT NULL
), segments AS (
    SELECT asset_id, boundary AS start_date,
           lead(boundary) OVER (PARTITION BY asset_id ORDER BY boundary) - 1 AS end_date
    FROM bounds
)
SELECT s.asset_id, s.start_date, s.end_date
FROM segments s
JOIN investory.long_term_asset_cash_flows f
  ON f.asset_id = s.asset_id
 AND f.valid_from <= s.start_date
 AND (f.valid_to IS NULL OR f.valid_to >= s.start_date)
JOIN investory.long_term_assets a ON a.id = s.asset_id
WHERE a.asset_type = 'REAL_ESTATE'
GROUP BY s.asset_id, s.start_date, s.end_date;

INSERT INTO investory.long_term_asset_rental_contract_terms
    (contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
SELECT c.id, f.cash_flow_type, f.amount, f.frequency, f.paid_by_tenant
FROM investory.long_term_asset_cash_flows f
JOIN investory.long_term_asset_rental_contracts c
  ON c.asset_id = f.asset_id
 AND f.valid_from <= c.start_date
 AND (f.valid_to IS NULL OR f.valid_to >= c.end_date OR c.end_date IS NULL);

CREATE OR REPLACE VIEW investory.v_long_term_asset_rental_economics AS
WITH term_totals AS (
    SELECT c.id AS contract_id, c.asset_id, c.start_date AS valid_from,
           CASE WHEN c.end_date IS NULL THEN c.terminated_date
                WHEN c.terminated_date IS NULL THEN c.end_date
                ELSE LEAST(c.end_date, c.terminated_date) END AS valid_to,
           a.current_value, a.tax_base,
           a.rental_tax_paid_by_tenant, a.portfolio_id,
           COALESCE(SUM(CASE WHEN t.cash_flow_type IN ('RENT','PARKING_RENT','OTHER_INCOME')
                    THEN CASE WHEN t.frequency = 'MONTHLY' THEN t.amount * 12 ELSE t.amount END ELSE 0 END), 0) AS gross_rental_income,
           COALESCE(SUM(CASE WHEN t.cash_flow_type IN ('ADMIN_FEE','UTILITIES','PROPERTY_TAX','INSURANCE','OTHER_EXPENSE') AND NOT t.paid_by_tenant
                    THEN CASE WHEN t.frequency = 'MONTHLY' THEN t.amount * 12 ELSE t.amount END ELSE 0 END), 0) AS landlord_paid_costs,
           COALESCE(SUM(CASE WHEN t.cash_flow_type IN ('ADMIN_FEE','UTILITIES','PROPERTY_TAX','INSURANCE','OTHER_EXPENSE') AND t.paid_by_tenant
                    THEN CASE WHEN t.frequency = 'MONTHLY' THEN t.amount * 12 ELSE t.amount END ELSE 0 END), 0) AS tenant_paid_costs
    FROM investory.long_term_asset_rental_contracts c
    JOIN investory.long_term_assets a ON a.id = c.asset_id
    LEFT JOIN investory.long_term_asset_rental_contract_terms t ON t.contract_id = c.id
    GROUP BY c.id, c.asset_id, c.start_date, c.end_date, c.terminated_date, a.current_value, a.tax_base,
             a.rental_tax_paid_by_tenant, a.portfolio_id
)
SELECT e.*, COALESCE(p.rate, 0.085) AS rental_tax_rate,
       CASE WHEN e.rental_tax_paid_by_tenant THEN 0
            ELSE COALESCE(e.tax_base, 0) * COALESCE(p.rate, 0.085) END AS rental_tax,
       e.gross_rental_income - e.landlord_paid_costs -
       CASE WHEN e.rental_tax_paid_by_tenant THEN 0
            ELSE COALESCE(e.tax_base, 0) * COALESCE(p.rate, 0.085) END AS net_rental_income,
       CASE WHEN e.current_value = 0 THEN 0 ELSE
            (e.gross_rental_income - e.landlord_paid_costs -
             CASE WHEN e.rental_tax_paid_by_tenant THEN 0 ELSE COALESCE(e.tax_base, 0) * COALESCE(p.rate, 0.085) END)
             / e.current_value END AS net_yield
FROM term_totals e
LEFT JOIN investory.rental_tax_policies p
  ON p.portfolio_id = e.portfolio_id
 AND p.valid_from <= e.valid_from
 AND (p.valid_to IS NULL OR p.valid_to >= e.valid_from);

CREATE OR REPLACE VIEW investory.v_long_term_asset_latest_rental_contract AS
SELECT * FROM (
    SELECT e.*, row_number() OVER (PARTITION BY e.asset_id ORDER BY e.valid_from DESC, e.contract_id DESC) AS contract_rank
    FROM investory.v_long_term_asset_rental_economics e
) ranked WHERE contract_rank = 1;
