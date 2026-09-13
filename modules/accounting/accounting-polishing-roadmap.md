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
