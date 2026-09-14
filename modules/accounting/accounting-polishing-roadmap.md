# Accounting Polishing Roadmap

Base: `develop`

Purpose: post-POC-freeze cleanup and architectural polishing. Items here are explicitly **not required to block the Accounting POC freeze** unless implementation work uncovers a correctness or data-safety defect.

## Architecture and module structure

- Move Accounting REST controllers into the same module-local web package convention used by other modules.
- Reduce the large flat `com.smartbox.investory.accounting` package by grouping application, domain, repository, adapter, web, staging, and API concerns.
- Split oversized classes such as `AccountingPocRepository`, `AccountingFactService`, and `AccountingUserFacade` along existing architectural seams.
- Remove remaining legacy `poc` / `tmp` naming from production identifiers only when a safe migration can be planned.
- Replace cross-module direct database reads with module APIs/repositories owned by the source module as part of the Database Module Isolation work.
- Reassess whether calculated month snapshots should be persisted for audit/reproducibility instead of always recomputed.

## Schema cleanup

- Standardize monetary precision across Accounting tables; document the chosen precision and rounding rules.
- Add audit metadata (`created_at`, `updated_at`, actor/source where useful) to mutable canonical tables.
- Make `calculation_hash` mandatory once all historical rows can be backfilled safely.
- Standardize enum-style CHECK constraints across all Accounting tables.
- Remove dead tables such as `accounting_tmp_vat_transaction` after confirming there are no runtime/test consumers.
- Converge the two provenance styles (`source_id` vs free-text `source_document_id`) toward one canonical model.
- Add query indexes based on measured plans, including the source-evidence hot path if still needed after freeze fixes.

## API consistency

- Align Accounting API under the repository-wide `/api/v1/...` convention.
- Decide and consistently use `profiles` vs `portfolios` in resource naming.
- Replace String-based enums in public DTOs with typed enums/OpenAPI declarations where practical.
- Put currency on every money-bearing API field or use a shared money DTO.
- Merge duplicate `FilingSummary` / `FilingView` shapes.
- Standardize temporal types (`Instant`, `OffsetDateTime`, etc.) across the Accounting contract.
- Make JPK metadata responses metadata-only; do not include artifact bytes when a download endpoint exists.
- Replace signed/negative document identifiers with an explicit `(type, id)` or opaque identifier.
- Add pagination/filtering to documents, bank transactions, reconciliation, and staging endpoints.
- Move free-text audit reasons from query parameters into request bodies.
- Add optimistic concurrency/versioning to lifecycle mutations if concurrent use becomes relevant.

## Error model and authorization polish

- Introduce Accounting-specific domain exceptions instead of broad `IllegalArgumentException` / `IllegalStateException`.
- Map domain errors consistently to repository-wide API error contracts.
- Centralize Accounting authorization with method security or a safe base-path policy instead of duplicated per-handler checks.
- Add generated API documentation/OpenAPI metadata for allowed values and error responses.

## UI polish

- Keep server-rendered Accounting pages thin and module-consistent.
- Standardize money/date formatting and labels.
- Improve long-list UX with pagination/filtering.
- Remove duplicated view models and derived status logic from templates/controllers.
- Add clearer status/history presentation for filing artifacts and authority confirmations.

## Testing and maintainability

- Certify full multi-profile Accounting after the POC: verify profile-scoped acquisition, staging,
  canonical facts, reconciliation, filing state, authorization, uniqueness constraints, and
  cross-profile isolation with end-to-end tests. Current persistence/API `profileId` support is
  implementation direction only; the POC is certified only for the primary profile (`profileId=1`).
- Add architecture tests enforcing module/controller placement and forbidden dependency directions.
- Add direct tests for remaining large facades and repositories as they are decomposed.
- Add contract tests for the normalized API error model and typed enums.
- Add migration/schema snapshot tests for precision, audit fields, constraints, and indexes.

## Suggested order

1. API/error consistency
2. Package/class decomposition
3. Schema precision/audit cleanup
4. Cross-module DB isolation
5. Identifier/provenance cleanup
6. Pagination/concurrency improvements
7. Naming cleanup and cosmetic refactors

   three moves
Move 1 — One accounting_document, not two tables

Why. 17 of ~25 columns are already the same concept (customer_alias/supplier_alias and issue_date/invoice_date are renames, not differences). The application already treats them as one type and pays for the split:

private DocumentView document(ExpenseRow r) {
  return new DocumentView(-r.id(), ...);   // AccountingUserFacade:267
}

That negation, plus GET /documents/{id} fetching a whole month and filtering in memory, plus two FK columns on the VAT table (invoice_id, expense_invoice_id) — all exist only because one concept sits in two tables. Your roadmap has "replace signed/negative document identifiers" as an API item; it is really a schema item.

Corrections are currently stored twice. accounting_poc_invoice has correction_net_amount / correction_vat_amount / correction_gross_amount and the same correction exists as a separate row:

-- accounting_poc_invoice: FV 4/2026
correction_gross_amount = -184.5000, correction_net_amount = -150.0000, correction_vat_amount = -34.5000
-- accounting_poc_fact
('SALES_CORRECTION', 'FK 1/2026', 'PLN', -184.5000, 'Correction linked to FV 4/2026; ...')

A faktura korygująca is a document. Model it as one, with a pointer — three columns and the duplicate representation both disappear.

CREATE TABLE investory.accounting_document (
    id                  BIGSERIAL PRIMARY KEY,
    profile_id          BIGINT      NOT NULL REFERENCES investory.portfolios(id),
    direction           VARCHAR(16) NOT NULL,   -- SALE | PURCHASE
    kind                VARCHAR(32) NOT NULL,   -- INVOICE | CORRECTION | RECEIPT
    corrects_document_id BIGINT     REFERENCES investory.accounting_document(id),
    stage               VARCHAR(16) NOT NULL,   -- STAGED | CANONICAL

    tax_period          DATE        NOT NULL,
    issue_date          DATE,
    supply_date         DATE,                   -- sale_date / invoice_date unified
    due_date            DATE,

    reference           VARCHAR(128) NOT NULL,
    counterparty_id     BIGINT      REFERENCES investory.accounting_known_counterparty(id),
    counterparty_name   VARCHAR(256) NOT NULL,

    currency            CHAR(3)      NOT NULL,
    net_amount          NUMERIC(19,2) NOT NULL,
    vat_amount          NUMERIC(19,2) NOT NULL,
    gross_amount        NUMERIC(19,2) NOT NULL,

    -- sale-side economics, NULL for PURCHASE
    fx_rate_date        DATE,
    booked_net_pln      NUMERIC(19,2),
    ryczalt_rate        NUMERIC(8,5),
    -- purchase-side, NULL for SALE
    category            VARCHAR(64),
    vat_deduction_ratio NUMERIC(3,2),

    source_id           BIGINT      NOT NULL REFERENCES investory.accounting_source_evidence(id),
    ksef_number         VARCHAR(256),
    filing_evidence     VARCHAR(8),
    note                VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_document_direction CHECK (direction IN ('SALE','PURCHASE')),
    CONSTRAINT chk_document_kind      CHECK (kind IN ('INVOICE','CORRECTION','RECEIPT')),
    CONSTRAINT chk_document_stage     CHECK (stage IN ('STAGED','CANONICAL')),
    CONSTRAINT chk_document_sale_only
        CHECK (direction = 'SALE' OR (fx_rate_date IS NULL AND ryczalt_rate IS NULL)),
    CONSTRAINT chk_document_purchase_only
        CHECK (direction = 'PURCHASE' OR (category IS NULL AND vat_deduction_ratio IS NULL)),
    CONSTRAINT chk_document_correction
        CHECK (kind <> 'CORRECTION' OR corrects_document_id IS NOT NULL),
    CONSTRAINT uq_document UNIQUE (profile_id, direction, reference)
);

expected_receivable drops out — it is gross_amount plus the sum of its corrections, i.e. a view, not a column.

Move 2 — VAT becomes lines on the document, not a satellite table

This is the one real product fork, and me think the answer is already decided for you.

accounting_vat_transaction today duplicates 12 columns of the document, carries four provenance pointers (invoice_id, expense_invoice_id, source_id, free-text source_document_id), and is enforced 1:1 in Java — DefaultAccountingMonthCalculator raises DUPLICATE_VAT_CLASSIFICATION if two rows share a reference.

But a Polish invoice can carry several VAT rates, and JPK_V7M expects exactly that: one <SprzedazWiersz> with multiple rate pairs filled (K_15/K_16 and K_19/K_20 on the same row). Your generator emits one pair per row:

if (vatRate == 8)  return "<K_17>…<K_18>…";
if (vatRate == 5)  return "<K_15>…<K_16>…";
if (vatRate == 23) return "<K_19>…<K_20>…";
return "";                                   // AccountingJpkGenerator

So today a mixed-rate invoice is unrepresentable, and DUPLICATE_VAT_CLASSIFICATION is what blocks it. That makes the dedupe rule a bug, not a guard — and it makes the line-level model the correct one:

CREATE TABLE investory.accounting_document_vat_line (
    id             BIGSERIAL PRIMARY KEY,
    document_id    BIGINT      NOT NULL REFERENCES investory.accounting_document(id) ON DELETE CASCADE,
    treatment      VARCHAR(48) NOT NULL,
    vat_rate       NUMERIC(5,2),                 -- NULL only for non-rated treatments
    net_amount     NUMERIC(19,2) NOT NULL,
    vat_amount     NUMERIC(19,2) NOT NULL,
    deductible_vat NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_vat_line_treatment CHECK (treatment IN (
        'DOMESTIC_VAT','EU_B2B_REVERSE_CHARGE','NON_EU_B2B_OUTSIDE_POLAND','VAT_EXEMPT',
        'DOMESTIC_PURCHASE','IMPORT_OF_SERVICES_EU','IMPORT_OF_SERVICES_NON_EU')),
    CONSTRAINT chk_vat_line_rate CHECK (vat_rate IS NULL OR vat_rate IN (0,5,8,23)),
    CONSTRAINT chk_vat_line_deductible CHECK (deductible_vat <= vat_amount),
    CONSTRAINT uq_vat_line UNIQUE (document_id, treatment, vat_rate)
);

What this buys, beyond removing a table:

chk_vat_line_rate closes the hole where an unsupported rate silently produces a <SprzedazWiersz> with no K_ columns while still counting in SprzedazCtrl and P_38.
chk_vat_line_deductible moves INVALID_DEDUCTIBLE_VAT from Java into the database.
UNIQUE (document_id, treatment, vat_rate) replaces DUPLICATE_VAT_CLASSIFICATION — and permits legitimate multi-rate.
The JPK generator changes from "one branch per row" to "pivot the lines onto one row", which is what the format wants anyway.
Header totals become SUM(lines) — add a deferred constraint or a reconciliation check rather than trusting two writers.

VIES data does not belong per-document. vat_eu_number, vies_status, vies_verified_at, identifier_type are properties of a counterparty, re-stored on every VAT row today. Move them:

ALTER TABLE investory.accounting_known_counterparty
    ADD COLUMN identifier_type    VARCHAR(16),
    ADD COLUMN vat_eu_number      VARCHAR(64),
    ADD COLUMN vies_status        VARCHAR(24),
    ADD COLUMN vies_verified_at   DATE;
Move 3 — One source of truth for employment state

Three places answer "does this person have a UoP / active JDG":

employment_period.employment_type         CHECK IN ('UOP','JDG')   -- temporal, EXCLUDE no-overlap
accounting_tax_profile_period.jdg_active  BOOLEAN NOT NULL         -- temporal
accounting_poc_profile.has_uop            BOOLEAN NOT NULL         -- not temporal at all

ZusCalculator branches on qualifyingUop, so a disagreement changes the tax. employment_period already has the proper EXCLUDE USING gist no-overlap constraint — it is the good model. Drop the other two:

ALTER TABLE investory.accounting_tax_profile_period DROP COLUMN jdg_active;
DROP TABLE investory.accounting_poc_profile;   -- after readers move to portfolios + employment_period

accounting_poc_profile's own comment already says identity is portfolio-owned: "Historical compatibility assumptions only." Its only live payload is that one derivable boolean.

Sequence — expand/contract, each step shippable

Do not do this as one migration. Seven steps, each independently revertible:

#	step	tables	risk
1	Retire the four self-declared legacy tables (_profile, _fact, _obligation, _tax_input) — 7 Java refs total, all read-only compat	−4	low
2	Standardize money to NUMERIC(19,2); add created_at/updated_at; fill the missing enum CHECKs	0	low — your roadmap already
3	Create accounting_document + accounting_document_vat_line; dual-write from the existing ingestion path	+2	low, additive
4	Backfill; add a reconciliation test asserting old and new produce byte-identical JPK for the 2026 reference months	0	none
5	Flip reads to the new tables; rewrite salesVatColumns as a pivot over lines	0	medium
6	Drop accounting_poc_invoice, _expense_invoice, accounting_vat_transaction	−3	low once #4 is green
7	Move 3 (employment single source), then rename survivors accounting_poc_* → accounting_*	−1	low

Step 4 is the one that makes this safe. Your accounting_reference_* oracle is exactly the right instrument — that is what it is for, and it is why me would not touch it.

Net: 15 → 9 tables, ~190 → ~120 columns, and three Java-enforced rules (DUPLICATE_VAT_CLASSIFICATION, INVALID_DEDUCTIBLE_VAT, unsupported-rate) become database constraints.

What me would not change
accounting_source_evidence with its immutability triggers — correct as-is.
The EXCLUDE USING gist temporal constraints. Best modelling in the module.
The three lifecycle tables — period_state (state), filing_artifact (output), authority_confirmation (external evidence) are genuinely three concepts. Only fix the polymorphic obligation_or_artifact_type.
accounting_known_counterparty — new, profile-scoped, has created_at. That is the pattern; expand it, don't touch it.
The reference layer. Your README defends it as a verification oracle and it is about to earn its keep in step 4.
One thing to settle first

Steps 3–6 all hinge on one answer: can a single invoice carry more than one VAT rate in your scope?

Me recommend yes — JPK_V7M is built for it, and your own fixtures already mix 8% fuel with 23% services across the same month. If you say no, keep the VAT columns on the header, skip the line table, and the target is 8 tables instead of 9 — but then fix the JPK generator to reject mixed-rate input loudly rather than emitting a row with no K_ columns.

Want me draft steps 1 and 2 as actual migrations? Those are pure cleanup, need no product decision, and clear ~50 columns before the interesting work starts.
