ALTER TABLE investory.ryczalt_invoice
    ALTER COLUMN currency TYPE VARCHAR(3) USING btrim(currency);

ALTER TABLE investory.ryczalt_transaction
    ALTER COLUMN currency TYPE VARCHAR(3) USING btrim(currency);

ALTER TABLE investory.ryczalt_obligation
    ALTER COLUMN currency TYPE VARCHAR(3) USING btrim(currency);

ALTER TABLE investory.ryczalt_fx_rate
    ALTER COLUMN currency TYPE VARCHAR(3) USING btrim(currency);
