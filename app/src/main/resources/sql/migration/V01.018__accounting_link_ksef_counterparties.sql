-- Link already-imported KSeF documents to their stable counterparty identity.
-- The application now sets this link for new canonical documents as well.
INSERT INTO investory.accounting_known_counterparty
    (profile_id, tax_identifier, country, canonical_name)
SELECT DISTINCT d.profile_id,
       UPPER(REGEXP_REPLACE(d.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')),
       UPPER(d.counterparty_country),
       d.counterparty_name
  FROM investory.accounting_document d
 WHERE d.ksef_number IS NOT NULL
   AND d.counterparty_tax_identifier IS NOT NULL
   AND d.counterparty_country IS NOT NULL
   AND NULLIF(TRIM(d.counterparty_name), '') IS NOT NULL
ON CONFLICT (profile_id, country, tax_identifier) DO NOTHING;

UPDATE investory.accounting_document d
   SET counterparty_id = k.id
  FROM investory.accounting_known_counterparty k
 WHERE d.ksef_number IS NOT NULL
   AND d.counterparty_id IS NULL
   AND k.profile_id = d.profile_id
   AND k.country = UPPER(d.counterparty_country)
   AND k.tax_identifier = UPPER(REGEXP_REPLACE(d.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g'));
