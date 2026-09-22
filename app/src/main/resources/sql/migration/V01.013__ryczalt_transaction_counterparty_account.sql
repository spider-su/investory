ALTER TABLE investory.ryczalt_transaction
    ADD COLUMN counterparty_account VARCHAR(64);

ALTER TABLE investory.ryczalt_counterparty
    ADD COLUMN bank_account VARCHAR(64);

CREATE INDEX ix_ryczalt_transaction_counterparty_account
    ON investory.ryczalt_transaction(profile_id, counterparty_account)
    WHERE counterparty_account IS NOT NULL;

CREATE INDEX ix_ryczalt_counterparty_bank_account
    ON investory.ryczalt_counterparty(profile_id, bank_account)
    WHERE bank_account IS NOT NULL;

CREATE TABLE investory.ryczalt_payment_account_rule (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_type VARCHAR(32) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_payment_account_rule UNIQUE (profile_id, obligation_type, account_number),
    CONSTRAINT chk_ryczalt_payment_account_rule_type CHECK (obligation_type IN ('RYCZALT', 'VAT', 'ZUS')),
    CONSTRAINT chk_ryczalt_payment_account_rule_account CHECK (length(btrim(account_number)) > 0)
);

CREATE INDEX ix_ryczalt_payment_account_rule_profile
    ON investory.ryczalt_payment_account_rule(profile_id, obligation_type);

INSERT INTO investory.ryczalt_payment_account_rule (profile_id, obligation_type, account_number)
SELECT p.id, rule.obligation_type, rule.account_number
  FROM investory.portfolios p
  LEFT JOIN investory.accounting_poc_profile legacy ON legacy.profile_id = p.id
  CROSS JOIN LATERAL (
      VALUES
          ('RYCZALT', COALESCE(legacy.ryczalt_payment_account, p.tax_micro_account)),
          ('VAT', COALESCE(legacy.vat_payment_account, p.tax_micro_account)),
          ('ZUS', p.zus_payment_account)
  ) AS rule(obligation_type, account_number)
 WHERE NULLIF(btrim(rule.account_number), '') IS NOT NULL
ON CONFLICT DO NOTHING;
