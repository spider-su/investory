# Accounting architecture

This document describes the current Accounting boundaries. It separates source extraction from
accounting-safe validation, normalized facts, calculation and filing projections.

## CURRENT: end-to-end flow

```text
sources
→ immutable source evidence
→ extraction adapters
→ candidate + field evidence
→ common validation
→ normalized accounting facts
→ AccountingFactService
→ AccountingMonthSnapshot
→ historical comparison / bank reconciliation / readiness
→ filing and payment projections
```

The operational lifecycle continues as:

```text
sources
→ extraction
→ normalized facts
→ calculation
→ filing representation
→ payment instruction
→ actual bank payment
→ authority confirmation evidence
→ SETTLED / LOCKED month
```

Runtime source data is separate from test-support and historical golden data. Uploaded documents,
KSeF XML and bank files are immutable source evidence. The snapshots and fixtures under
`test-support` are deterministic regression evidence and historical reconstruction inputs; they are
not runtime adapters and must not drive current operational calculations.

## Bank acquisition boundary

Bank acquisition is provider-neutral before it enters Accounting:

```text
bank provider
    ↓
BankTransactionSource
    ↓
ExternalBankTransaction
    ↓
normalized bank fact and provider metadata
    ↓
classification / reconciliation / PaidContribution / settlement
```

`CsvBankTransactionSource` is the current deterministic offline adapter used by the POC and CI.
It maps the supported semicolon or comma CSV format into external transactions and derives a stable
transaction identity when the file has no provider ID. Accounting classification happens only after
normalization; unknown transactions remain persisted for review. The persisted provider, external
account, external transaction ID and source payload hash support idempotent imports and audit.

The future seam is `EnableBankingTransactionSource implements BankTransactionSource`. It is not
implemented here: it will later map Enable Banking accounts, pagination and provider payloads into
the same external model without changing Accounting calculations or settlement.

The source boundary is therefore:

```text
source acquisition
    ↓
immutable source evidence
    ↓
document extraction adapters
    ↓
document candidate + field evidence
    ↓
common validation
    ↓
normalized accounting facts
```

## Document extraction boundary

The primary extraction boundary is:

```text
AccountingSourceDocument
        ↓
AccountingDocumentExtractor
        ↓
source-specific adapter
        ↓
AccountingDocumentCandidate
+ FieldCandidate / ExtractionEvidence
        ↓
InvoiceValidator
        ↓
ACCEPTED / REVIEW_REQUIRED / FAILED
```

`AccountingDocumentExtractionService` selects an adapter and applies the common validation gate.
`AccountingDocumentCandidate` is a source claim, not an authoritative accounting fact.
`ExtractionResult` carries the candidate, extractor type, parser version and outcome. Field evidence
records why a value was extracted. Current evidence types include explicit labels, table values,
structured sources, OCR, AI, arithmetic-derived values and heuristics.

The intended semantic boundary is:

```text
source document
→ extracted field candidates
→ evidence / provenance
→ validation
→ normalized accounting fact
```

Extraction reports what a source appears to contain. Validation and normalization decide what
Investory is willing to accept as an accounting fact. Extractors may suggest a category, but they do
not decide VAT deduction, final tax treatment, ryczałt applicability or filing acceptance.

## Adapter state

The PDF layout adapter uses PDFBox and the lightweight document-text representation. It preserves page
and line structure and extracts invoice fields deterministically where the layout and labels support
them. Multi-rate VAT summaries, arithmetic checks and source evidence remain subject to the common
validator.

KSeF XML has a structured-source adapter in the application composition layer. Explicit XML fields use
structured-source evidence and still pass through the common candidate and validator boundary.

AI is an extraction fallback/enrichment source. Its output is a candidate and cannot become accepted
without the same deterministic validation used for other sources.

Image and scanned-PDF OCR is not implemented on this branch. The existing image compatibility path
can fall through to AI; it is not an OCR implementation and must not be documented as one.

The older scanner classes are transitional compatibility plumbing behind the newer boundary where
they are still used: `LayeredDocumentScanner`, `DocumentScanner`, `PdfDocumentScanner`,
`ImageDocumentScanner`, `AiDocumentScanner`, `LegacyInvoiceExtractorAdapter` and
`LegacyImageAiExtractorAdapter`. They are not the long-term public extraction port.

Invoice direction uses the configured taxpayer NIP:

```text
seller NIP == configured taxpayer NIP → SALES_INVOICE
buyer NIP == configured taxpayer NIP  → PURCHASE_INVOICE
otherwise                             → REVIEW_REQUIRED
```

Adapters do not silently guess direction. Corrections retain correction semantics independently of
direction where the source establishes that fact.

## Accounting facts and calculation

All accepted or reviewed documents converge on `AccountingInvoiceIngestionService`. No PDF, image,
AI or KSeF adapter writes normalized accounting facts or applies a source-specific VAT deduction
policy. Bank ingestion likewise persists source evidence before normalized bank transactions.

The current calculation boundary is:

```text
normalized accounting facts
    ↓
AccountingFactService
    ↓
AccountingMonthSnapshot
```

`AccountingFactService` currently orchestrates monthly calculation, historical comparison, bank
reconciliation, readiness and snapshot assembly. The snapshot is the canonical POC monthly result.
`AccountingCalculationResult` is the calculation-only result used by the current calculator path;
the snapshot adds comparison, reconciliation, readiness and presentation concerns around it.

## TARGET: pure calculation architecture

The following is a refactoring direction, not the current stable production boundary:

```text
sources
→ source evidence
→ extraction port
→ validated normalized facts
→ AccountingMonthCalculator
→ AccountingCalculationResult
    ├→ historical comparison
    ├→ bank reconciliation
    ├→ readiness
    ├→ filing exporter
    └→ payment projector
```

`AccountingCalculationInput`, `AccountingMonthCalculator` and `AccountingCalculationResult` are
target pure-calculation concepts. Comparison, reconciliation and readiness should remain outside the
pure calculator as this refactoring matures.

## CURRENT: filing and payment projections

The current POC filing path is:

```text
AccountingMonthSnapshot
    ↓
AccountingFilingService
    ↓
AccountingJpkGenerator
    ↓
JPK projection
```

Payment instructions are also projections of the calculated snapshot. They are not a second
calculation path and actual bank payments remain separate evidence.

The responsibility split is:

```text
calculation        = authoritative accounting amounts
filing             = projection of accepted canonical accounting data
payment instruction = operational projection
bank transaction   = observed cash evidence
```

No export layer independently recalculates tax.

The current obligation matrix is:

| Obligation | Calculated input | Operational projection | Confirmation evidence |
| --- | --- | --- | --- |
| PIT ryczałt | revenue and paid deductible ZUS | PPE payment instruction | imported/manual tax-account posting |
| VAT | sales VAT and deductible purchase VAT | JPK projection and VAT payment | JPK UPO and imported/manual tax-account posting |
| VAT-UE | accepted qualifying EU B2B facts | VAT-UE projection when required | VAT-UE UPO |
| ZUS | effective insurance state and supported 2026 rules | DRA representation and NRS payment instruction | imported/manual eZUS acceptance or account evidence |
| KSeF | invoice source evidence | issue/receive through the KSeF boundary | KSeF number/status |

These are representations and evidence boundaries. Government submission APIs are not part of this
POC.

This is a bounded filing-ready POC projection: natural-person JDG identity, KSeF/OFF/BFK/DI
semantics, deductible purchase VAT, official local JPK_V7M(3) XSD validation, typed filing issues,
deterministic semantic confirmation fingerprints and business-day due dates are implemented. It
does not submit to government services; confirmation evidence is imported or recorded manually.

## CURRENT: bank reconciliation

```text
raw bank source
    → immutable source evidence
    → normalized bank transactions
    → classification
    → reconciliation
```

Calculated accounting obligations, actual bank movements and reconciliation state remain separate.
Bank cash can expose a difference or unresolved classification, but never mutates a calculated
accounting value.

## CURRENT: historical comparison and fixtures

Historical goldens may reconstruct or compare historical months, but they must never influence the
canonical calculation:

```text
normalized facts → calculate → compare with golden
```

Never use:

```text
golden → influence calculation
```

The frozen 2026 matrix is regression evidence for the POC. A new operational month must be calculated
from normalized rows persisted through the normal ingestion boundaries, without writing a month-
specific golden or Flyway balancing fixture.

## Status boundaries

Calculation readiness, filing status, payment status, authority confirmation status and period
lifecycle status are separate concepts. A bank transfer alone does not prove that an obligation was
filed, posted by an authority or settled. A period can reach `SETTLED` only after calculated, filed
and authority-posted amounts reconcile; `LOCKED` is an explicit lifecycle action that requires no
blocking issues. New tax-relevant evidence for a locked period requires an explicit reopen.

Rule/version metadata is intentionally lightweight. The supported calculation uses versioned 2026
ZUS policy and the JPK_V7M(3) filing schema; this is reproducibility metadata, not a dynamic rules
engine.

## Related domain contract

See [Accounting POC](../domain/accounting-poc.md) for supported JDG scope, UoP/ZUS semantics, VAT and
FX rules, historical proof values, correction scope and domain non-goals.
# Accounting application boundary

The diagnostic `/poc/accounting` page remains a development and evidence view. The product page is `/accounting` and reads a stable profile-scoped API contract. Its flow is:

`Browser -> AccountingPageController -> AccountingRestClient -> AccountingUserApi -> AccountingUserFacade -> accounting services`

The MVC controller depends only on the typed client. The facade maps internal calculations to user DTOs and owns the user-facing lifecycle, next-action, issue, source, document, bank, payment, filing, and reconciliation projections. Production sources remain separate from normalized facts; unknown tax-relevant values remain reviewable.
