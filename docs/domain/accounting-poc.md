# Accounting POC

## Goal

The accounting POC proves that a month can be reconstructed deterministically from sales invoices, expense invoices, bank transactions, tax/ZUS obligations and Investory FX conversion, then compared with captured wFirma outputs.

Historical fixtures are anonymized. Source facts, derived facts and golden comparison values must remain distinguishable. The implementation must not introduce hidden balancing values merely to force a match.

## Supported POC setup

The current POC models a Polish JDG operating with:

- ryczałt income tax;
- VAT;
- JDG health contribution;
- JDG compulsory social ZUS when applicable;
- optional employment alongside JDG (`hasUop`);
- domestic PLN revenue and foreign EUR revenue converted through Investory FX;
- sales invoices, purchase invoices, document-level deductible VAT and bank reconciliation.

Historical reconstruction retains the legacy POC-wide `hasUop` flag for compatibility. Operational
months use persisted effective-dated JDG activity, employment, tax-profile and VAT-treatment facts;
the legacy flag is not an operational fallback when those facts exist.

### UoP and JDG ZUS semantics

`hasUop=true` has one precise POC meaning: the owner has an active employment contract whose remuneration satisfies the statutory minimum-remuneration condition for the employment contract to be the primary social-insurance title.

Under that assumption:

- compulsory JDG social ZUS is `0`;
- JDG health contribution remains applicable under the ryczałt regime;
- total JDG ZUS equals the health contribution;
- the stable social-ZUS reason code is `UOP_PRIMARY_INSURANCE`.

When `hasUop=false`:

- the normal JDG compulsory social component applies;
- the JDG health contribution still applies;
- total JDG ZUS is social plus health;
- the stable social-ZUS reason code is `JDG_PRIMARY_INSURANCE`.

The calculation layer stores the stable reason code. Human-readable explanation is derived separately for the UI, so display wording can change without changing accounting semantics or test contracts.

The POC intentionally does not model employment salary, minimum-wage comparison, payroll, employment PIT, multiple employment titles, voluntary sickness insurance selection, benefit periods or a generic ZUS insurance-title resolution engine.

### Historical ZUS golden values

Captured 2026 `ZUS` historical obligations in the current fixtures are **health-only values captured under the historical qualifying-UoP assumption**. They are not a generic expected value for every possible `hasUop` configuration.

With the historical profile (`hasUop=true`), calculated total JDG ZUS consists only of health contribution and can therefore match the captured historical golden.

If the profile is changed to `hasUop=false`, calculated total JDG ZUS also includes compulsory JDG social ZUS. A difference against the historical health-only golden is expected and must not be reported as an ordinary reconstruction `DIFF`. The comparison uses status `HISTORICAL_PROFILE_DIFF` to show that the selected profile differs from the assumptions under which the golden was captured.

`HISTORICAL_PROFILE_DIFF` means:

- the historical source remains unchanged;
- the current calculation is using a different employment/social-insurance assumption;
- the difference is visible and intentional;
- it is not evidence that the accounting reconstruction itself failed.

Accounting obligations and observed bank payments are separate facts. A payment difference must remain visible, as in July: 1,495.04 PLN accounting obligation versus 1,495.00 PLN bank payment.

## Proven 2026 coverage

January through July are historical reconstruction months. August is intentionally an open/partial trailing month.

The golden matrix is enforced by `AccountingGoldenMatrixIT` and the focused scenarios in `AccountingGoldenIT`.

- January: recurring EUR service source restored; NBP rate date 2026-01-30; revenue/ryczalt/VAT/ZUS/FX reconstruct from source facts.
- February: clean ordinary reference month.
- March: JPK confirms declaration rounding: sales VAT 7,488.80 becomes 7,489, deductible purchase VAT 238.38 becomes 238, and VAT payable is 7,251. The model rounds these two components independently before subtraction.
- April: BP fuel reconstructed at 8% invoice VAT with 50% mixed-use vehicle deduction.
- May: source invoices prove 8% fuel VAT; purchase VAT reconstructs to 207.42 PLN.
- June: original FV4 revenue remains in June; the later correction does not rewrite June revenue. Purchase VAT reconstructs to 196.10 PLN.
- July: special sales correction is applied separately; ordinary purchase VAT is reconstructed from expense documents. Accounting ZUS 1,495.04 PLN remains distinct from the 1,495.00 PLN bank payment.
- August: revenue is captured, while complete tax/ZUS goldens are intentionally absent.

## Accounting boundaries

### Source evidence and ingestion

Production source evidence is stored separately from normalized accounting facts. Each uploaded
file and KSeF XML payload is preserved in the source-evidence table with an immutable content hash
and processing status. Source identity is deliberately separate from accounting identity: KSeF
number and upload hash identify the source, while invoice reference identifies the business fact.
Normalized invoice rows carry `source_id` with a database foreign key to the immutable source row;
the provenance link is therefore enforced, not only descriptive.
The statuses are `RECEIVED`, `PARSED`, `REVIEW_REQUIRED`, `IMPORTED` and `FAILED`. The common path
is source -> parse -> review/validation -> normalized facts; only reviewed or proven documents
reach `AccountingInvoiceIngestionService`. Historical golden fixtures are regression evidence only.
Unknown tax-relevant classification or VAT deduction must produce `REVIEW_REQUIRED`, never a guessed
value. KSeF classification intentionally recognizes only explicit, proven rules (for example vehicle
fuel); all other tax-relevant cases remain review-required until a user supplies the missing decision.

### Operational monthly calculation

Historical reconstruction and current calculation are separate modes. January through August 2026
remain frozen reconstruction evidence and may compare with captured goldens. A new operational month
is calculated from normalized rows already persisted for that month; it does not need a month-specific
accounting-result fixture or golden output. The existing VAT, ryczałt, ZUS, FX and payment-reconciliation
calculations remain authoritative for those rows, and UoP keeps its documented health-only/social-ZUS
semantics.

Operational calculation resolves JDG activity, qualifying UoP, ryczałt/VAT applicability and ZUS
settings for the requested period through effective-dated context. The compatibility `hasUop` flag
is retained only for older profiles/snapshots where no effective periods exist; it is not the
authoritative model when period data is present. Each operational snapshot exposes derived readiness: `READY`, `REVIEW_REQUIRED` or `INCOMPLETE`.
Compact issues identify a type, severity, source reference and message. Missing normalized inputs,
missing FX or missing tax/ZUS inputs make the month incomplete. A preserved source with an uncertain
tax classification or deduction makes it review-required. The UI shows the mode, readiness and issues;
historical comparison tables are shown only for reconstruction mode. This is operational completeness
visibility, not a full review inbox.

### Bank ingestion and reconciliation

Raw bank files are source evidence. The file is persisted before parsing, then parsed into normalized
bank transactions through `AccountingBankTransactionIngestionService`. The POC accepts a deterministic
semicolon-delimited export with booking date, related period, reference, counterparty, currency,
amount and note. The source hash makes repeated file import idempotent, while each normalized row uses
a stable source-row identity and retains its source foreign key.

Only explicit classifications are accepted: customer receipt, supplier payment, VAT payment, ryczałt
payment, ZUS payment and internal transfer. Unknown rows remain persisted as `UNKNOWN`, mark the source
`REVIEW_REQUIRED`, and cannot satisfy reconciliation. Internal transfers are retained for audit but are
excluded from income, expense and payment matching.

Reconciliation compares invoice receivables, supplier invoice gross amounts and tax/ZUS obligations
with actual bank movements. It reports matching and payment differences separately; bank cash never
changes a calculated accounting amount. For example, a 1,495.00 PLN ZUS payment against a 1,495.04 PLN
obligation remains a visible difference. Current-month readiness incorporates unresolved bank source
and reconciliation issues, while unpaid future obligations do not become calculation changes.

### Sales period vs cash period

A sales invoice belongs to its accounting/tax period. Payment can happen in a later calendar month.

The selected month must therefore keep separate views of:

1. accounting-period sales invoices;
2. payment candidates related to those invoices;
3. bank transactions physically booked during the selected calendar month.

A payment date must never move an invoice into another accounting period.

### Corrections

FV4/FK1 is a historical July special case. The original June invoice remains at its original June values. The July correction is a separate adjustment.

Do not infer a generic correction engine from this fixture yet.

### Accounting obligations vs bank cash

The accounting obligation and the observed cash payment are independent facts. A bank payment difference must remain visible, as in July ZUS 1,495.04 PLN expected vs 1,495.00 PLN paid.

## VAT rules proven by the fixtures

The POC stores the actual/derived invoice VAT amount and a deduction ratio per expense document.

Current proven treatment:

- business/accounting services: typically 100% deductible VAT when business use is established;
- mixed-use passenger-car fuel: 50% of invoice VAT is deductible;
- captured BP/ANIWIM fuel invoices in the hardened months use 8% invoice VAT;
- document/source VAT values take precedence over category defaults or gross-value reconstruction;
- monthly wFirma purchase-VAT totals are comparison evidence only, never balancing calculation inputs.

Operational VAT treatment is explicit. Domestic VAT, reviewed EU B2B reverse charge, reviewed
non-EU B2B outside-Poland service, domestic purchase and import-of-services treatments are distinct
from currency. Missing or ambiguous treatment remains review-required; currency alone never decides
the VAT result. Accepted EU B2B rows feed a separate VAT-UE projection, which is `NOT_REQUIRED` when
no qualifying rows exist.

For an operational month, every invoice and purchase needs an explicit persisted
`AccountingVatTransaction` treatment. A PLN amount does not classify VAT by itself; missing rows
produce `MISSING_VAT_CLASSIFICATION`. Historical reconstruction may retain the documented fallback.

The operational lifecycle is `OPEN -> READY_FOR_REVIEW -> CONFIRMED -> FILED -> PAID -> SETTLED
-> LOCKED` (with explicit incomplete/issue states before review). Illegal jumps are rejected.
`SETTLED` requires calculated, filed and authority amounts to agree and every positive obligation to
have a matching positive bank payment. Zero obligations may settle without a payment row. Only a
locked period can be reopened, with a mandatory reason; the old confirmation and evidence remain
auditable while the confirmation becomes stale.

Ryczałt deductions use eligible contributions actually paid by the applicable payment-date rule.
An unpaid ZUS obligation is not a PIT deduction. The operational calculator supports the guaranteed
12% rate and reports unsupported rates instead of silently applying 12%.

When only a gross list value is available, provenance must say that the split is derived (for example `WFIRMA_LIST_DERIVED_8` or `WFIRMA_LIST_DERIVED_23`). Source-backed rows use `SOURCE_DOCUMENT`.

## FX rule

Foreign-source EUR revenue is kept separately from its booked PLN accounting value.

The calculation goes through the shared `CurrencyConversion` boundary. Historical fixtures retain the rate date that reproduced the booked accounting amount. For January, document 015 is 7,636 EUR with sale date 2026-01-31 and rate date 2026-01-30, producing 32,171.23 PLN.

Do not silently replace a failed conversion with a fabricated rate. If the conversion provider is unavailable, the fallback golden must remain explicitly labelled.

Each EUR invoice is converted independently using its stored `fx_rate_date`. If a rate is unavailable,
the snapshot lists the affected invoice reference in `FxCalculation.unavailableInvoiceReferences`.
Its stored booked PLN value may be used as an explicit `FX_UNAVAILABLE_USING_BOOKED_FALLBACK`; no
monthly golden total is substituted for an unidentified invoice.

## Source quality

Preferred evidence order:

1. source/KSeF invoice or bank document;
2. wFirma detailed register/list;
3. wFirma monthly analytic/golden total;
4. transparent derivation from a visible gross amount.

Derived fixtures are acceptable for the POC, but the UI and data must preserve provenance. Upgrade them only when source evidence becomes available.

## Document recognition boundary

The stable extraction architecture, including current versus target boundaries, is documented in
[Accounting architecture](../architecture/accounting.md). PDF, image, AI and KSeF-style sources use
source-specific adapters and converge on one candidate/evidence model and one validator before
normalized ingestion. This domain document keeps only the proven source and accounting semantics;
it does not define scanner implementation details.

Reviewed upload persistence remains centralized in `AccountingInvoiceIngestionService`. It validates
the reviewed document, preserves the selected VAT deduction ratio for purchases, applies the POC sales
classification and ryczałt rate, and uses the invoice reference as the idempotency key. KSeF incoming
invoices use the same service after structured XML parsing; their purchase rows retain
`KSEF_SOURCE_DOCUMENT` and the KSeF number in the note. KSeF metadata is read page by page, and one
bad source document skips while other documents continue. Reviewed credit notes persist as signed
sales adjustments in the same normalized invoice table; the historical July correction remains the
only special fixture treatment.

The user-facing `/accounting` page reads only `months` and the aggregate monthly `overview` over the
HTTP `AccountingRestClient` boundary. Detailed documents, bank transactions, payments, filings and
reconciliation endpoints remain available separately. Page mutations (month lifecycle actions,
document recognition/review and bank CSV import) also go through that REST client. Server-side REST
authorization is authoritative; `PROFILE_USER` is read-only in the UI while administrators and
profile owners see mutation controls.

## Filing output

Filing output uses a dedicated projection. Natural-person JDG identity is emitted as
`OsobaFizyczna`; missing identity, counterparty identifier, or explicit KSeF/OFF/BFK/DI evidence
blocks export. Purchase rows use normalized deductible VAT, so `ZakupCtrl` reconciles to the same
document-level deductions used by accounting. Generated JPK_V7M(3) is validated locally against the
bundled official Ministry of Finance XSD and its imported domain schemas; schema failures block the
artifact. Confirmation fingerprints are SHA-256 hashes of stable semantic filing fields and are
independent of collection order or Java object formatting. Due dates use the next Polish working day
when the statutory date falls on a weekend or supported public holiday. CSV remains a separate
reporting export.

The canonical monthly result is the `AccountingMonthSnapshot` produced by `AccountingFactService`.
JPK_V7M(3) is only a projection of that result; it is not a second VAT calculator. The POC preserves
the current Ministry of Finance schema boundary (JPK_V7M(3), effective from February 2026), but does
not submit files to government services. Filing output is blocked while the month has unresolved
`REVIEW_REQUIRED` or `INCOMPLETE` issues, taxpayer configuration is incomplete, or the user has not
confirmed the calculated month. Confirmation stores a calculation hash and becomes stale when
accounting-relevant facts change.

## Payment obligations

VAT, ryczałt and ZUS payment instructions are projections of the canonical calculated amounts. They
contain a deterministic due date, recipient, configured account and transfer title. A missing account
is exposed as `MISSING_PAYMENT_CONFIGURATION`; no account number is fabricated. Calculated obligation,
payment instruction and actual bank payment remain separate concepts. Prompt 3 bank transactions are
actual cash evidence used for reconciliation and can show `PARTIAL`, `OVERPAID` or `UNMATCHED` without
changing the calculated obligation.

## Next milestone

The historical POC is considered proven when the frozen matrix stays stable. The next product milestone is:

> Import one new month without writing a month-specific Flyway data fixture.

That requires an ingestion boundary that produces normalized facts for:

- sales invoices / KSeF;
- expense invoices;
- bank transactions;
- tax/ZUS obligations;
- FX rate-date selection;
- deterministic invoice-payment matching;
- monthly comparison and discrepancy reporting.

The reusable workflow should produce `MATCH`, `DIFF`, `MISSING_SOURCE`/`INPUTS_INCOMPLETE`, `HISTORICAL_PROFILE_DIFF`, and `NO_GOLDEN` states without hiding differences.

For a new month, persist normalized source rows first: reviewed invoices through
`AccountingInvoiceIngestionService`, KSeF invoices through the KSeF controller and the same service,
bank transactions and tax/ZUS obligations in the normalized POC tables, and the selected FX rate date
on each foreign invoice. `AccountingPocRepository` then exposes the month to `AccountingFactService`
for calculation, comparison and payment reconciliation. No month-specific Flyway fixture is needed;
use a separate import/application command or operator flow to write these rows.

## Non-goals for the next step

- generic correction processing beyond the proven July special case;
- AI deciding accounting values;
- private rental PPE accounting;
- forcing historical outputs to match via summary balancing inputs.
