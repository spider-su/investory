-- Polish NIP is the same identity with or without the PL VAT prefix.
CREATE TEMP TABLE accounting_counterparty_merge ON COMMIT DROP AS
SELECT duplicate.id AS duplicate_id, keeper.id AS keeper_id
  FROM investory.accounting_known_counterparty duplicate
  JOIN LATERAL (
        SELECT candidate.id
          FROM investory.accounting_known_counterparty candidate
         WHERE candidate.profile_id = duplicate.profile_id
           AND candidate.country = 'PL'
           AND REGEXP_REPLACE(UPPER(candidate.tax_identifier), '^PL', '') =
               REGEXP_REPLACE(UPPER(duplicate.tax_identifier), '^PL', '')
         ORDER BY (UPPER(candidate.tax_identifier) LIKE 'PL%'), candidate.id
         LIMIT 1
       ) keeper ON TRUE
 WHERE duplicate.country = 'PL'
   AND duplicate.id <> keeper.id;

UPDATE investory.accounting_known_counterparty keeper
   SET alias = COALESCE(NULLIF(keeper.alias, ''), duplicate.alias)
  FROM investory.accounting_known_counterparty duplicate
  JOIN accounting_counterparty_merge merge ON merge.duplicate_id = duplicate.id
 WHERE keeper.id = merge.keeper_id
   AND NULLIF(duplicate.alias, '') IS NOT NULL;

UPDATE investory.accounting_document document
   SET counterparty_id = merge.keeper_id
  FROM accounting_counterparty_merge merge
 WHERE document.counterparty_id = merge.duplicate_id;

UPDATE investory.accounting_trusted_counterparty_treatment treatment
   SET counterparty_id = merge.keeper_id
  FROM accounting_counterparty_merge merge
 WHERE treatment.counterparty_id = merge.duplicate_id;

DELETE FROM investory.accounting_known_counterparty duplicate
 USING accounting_counterparty_merge merge
 WHERE duplicate.id = merge.duplicate_id;

UPDATE investory.accounting_known_counterparty
   SET tax_identifier = REGEXP_REPLACE(UPPER(tax_identifier), '^PL', '')
 WHERE country = 'PL'
   AND UPPER(tax_identifier) LIKE 'PL%';
