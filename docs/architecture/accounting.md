# Accounting architecture

This document describes the current Accounting boundaries. It separates source extraction from
accounting-safe validation, normalized facts, calculation and filing projections.

## Current flow

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

Runtime source data is separate from test-support and historical golden data. Uploaded documents,
KSeF XML and bank files are immutable source evidence. The snapshots and fixtures under
`test-support` are deterministic regression evidence and historical reconstruction inputs; they are
not runtime adapters and must not drive current operational calculations.

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

### Target calculation architecture

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

## Filing and payment projections

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

This is not yet fully filing-ready. Hardening areas include natural-person JDG taxpayer identity,
KSeF/OFF/BFK/DI semantics, deductible purchase VAT projection, official XSD validation, typed filing
issues, deterministic semantic confirmation fingerprints and business-day due dates.

## Historical comparison and fixtures

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

## Related domain contract

See [Accounting POC](../domain/accounting-poc.md) for supported JDG scope, UoP/ZUS semantics, VAT and
FX rules, historical proof values, correction scope and domain non-goals.
