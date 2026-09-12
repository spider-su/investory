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

The accounting profile contains the POC-wide `hasUop` flag plus an effective-dated health-contribution basis. The employment flag still applies to all represented months; health-basis rows are profile-scoped and selected by `valid_from`/`valid_to`.

Health basis values are `YTD_REVENUE`, `LOW`, `MEDIUM` and `HIGH`. The selected basis chooses a traceable source input; it never stores or injects a calculated golden output. The historical fixture is explicitly `HIGH`, backed by `HEALTH_CONTRIBUTION_PAID` for January through July. A non-HIGH basis is usable when its corresponding normalized input is present.

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

Accounting obligations and observed bank payments are separate facts. Reconciliation status is `UNPAID`, `PARTIAL` or `PAID`; a payment difference must remain visible, as in July: 1,495.04 PLN accounting obligation versus 1,495.00 PLN bank payment, leaving 0.04 PLN remaining.

Bank imports may omit a provider transaction ID. Such rows use a deterministic `source_row_identity` derived from the source fields, while provider IDs remain preferred when available. This identity is the idempotency boundary and must not be replaced with an import-row number.

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

## Document recognition pipeline

Invoice uploads use a layered scanner. Text PDFs go through PDFBox text extraction and the small
deterministic invoice text parser first. A complete result stays local and does not call AI. Empty or
incomplete PDFs, images (OCR is not implemented yet), and unsupported files use the existing AI
recognition client as fallback. Scanner routing and fallback stay outside accounting calculations and
the controller.

The deterministic draft recognizes invoice number, issue/sale/due dates, seller/buyer lines, currency,
net/VAT/gross totals, and a small fuel/accounting-service category hint. Amount extraction accepts
Polish and English labels, Polish/US number formats, flattened PDF table cells, reverse-charge (`NP`),
VAT-exempt (`ZW`), and correction-invoice totals. It chooses totals only when labels or arithmetic
consistency support them; otherwise it returns `PARTIAL` and the layered scanner may use AI. It is
still intentionally not a universal invoice parser. The next extension point is OCR behind
`ImageDocumentScanner`.

The parser contract is covered by sanitized fixtures for ordinary VAT invoices, flattened table
totals, reverse-charge invoices, VAT-exempt documents, correction invoices, foreign-currency totals,
and incomplete documents. Archive-wide checks are useful evidence, but they are not committed tests:
personal archive files must not become CI fixtures. New parser rules should add a sanitized fixture and
keep the arithmetic checks strict.

Reviewed upload persistence is centralized in `AccountingInvoiceIngestionService`. It validates the
reviewed document, preserves the selected VAT deduction ratio for purchases, applies the POC sales
classification and ryczałt rate, and uses the invoice reference as the idempotency key. KSeF incoming
invoices use the same service after structured XML parsing; their purchase rows retain
`KSEF_SOURCE_DOCUMENT` and the KSeF number in the note. KSeF metadata is read page by page, and one
bad source document is skipped while other documents continue. Reviewed credit notes persist as
signed sales adjustments in the same normalized invoice table; the historical July correction remains
the only special fixture treatment.

The pipeline has simple application feature flags:

```yaml
app:
  accounting:
    document-scanner:
      pdf-enabled: true
      image-enabled: true
      ai-fallback-enabled: true
```

All flags default to `true`. Disable `pdf-enabled` or `image-enabled` to skip that deterministic layer.
Disable `ai-fallback-enabled` to return an incomplete result instead of calling AI.

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
