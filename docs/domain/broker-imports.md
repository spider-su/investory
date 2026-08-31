# Broker import contract

This document defines the stable semantics shared by supported broker imports. Provider-specific column mappings and parser details remain implementation/test concerns.

## Source evidence and canonical rows

Uploaded source artifacts and parsed source rows are retained as immutable provenance evidence. Canonical accounts, cash operations, positions, and related reporting facts are derived from that evidence; they are the accounting state consumed by projections and reporting.

Retained source evidence supports audit and reprocessing but does not make Investory a general event-sourcing system.

## Identity resolution

An imported asset reference must resolve deterministically to exactly one existing canonical asset mapping. Unknown or ambiguous mappings fail closed. Importers must not create guessed canonical assets or silently remap one source symbol to multiple active assets.

Canonical asset/currency semantics are defined in `asset-identity-and-money.md`.

## Idempotency

Reprocessing the same source file must not duplicate canonical financial facts. Exact-file identity is tracked by source/file evidence and checksum-based import history.

Overlapping but non-identical exports must rely on stable provider or deterministic synthetic source-record identities. A changed export window must not create a second economic transaction merely because it arrived in another file. Where that guarantee cannot be proven, the importer should fail/review rather than silently double count.

## Import lifecycle

Import history records execution status and counters. Completed imports must have no unexplained failed rows. Partial or failed imports remain visible as such and cannot be presented as complete merely because some rows were written.

Validation and canonicalization failures should preserve enough provenance to identify the source file/row that caused the problem.

## Money and operation semantics

Imported amounts, quantities, prices, and FX-relevant values use decimal financial types; importer code must not construct canonical money from binary floating-point values.

Broker operation labels are normalized into Investory's economic classes. External funding, internal transfers, FX conversions, dividends, interest, taxes, fees, trades, and result-only rows must retain their distinct accounting meaning. Import classification must not create portfolio profit from cash movement alone.

## Reconstruction

Broker-provided position/account values may be used as independent reconciliation evidence where available, but they do not silently overwrite canonical reconstructed accounting state. Position reconstruction, valuation, and settlement semantics remain subject to the C0-C7 verification contract in `../quality/reconciliation.md`.

## Failure behavior

Fail closed for:

- unknown/ambiguous canonical asset identity;
- invalid currency, date, amount, or required provider identity;
- source rows that cannot be classified safely;
- duplicate/conflicting source identities that could double count an economic event;
- incompatible schema/state assumptions.

Do not use broad conflict suppression to turn an unexplained collision into a successful import.

## Supported-provider boundary

IBKR and XTB currently have first-class import support. New broker parsers should implement the same provenance, identity, idempotency, numeric, lifecycle, and reconciliation semantics before they are considered production supported.

## Verification

Importer unit tests cover parser/classifier behavior; PostgreSQL integration tests cover persistence/idempotency; `GoldenRebuildIT` covers a deterministic reduced real-world corpus. Full private-archive verification remains a release/operator check as defined in `../quality/reconciliation.md`.
