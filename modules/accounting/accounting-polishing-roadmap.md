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

Correction to my earlier count: with document_vat_line the target is 10 tables, not 9 — 15 → 10, ~190 → ~125 columns.

Arrows drawn are the real foreign keys: source_evidence → document and → bank_transaction (both source_id), and document → document_vat_line (document_id).

Not drawn, to keep it readable:

document.corrects_document_id → document.id — self-reference, a korygująca pointing at what it corrects
document.counterparty_id → known_counterparty.id
every profile_id → portfolios.id

The period-and-filing tier has no FK to document on purpose. Those three are keyed by (profile_id, tax_period) and relate to the facts only through the period. That is already how the code works; the diagram just makes it explicit.

Old → new
today	becomes
accounting_poc_invoice + accounting_poc_expense_invoice	document (direction discriminator)
accounting_poc_invoice.correction_* + the SALES_CORRECTION row in accounting_poc_fact	document row with kind = 'CORRECTION' + corrects_document_id
accounting_vat_transaction	document_vat_line (+ VIES fields → known_counterparty)
accounting_tmp_invoice / accounting_tmp_bank_transaction	document.stage / bank_transaction.stage column
accounting_poc_profile.has_uop, tax_profile_period.jdg_active	derived from employment_period
accounting_poc_fact, _obligation, _tax_input	retired (self-declared legacy)
accounting_poc_period_state	period_state, PK unchanged

Four tables vanish by merging, three by retirement, two by becoming a column.

What the shape buys
document_vat_line with UNIQUE (document_id, treatment, vat_rate) replaces the Java DUPLICATE_VAT_CLASSIFICATION check and unblocks mixed-rate invoices — which JPK_V7M expects, since one <SprzedazWiersz> can fill K_15/K_16 and K_19/K_20 together.
CHECK (vat_rate IN (0,5,8,23)) closes the hole where an unsupported rate produces a sales row with no K_ columns.
CHECK (deductible_vat <= vat_amount) moves INVALID_DEDUCTIBLE_VAT into the database.
One document table deletes the -r.id() negation in AccountingUserFacade:267 and the two-FK provenance on the VAT table.
employment_period as sole source removes a contradiction that can change the tax through qualifyingUop.

Want me draft the DDL migrations for the retirement steps (1 and 2 from the plan)? Those need no product decision.

explain domain entities we should have

Different layer from the tables. Here what the domain model should be — grounded in what your code already almost does.

Context boundary

Accounting owns: Polish JDG tax compliance — ryczałt PIT, VAT/JPK_V7M, ZUS. It does not own portfolio valuation, FX rate sourcing, or bank transport. Those are upstream suppliers it consumes through ports (CurrencyConversion, BankTransactionSource, AccountingKsefSyncPort) — that part you already got right.

The aggregates

An aggregate is a consistency boundary: the set of things that must change in one transaction and that share invariants. Your current pain is that you have tables but no aggregates, so invariants leak into whichever service happened to touch the row.

1. SourceEvidence — raw immutable payload

Root, no children. Invariant: payload, hash and identity never change after creation; only processingStatus moves. Already enforced in the database by prevent_accounting_source_mutation and prevent_accounting_source_delete. This one is genuinely modelled. Leave it.

2. Document — invoice, expense or correction, with VatLine children
Document (root)
 ├─ direction, kind, taxPeriod, reference
 ├─ counterparty, sourceEvidenceId
 ├─ corrects → DocumentId
 └─ VatLine[]  (treatment, vatRate, net, vat, deductibleVat)

Invariants that must hold atomically:

net + vat = gross
Σ lines.net = header.net, Σ lines.vat = header.vat
direction constrains legal treatments (a SALE cannot be IMPORT_OF_SERVICES_EU)
deductibleVat ≤ vat per line
kind = CORRECTION ⟹ corrects is present
no two lines share (treatment, vatRate)

Every one of those rules exists in your code today — scattered across AccountingStagingAcquisitionService.validate(), DefaultAccountingMonthCalculator.validateVatInputs(), and AccountingVatClassifier.issues(). Three places, three phases, inconsistently applied. That scattering is the missing aggregate. A document that cannot be constructed in an invalid state removes all three.

This is also where the multi-rate question resolves: VatLine is a child entity, not a separate aggregate, precisely because its consistency is only meaningful relative to its document header.

3. BankTransaction — observed cash

Separate root, deliberately not inside Document. Cash is observed independently of documents, arrives out of order, and may match nothing. Reconciliation is a relationship between two aggregates, not a parent-child link. Your architecture doc already states this ("Bank cash can expose a difference … but never mutates a calculated accounting value") — the model should say it too.

4. AccountingPeriod — the month

Root holding lifecycleStatus, confirmedAt, confirmedCalculationHash, reopenedAt, reopenReason. Invariants: only legal transitions; LOCKED requires explicit reopen; cannot confirm with blocking issues.

Today those rules live in AccountingPeriodLifecycle (a stateless helper) while the state lives in AccountingPocRepository, and AccountingFilingService bypasses the helper for markFiled, settle and lock — which is exactly why those still throw raw IllegalStateException and surface as HTTP 500. An aggregate with the state and the transition rules in the same object makes bypassing it impossible.

5. FilingArtifact — the JPK output

Separate root, immutable once generated, carrying the calculationFingerprint that produced it. Separate from AccountingPeriod because artifacts accumulate over regenerations and must never be rewritten.

6. AuthorityConfirmation — external evidence

Separate root, immutable, idempotent on external identity. Fix the polymorphic obligation_or_artifact_type here: it is really two subtypes — confirmation of a filing and confirmation of a posting/payment.

7. TaxProfile and EmploymentTimeline — effective-dated configuration

Temporal aggregates. TaxProfile carries ryczałt rate, VAT registration, VAT-EU, ZUS regime, voluntary sickness. EmploymentTimeline carries UOP/JDG periods and is the sole answer to "does a qualifying UoP exist" — deleting has_uop and jdg_active removes a contradiction that today can change the tax through qualifyingUop.

8. Counterparty — identity and VIES state

Root. VIES verification is a property of the counterparty, not of each document, so viesStatus / viesVerifiedAt / vatEuNumber belong here rather than copied onto every VAT row.

The value objects — your biggest gap

Everything below is a bare BigDecimal or String today:

value object	today	what it costs you
Money(amount, currency)	naked BigDecimal	PaymentSummary.totalOutstanding reduces over payments with no currency guard, and you handle EUR
TaxPeriod	LocalDate first-of-month	date(month) conversions scattered through the facade; YearMonth at the API, LocalDate below
VatRate	BigDecimal, unconstrained	unsupported rate silently produces a sales row with no K_ columns
RyczaltRate	BigDecimal	NUMERIC(8,5) vs NUMERIC(7,4) in two tables
Nip / VatEuNumber	String	normalization done ad hoc — V01.018 does UPPER(REGEXP_REPLACE(…,'[^[:alnum:]]','')) in SQL, nowhere in Java
CalculationFingerprint	String	nullable in two tables
DocumentReference	String	trimming done at three call sites

Money alone is worth doing first. It is the single most-repeated concept in the module and the only one where a silent wrong answer is currently possible.

What should not be an entity

Obligation is a computed value object. accounting_poc_obligation is a table, but the RYCZALT/VAT/ZUS amounts are derived from the calculation. Your own code already knows this — AccountingFilingService.payableObligations(snapshot) builds them on the fly, while the table's own comment says "Historical/golden obligation rows retained for compatibility, not new operational ingestion." Two sources of truth for the same number. Persist only the evidence (bank transaction, authority confirmation); compute the obligation.

MonthSnapshot / Readiness / ReconciliationRow are read models, not domain objects. AccountingMonthSnapshot currently mixes calculation, historical comparison, reconciliation and presentation — your architecture doc flags this as the TARGET refactor already.

One caveat, and it cuts the other way: the calculation result should be persisted as an immutable record when a period is confirmed. Right now you store a fingerprint of a number you never stored, so you can detect drift but cannot show what was actually filed. For a tax system that is the wrong trade. Your roadmap already lists this as "reassess whether calculated month snapshots should be persisted" — me would say yes, and make it a first-class ConfirmedCalculation record keyed by fingerprint.

Domain services

Stateless, span aggregates, hold no data:

MonthCalculator — already pure and repository-free. Your best-designed class. Keep it exactly as it is.
Reconciler — BankTransaction ↔ Document / obligations
JpkProjector — AccountingPeriod + Document[] → FilingArtifact. Pure projection, no recalculation (your doc already mandates this)
ZusPolicy — versioned rules. Currently ZusRules2026 hardcodes 2026 constants in Java while TaxProfile is effective-dated in the database; the rules should be effective-dated the same way.
Where invariants live today vs where they belong
invariant	today	belongs to
net + vat = gross	AccountingStagingAcquisitionService	Document
deductible ≤ vat	DefaultAccountingMonthCalculator	VatLine
one treatment per document	DUPLICATE_VAT_CLASSIFICATION in the calculator	Document uniqueness on (treatment, vatRate)
treatment legal for direction	AccountingStagingAcquisitionService	Document
VIES verified for reverse charge	AccountingVatClassifier	Counterparty
legal lifecycle transition	AccountingPeriodLifecycle, bypassed by AccountingFilingService	AccountingPeriod
qualifying UoP	three tables	EmploymentTimeline

The pattern is consistent: validation currently happens at the phase that noticed it, not at the object that owns it. That is why the same rule gets re-checked in staging, in promotion and in calculation — with different strictness each time, which is how MISSING_EXPLICIT_VAT_RATE ended up blockable by a form that cannot supply the field.

Me suggest one concrete first step: build Document + VatLine as a real aggregate with a constructor that refuses invalid state, and have AccountingStagingAcquisitionService construct it instead of validating a parameter list. That single change consolidates six scattered rules, and it is the prerequisite for the accounting_document table anyway.

What me changed and why

The answer comes first, not last. Today "10 843 zł" is buried in a metric row below three cards, unformatted (10843.0000). A sole trader opens this page to learn one thing: what do I owe, by when. Put it at the top, formatted, with the deadline.

14 buttons → 1 at a time. The lifecycle OPEN → CONFIRMED → FILED → PAID → SETTLED → LOCKED is your state machine, not the user's mental model. Theirs is: is it all in → is anything wrong → approve → pay. Four steps, only the live one expanded, the rest collapsed with a lock and a one-line reason. overview.allowedActions already computes this — the page just has to obey it instead of rendering every form and hiding some with th:if.

"Reconcile" and "Promote ready data" disappear. Those are pipeline stages. Run reconcile automatically on every import; promote implicitly when the queue is empty. Only ever surface the MISMATCH and AMBIGUOUS rows — those are the only ones a human can help with. NEW rows promoting themselves is not a loss of control, because nothing reaches canonical without the user having answered whatever was ambiguous about it.

The 13-field review form becomes one question at a time. Your extractor already produces a candidate with confidence. Prefill everything; ask only what it could not determine. Most documents need zero questions. A fuel invoice needs one. Framed as a plain question — "Is this fuel for the business car?" — with three tappable answers, not a vatDeductionRatio numeric input where the user must know that 0.50 means mixed use.

This also fixes the bug me found: vatRate and counterpartyCountry are required by validateDocument but absent from the form. In a question-per-unknown model they cannot go missing, because the form is generated from what the validator still needs.

"Settle" and "Lock" stop being buttons. Both are already evidence-derived — markPaid looks for a matching bank payment, settle looks for an authority posting. When the evidence arrives, advance automatically and tell the user. Asking someone to press "Settle month" after the money already left their account is asking them to do the system's bookkeeping.

Jargon table:

today	proposed
Promote ready data / Reconcile	(gone — automatic)
Confirm month	Approve
Generate JPK_V7M / Record UPO	Approve and file
Record filing / Settle month / Lock month	(gone — evidence-driven)
Sync KSeF / Sync sales / Review third-party	Add more
Reference check	(moved to admin)

Reference check leaves the user UI. It compares against the golden oracle. That is a developer regression tool — valuable, but it belongs behind an admin flag, not on the page a taxpayer uses.

What the backend needs

Mostly nothing new — mostly less:

overview.allowedActions drives which step is open. Already exists.
An issues list where each entry carries a question, options, and the field it sets. Today IssueView is (code, severity, title, message, sourceReference) — a message, not an action. This is the one real addition.
Auto-reconcile on import; drop POST /staging/reconcile and /staging/promote from the UI surface.
Auto-advance on evidence for PAID and SETTLED.
Money with currency on every API field, formatted server-side or with Intl.NumberFormat('pl-PL').

Item 2 is where the design lives. If an issue cannot state itself as a question with answers, it is not ready to show a user — and that constraint will improve the issue set itself.

The 5-minute test

Open → read the number → answer two questions → Approve → copy three account numbers. Nothing else on screen unless asked for. The collapsed "Documents, bank lines, JPK file" row keeps every current view reachable for the one month in twenty when someone needs to dig.

Want me write the Thymeleaf for the step-based page, or spec the IssueView → question model first? Me would do the question model first — it is the load-bearing piece.

visualize the page

Used visualize: show widget

Click the three buttons to walk the month.

What stays constant — the header. Month, deadline, one big number, three tax cards. Same position in every state, so the user always knows where to look. In state 3 the number and label change ("left to pay") but the layout does not move.

What changes — exactly one step is open. States 1 and 2 are the same page; the difference is that the question queue emptied, which unlocked approval. Nothing was navigated to.

State 3 detail worth noting: the payment rows say "We'll tick these off when your bank shows them." That is markPaid doing its job in the background instead of asking the user to press "Settle month". The SETTLED and LOCKED states never appear as words — they happen.

The mono-font account numbers with a copy button are the actual deliverable of the whole month for a sole trader. Today paymentInstructions already computes recipient, account and amount, and MISSING_PAYMENT_CONFIGURATION throws a 500 when the account is unset — worth turning into a one-time setup prompt instead.

Three things this needs from the backend that do not exist yet:

IssueView must carry a question plus answer options, not just a message string.
Auto-reconcile on import, so "Reconcile" and "Promote" never appear.
Money with currency, formatted pl-PL.

Me can spec the IssueView question model next — it is the piece the whole design rests on.
