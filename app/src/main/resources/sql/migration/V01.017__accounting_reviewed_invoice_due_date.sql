-- Preserve the optional due date supplied during reviewed-document intake.
ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;

ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;
