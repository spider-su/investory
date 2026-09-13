-- Never silently attach a future Accounting write to profile 1.
ALTER TABLE investory.accounting_poc_invoice ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_poc_expense_invoice ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_poc_bank_transaction ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_poc_fact ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_source_evidence ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_poc_obligation ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_poc_tax_input ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_poc_period_state ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_filing_artifact ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_authority_confirmation ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_vat_transaction ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_tmp_invoice ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_tmp_bank_transaction ALTER COLUMN profile_id DROP DEFAULT;
ALTER TABLE investory.accounting_tmp_vat_transaction ALTER COLUMN profile_id DROP DEFAULT;
