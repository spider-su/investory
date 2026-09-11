# Accounting POC

## Goal

The accounting POC proves that a month can be reconstructed deterministically from sales invoices, expense invoices, bank transactions, tax/ZUS obligations and Investory FX conversion, then compared with captured wFirma outputs.

Historical fixtures are anonymized. Source facts, derived facts and golden comparison values must remain distinguishable. The implementation must not introduce hidden balancing values merely to force a match.

## Proven 2026 coverage

January through July are historical reconstruction months. August is intentionally an open/partial trailing month.

The golden matrix is enforced by `AccountingGoldenMatrixIT` and the focused scenarios in `AccountingGoldenIT`.

- January: recurring EUR service source restored; NBP rate date 2026-01-30; revenue/ryczalt/VAT/ZUS/FX reconstruct from source facts.
- February: clean ordinary reference month.
- March: document-level purchase VAT is 238.38 PLN; the captured VAT obligation differs by 1 PLN from the current whole-PLN calculation. Keep the difference explicit until the declaration/filing semantics are source-proven.
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

## Source quality

Preferred evidence order:

1. source/KSeF invoice or bank document;
2. wFirma detailed register/list;
3. wFirma monthly analytic/golden total;
4. transparent derivation from a visible gross amount.

Derived fixtures are acceptable for the POC, but the UI and data must preserve provenance. Upgrade them only when source evidence becomes available.

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

The reusable workflow should produce `MATCH`, `DIFF`, `MISSING_SOURCE`/`INPUTS_INCOMPLETE`, and `NO_GOLDEN` states without hiding differences.

## Non-goals for the next step

- generic correction processing beyond the proven July special case;
- AI deciding accounting values;
- private rental PPE accounting;
- forcing historical outputs to match via summary balancing inputs.
