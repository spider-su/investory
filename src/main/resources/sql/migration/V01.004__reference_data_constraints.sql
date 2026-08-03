SET search_path TO investory, public;

-- Shared ISO-like currency dictionary. Existing values are imported first so the
-- migration is non-destructive; application-supported currencies are always present.
CREATE TABLE currency_codes (
    code varchar(3) PRIMARY KEY,
    CONSTRAINT ck_currency_codes_uppercase CHECK (code = upper(code)),
    CONSTRAINT ck_currency_codes_format CHECK (code ~ '^[A-Z]{3}$')
);

INSERT INTO currency_codes (code)
SELECT DISTINCT upper(currency)
FROM accounts
WHERE currency IS NOT NULL AND upper(currency) ~ '^[A-Z]{3}$'
UNION
SELECT DISTINCT upper(currency)
FROM assets
WHERE currency IS NOT NULL AND upper(currency) ~ '^[A-Z]{3}$'
UNION
VALUES ('PLN'), ('USD'), ('EUR');

UPDATE accounts
SET currency = upper(currency)
WHERE currency IS NOT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_currency
        FOREIGN KEY (currency) REFERENCES currency_codes(code);

-- Broker remains optional, but non-null labels must come from one canonical table.
CREATE TABLE brokers (
    code varchar(64) PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    CONSTRAINT ck_brokers_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_brokers_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO brokers (code, display_name)
SELECT DISTINCT btrim(broker), btrim(broker)
FROM accounts
WHERE broker IS NOT NULL AND btrim(broker) <> '';

UPDATE accounts
SET broker = NULLIF(btrim(broker), '');

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_broker
        FOREIGN KEY (broker) REFERENCES brokers(code);

-- Account types are lookup-backed rather than unconstrained free text.
CREATE TABLE account_types (
    code varchar(64) PRIMARY KEY,
    description varchar(255),
    CONSTRAINT ck_account_types_code_not_blank CHECK (btrim(code) <> '')
);

INSERT INTO account_types (code)
SELECT DISTINCT upper(btrim(type))
FROM accounts
WHERE type IS NOT NULL AND btrim(type) <> '';

UPDATE accounts
SET type = upper(btrim(type))
WHERE type IS NOT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_type
        FOREIGN KEY (type) REFERENCES account_types(code);

-- Assets are the persisted instrument catalogue in the current schema.
CREATE TABLE instrument_types (
    code varchar(64) PRIMARY KEY,
    description varchar(255),
    CONSTRAINT ck_instrument_types_code_not_blank CHECK (btrim(code) <> '')
);

INSERT INTO instrument_types (code)
SELECT DISTINCT upper(btrim(asset_type))
FROM assets
WHERE asset_type IS NOT NULL AND btrim(asset_type) <> '';

UPDATE assets
SET asset_type = upper(btrim(asset_type)),
    currency = upper(currency)
WHERE asset_type IS NOT NULL OR currency IS NOT NULL;

ALTER TABLE assets
    ADD CONSTRAINT fk_assets_instrument_type
        FOREIGN KEY (asset_type) REFERENCES instrument_types(code),
    ADD CONSTRAINT fk_assets_currency
        FOREIGN KEY (currency) REFERENCES currency_codes(code);

-- Transaction type is now lookup-backed. Existing values are retained and normalized.
CREATE TABLE transaction_types (
    code varchar(64) PRIMARY KEY,
    description varchar(255),
    CONSTRAINT ck_transaction_types_code_not_blank CHECK (btrim(code) <> '')
);

INSERT INTO transaction_types (code)
SELECT DISTINCT upper(btrim(type))
FROM transactions
WHERE type IS NOT NULL AND btrim(type) <> '';

UPDATE transactions
SET type = upper(btrim(type))
WHERE type IS NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_type
        FOREIGN KEY (type) REFERENCES transaction_types(code);

-- Side is explicit even when broker-specific type names differ.
ALTER TABLE transactions
    ADD COLUMN side varchar(16);

UPDATE transactions
SET side = CASE
    WHEN type ~ '(BUY|PURCHASE|OPEN_LONG)' THEN 'BUY'
    WHEN type ~ '(SELL|SALE|CLOSE_LONG)' THEN 'SELL'
    WHEN type ~ '(TRANSFER|DEPOSIT|WITHDRAW)' THEN 'TRANSFER'
    ELSE 'OTHER'
END;

ALTER TABLE transactions
    ALTER COLUMN side SET NOT NULL,
    ADD CONSTRAINT ck_transactions_side
        CHECK (side IN ('BUY', 'SELL', 'TRANSFER', 'OTHER'));

-- Store an unambiguous instant. Existing date-only values are interpreted as UTC;
-- importers should provide the broker timestamp and zone for new records.
ALTER TABLE transactions
    ADD COLUMN occurred_at timestamptz;

UPDATE transactions
SET occurred_at = date::timestamp AT TIME ZONE 'UTC'
WHERE occurred_at IS NULL;

ALTER TABLE transactions
    ALTER COLUMN occurred_at SET NOT NULL;

-- Persist transaction currency explicitly rather than relying on account/instrument inference.
ALTER TABLE transactions
    ADD COLUMN currency varchar(3);

UPDATE transactions t
SET currency = upper(COALESCE(a.currency, i.currency))
FROM accounts a
LEFT JOIN assets i ON i.id = t.asset_id
WHERE a.id = t.account_id
  AND t.currency IS NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_currency
        FOREIGN KEY (currency) REFERENCES currency_codes(code);

-- No user_id is added: portfolio/account ownership remains the tenancy boundary for
-- this single-user application. A separate tenant model should be introduced only
-- together with authorization and row-level access rules.
