# Ryczalt cutover gap audit

Audit basis: current `develop` source, not the historical stage plan. This document describes the
current code and the work still required before deleting the temporary `accounting` dependency or
the `modules/accounting` module.

## Executive result

`modules/ryczalt` has no production dependency on `modules/accounting`. Its native application
boundary is `RyczaltAccountingApi` implemented by `RyczaltAccountingFacade`. The former
`LegacyAccountingApiBridge` has been removed; remaining legacy route and DTO dependencies are
tracked below as migration gaps.

The mobile consumer checkout is available at `/home/alex/projects/ryczalt_it`. It is an Expo/
React Native client named `investory-accounting-mobile`, not a missing repository. Its concrete
paths and field usage are recorded in `docs/rest-api.md`.

Native period, invoice, transaction, obligation, issue, payment, counterparty, bank-import, KSeF,
calculation, and lifecycle paths are now wired. The old Accounting routes remain separate
compatibility routes. Filing, confirmation, JPK, and legacy-only staging remain outside the native
scope.

The NBP code added in the previous stage is a native core capability, but it is not exposed by the
public accounting application contract. Bank and KSeF have reusable generic transports, but no
Ryczalt application sync path. eZUS has no reusable production verification client.

## Direct Ryczalt to Accounting inventory

| Location | Dependency | Classification |
| --- | --- | --- |
| `modules/ryczalt/pom.xml` | no `com.smartbox:accounting` dependency | `REMOVED` |
| `ryczalt/application/RyczaltAccountingApi.java` | native query/lifecycle port | Ryczalt-owned application contract |
| `app/accounting/application/LegacyAccountingApiBridge.java` | maps native records and qualifies `accountingUserFacade` | Removed |

No other production class under `modules/ryczalt` imports an Accounting class. The migration
service's SQL references legacy `accounting_poc_*` tables, but that is an explicit one-way import
path and is classified separately below.

## Adapter method audit

The old-route methods below are implemented by `LegacyAccountingApiBridge`.

| Method | Current handling | Classification | Replacement/blocker |
| --- | --- | --- | --- |
| `months` | delegates to Accounting | `LEGACY_DELEGATED` | Native period/month query exists; REST wiring remains |
| `overview` | delegates to Accounting | `LEGACY_DELEGATED` | Native API view assembler missing |
| `issues` | delegates to Accounting | `LEGACY_DELEGATED` | Native issue query exists; REST wiring remains |
| `documents` | delegates to Accounting | `LEGACY_DELEGATED` | Native invoice query exists; REST wiring remains |
| `bankTransactions` | delegates to Accounting | `LEGACY_DELEGATED` | Native transaction query exists; acquisition remains legacy |
| `payments` | delegates to Accounting | `LEGACY_DELEGATED` | Native obligation query exists; REST wiring remains |
| `paymentHistory` | delegates to Accounting | `LEGACY_DELEGATED` | Native persisted-match history query exists; REST wiring remains |
| `filings` | delegates to Accounting | `LEGACY_DELEGATED` | Filing lifecycle is not implemented in Ryczalt |
| `reconciliation` | delegates to Accounting | `LEGACY_DELEGATED` | Native reconciliation view missing |
| `counterparties` | delegates to Accounting | `LEGACY_DELEGATED` | Counterparty model/read path not in Ryczalt |
| `counterpartyDocuments` | delegates to Accounting | `LEGACY_DELEGATED` | Depends on legacy counterparty/document model |
| `updateCounterpartyAlias` | delegates to Accounting | `LEGACY_DELEGATED` | Legacy-owned counterparty persistence |
| `autoApprovalSettings` | delegates to Accounting | `LEGACY_DELEGATED` | Not part of current Ryczalt domain |
| `updateAutoApprovalSettings` | delegates to Accounting | `LEGACY_DELEGATED` | Not part of current Ryczalt domain |
| `recognize` | delegates to Accounting | `LEGACY_DELEGATED` | Upload/document recognition not native |
| `reviewSource` | delegates to Accounting | `LEGACY_DELEGATED` | Legacy source-evidence workflow |
| `saveReviewed` / `saveReviewedResult` | delegates to Accounting | `LEGACY_DELEGATED` | Native reviewed-invoice mutation missing |
| `issueInvoice` | delegates to Accounting | `LEGACY_DELEGATED` | Issuance is not a Ryczalt requirement currently |
| `recordManualIncome` | delegates to Accounting | `LEGACY_DELEGATED` | Native manual-income mutation missing |
| `importBank` | delegates to Accounting | `LEGACY_DELEGATED` | Generic CSV source exists, Ryczalt sync/persistence path missing |
| `syncKsef` / `reimportKsef` | delegates to Accounting | `LEGACY_DELEGATED` | Generic KSeF transport exists; Ryczalt FA(3) normalizer/sync missing |
| `syncKsefSeller` / `syncKsefThirdParty` | delegates to Accounting | `LEGACY_DELEGATED` | Same KSeF adapter/application gap |
| `generateJpk` | delegates to Accounting | `LEGACY_DELEGATED` | Filing/export contract not implemented natively |
| `filingArtifact` | delegates to Accounting | `LEGACY_DELEGATED` | Filing artifact persistence/read model missing |
| `recordConfirmation` | delegates to Accounting | `LEGACY_DELEGATED` | External confirmation lifecycle missing |
| `confirm` / `file` | delegates to Accounting | `LEGACY_DELEGATED` | Filing state machine missing |
| `settle` | native only when `ryczalt_period` exists; otherwise legacy | `LEGACY_DELEGATED_BUT_NATIVE_CAPABILITY_EXISTS` | Remove fallback after native period coverage/read API is complete |
| `lock` | native freeze only when period exists; otherwise legacy | `LEGACY_DELEGATED_BUT_NATIVE_CAPABILITY_EXISTS` | Replace fallback; retain frozen-period checks |
| `reopen` | native reopen only when period exists; otherwise legacy | `LEGACY_DELEGATED_BUT_NATIVE_CAPABILITY_EXISTS` | Replace fallback; retain correction/reason semantics |

The default `AccountingUserApi` implementations for `issueInvoice` and `recordManualIncome` do not
make those capabilities native: the adapter explicitly delegates them.

## Public contract and DTO ownership

The native contract owns only Ryczalt read models and lifecycle commands. Old-route DTO mapping stays
in `app`; `AccountingUserApi` remains a temporary compatibility contract for consumers not yet
migrated.

Classification:

| DTO family | Owner today | Classification |
| --- | --- | --- |
| `AccountingUserApi` nested records | `modules/accounting` | `SHOULD_REPLACE` with a deliberately scoped Ryczalt application contract |
| `AccountingStagingApi` nested records | `modules/accounting` | `ACCOUNTING_INTERNAL` for current cutover; staging remains legacy-only |
| `AccountingRestClient` | `adapters/web-ui` but extends both Accounting APIs | `GENERIC_PUBLIC_CONTRACT` in name only; still Accounting-owned |
| `AccountingMobileResponse` | `app` and maps Accounting DTOs | `SHOULD_REPLACE` after REST contract migration |

Do not move all nested records into a shared dumping ground. Define only the records required by the
native Ryczalt endpoints and keep adapter/web DTO mapping at the application boundary.

## Endpoint ownership map

All routes below are under `/api/profiles/{profileId}/accounting` and are currently exposed by
`app` controllers.

| Endpoint group | Entry point | Current owner | Result |
| --- | --- | --- | --- |
| months, overview, issues, documents, bank transactions, payments | `AccountingRestController` | app bridge; native period data or legacy fallback | `NATIVE_OR_LEGACY_FALLBACK` |
| counterparties and aliases | `AccountingRestController` | legacy adapter -> Accounting | `LEGACY_DELEGATED` |
| document recognition/review/manual income/issue | `AccountingRestController` | legacy adapter -> Accounting | `LEGACY_DELEGATED` |
| bank import | `AccountingRestController` and web UI controller | legacy adapter -> Accounting | `LEGACY_DELEGATED` |
| KSeF sync/reimport/seller/third-party | `AccountingRestController` and web UI controller | legacy adapter -> Accounting | `LEGACY_DELEGATED` |
| confirm/file/JPK/confirmation | `AccountingRestController` | legacy adapter -> Accounting | `LEGACY_DELEGATED` |
| settle/lock/reopen | `AccountingRestController` and web UI controller | app bridge; native period data or legacy fallback | `NATIVE_OR_LEGACY_FALLBACK` |
| staging rows/reconcile/promote | `AccountingStagingRestController` | `AccountingStagingFacade` directly | `LEGACY_DELEGATED` / outside Ryczalt bridge |
| web accounting page | `AccountingPageController` -> `InProcessAccountingClient` | Accounting DTOs plus `accountingUserFacade` and `accountingStagingFacade` | Legacy contract dependency |
| mobile accounting responses | `AccountingMobileRestController` -> `AccountingMobileResponse` | app bridge input, Accounting DTO mapping | Legacy DTO dependency |
| common native accounting resources | `RyczaltAccountingRestController` | native Ryczalt query/lifecycle services | `STABLE_NATIVE`; web/mobile migration pending |

The native REST controller injects `RyczaltAccountingApi` directly. Old REST, mobile, staging, and
Web compatibility controllers have been removed. The native Web client gets period/reference data
from the native controller response.

## Web/mobile consumer matrix

| Consumer | Actual client path | Fields/behavior used | Finding |
| --- | --- | --- | --- |
| Web `AccountingPageController` | in-process `AccountingRestClient` | legacy overview, documents, payments, issues, filings, reconciliation, counterparties, staging | Accounting-owned contract; not native |
| Mobile `ApiAccountingRepository` | `/api/v1/.../months/{month}` plus `/documents` | factual monthly totals, tax values, payment rows, issue rows, status summaries, invoice rows | Mobile does not calculate accounting values |
| Mobile payments | `/api/v1/.../payments/history` | type, period, amounts, dates, status | Payment history is a separate read |
| Mobile add-cost flow | legacy `/api/.../documents/recognize` and `/documents` | candidate fields, VAT options/required inputs, reviewed-document mutation | Temporary legacy ingestion dependency |
| Mobile automation settings | `/api/v1/.../auto-approval` | enabled, max amount, trusted categories | Legacy feature; no native Ryczalt owner |
| Mobile counterparties | legacy `/api/.../counterparties` | id, identity, name/alias, document count | Legacy feature; not used by the core monthly read |

No separate mobile aggregation service was found in the backend. `AccountingMobileResponse` is a
server-side DTO mapping layer; the mobile repository separately maps it into screen/domain state.
The mobile-only presentation states (`MATCH`, `WARNING`, `attention`, labels, and formatting) are
not REST contract requirements.

## Integration audit

| Integration | Status | Evidence | Cutover gap |
| --- | --- | --- | --- |
| NBP/FX | `PARTIAL` | `NbpClient` -> `NbpFxRateAdapter` -> `FxRateSourcePort` -> `RyczaltFxRateService` and `ryczalt_fx_rate` | No public Ryczalt API/application operation invokes it yet |
| Bank | `PARTIAL` | `CsvBankTransactionSource` and generic bank DTOs exist in `integrations` | No Ryczalt transaction source port, sync service, source-reference persistence, or endpoint |
| KSeF | `PARTIAL` | `KsefClient` and `KsefInvoiceService` exist in `integrations` | No Ryczalt FA(3) normalization, invoice import, idempotency, or endpoint |
| eZUS | `UNSUPPORTED` | Only `ZusClient`/`MockZusClient` support exists; no production verification path | Do not invent a client; keep external verification unsupported |

The existing Ryczalt `PaymentChecker` and `SettlementService` consume canonical transactions. They
are not blockers themselves; the missing piece is native transaction acquisition.

## Database dependency audit

| Dependency | Classification | Finding |
| --- | --- | --- |
| Legacy import utility and source tables | `REMOVED` / `HISTORICAL_SCHEMA` | No production Ryczalt code reads `accounting_poc_*`; the old tables remain only in historical Flyway migrations |
| `HappyInvestorStage2Fixture` references legacy migration fixture provenance | `REFERENCE_TEST_ONLY` | Test documentation/evidence, not runtime |
| `AccountingReferenceMatrixE2EIT` and Accounting golden/staging tests query `accounting_reference_*` / `accounting_poc_*` | `REFERENCE_ONLY` / `LEGACY_ONLY` | Certification and legacy tests; not a Ryczalt runtime blocker by themselves |
| Ryczalt calculators, persistence, settlement, lifecycle | none | No normal runtime SQL read of `accounting_*` found |

Reference tables must not be copied into canonical Ryczalt persistence just to remove test/oracle
dependencies.

## Parity status

`ParityReport` and its unit test exist, but there is no current executable cross-module Ryczalt-vs-
Accounting certification test covering the requested historical matrix. The existing integration
test `RyczaltPersistenceMigrationIT` verifies migration/persistence, not old/new calculated parity.

Status: `PARITY_PARTIAL`.

Supported native unit coverage exists for Ryczałt, VAT, ZUS, settlement, invalidation, and FX date
policy. FX/booked-PLN and obligations are persisted/mapped, but no full old/new matrix currently
certifies them through both implementations. Do not label parity complete.

## Lifecycle, isolation, and frozen history

Ryczalt owns calculation, invalidation, freeze, reopen, correction, settlement, manual matching, and
completeness services for periods present in its schema. The adapter's native lifecycle branches
preserve frozen-period protection. Any future replacement of document/bank/KSeF mutations must use
`RyczaltPeriodLifecycleService.invalidate` and explicit reopen/correction behavior; direct writes to
frozen periods are not acceptable.

Ryczalt persistence queries are profile-scoped and schema constraints include `profile_id`. The
legacy fallback operations remain dependent on the Accounting facade's own profile semantics until
those operations are replaced.

## What breaks if Accounting is deleted today?

### Remaining blockers before removing legacy Accounting

1. Remaining web/mobile consumers still use the old `AccountingUserApi` DTO contract.
2. Native read/application services for months, overview, issues, invoices, transactions, payments,
   reconciliation, and counterparties.
3. Native document recognition/review/manual income behavior, or an explicit decision to classify
   those legacy-only capabilities as `NOT_REQUIRED`.
4. Native bank import and transaction synchronization.
5. Native KSeF invoice synchronization and source-reference handling.
6. Native filing/confirmation behavior, or an explicit product decision that filing is outside
   Ryczalt scope.
7. Removal of legacy fallbacks from `settle`, `lock`, and `reopen` after coverage is guaranteed.

### Blocks removal of `modules/accounting` itself

1. `app` accounting REST/mobile controllers and Accounting-owned response DTOs.
2. `adapters/web-ui`'s `AccountingRestClient`, `InProcessAccountingClient`, page controller, and
   templates expecting Accounting DTO/status semantics.
3. `AccountingStagingRestController`, `AccountingStagingFacade`, and staging tests.
4. Accounting-only reference/golden/parity tests, unless deliberately retained in a separate
   certification module or replaced with Ryczalt-owned fixtures.
5. Legacy source tables and their historical migrations. They are not runtime dependencies, but
   database cleanup still requires a separate data-retention and migration plan.

## Do not migrate

The following should not be copied into Ryczalt merely to obtain API parity:

- POC-only `accounting_poc_*` workflow and its internal staging machinery after migration use ends.
- `accounting_reference_*` oracle/reference tables as canonical Ryczalt facts.
- Accounting-specific DTOs that exist only to expose legacy staging, filing, or source-evidence
  internals when the product does not require them.
- Duplicate Accounting calculators or legacy adapters; Ryczalt calculators and canonical facts remain
  the replacement source of truth.
- eZUS production behavior without a real supported client and contract.

## Ordered implementation backlog

1. Define a Ryczalt-owned read/application contract for the required period, invoice, transaction,
   payment, issue, and lifecycle views. **DONE for the stable native REST surface.**
2. Wire the native Ryczalt query foundation to the scoped REST contract and keep legacy routes as
   compatibility routes. **DONE.** Bank acquisition/synchronization remains a separate gap.
3. Add a Ryczalt bank source port and sync service around the reusable bank source. Persist source
   references, enforce profile/frozen-period rules, and expose the bank import operation. **DONE.**
4. Add a Ryczalt KSeF FA(3) normalizer/import service around `KsefClient`, including idempotency and
   provenance. This removes KSeF delegation.
5. Decide the product boundary for recognition, counterparties, filing, confirmations, and
   auto-approval. Implement only required native operations; classify the rest `NOT_REQUIRED` or
   remove their routes.
6. Expose the existing NBP FX service from the required native application use case and add the
   end-to-end persistence/use-case test. This removes the current `IMPLEMENTED_CORE_NOT_WIRED` gap.
7. Replace the Accounting mobile/web DTO mapping and staging client with the scoped native contract.
8. Migrate remaining consumers, remove compatibility fallbacks, then delete `modules/accounting`.
9. Run replacement parity/certification tests for supported Ryczałt, VAT, ZUS, FX/booked PLN, and
   obligations. Retire or relocate legacy-only reference tests.
10. Delete `modules/accounting` only after controllers, UI, staging, tests, migration tooling, and
    dependency audits are clean.

The concrete common REST design and current consumer field inventory are in
`modules/ryczalt/docs/rest-api.md`.

## Current classification summary

```text
NATIVE_RYCZALT       calculators, persistence, invalidation, freeze/reopen/correction,
                     payment checking, settlement for Ryczalt-backed periods
LEGACY_DELEGATED     all read/document/bank/KSeF/filing/counterparty operations and fallbacks
MISSING              native application contract, bank/KSeF sync, filing/read model coverage
NOT_REQUIRED         eZUS unless a real product integration is introduced; obsolete POC internals
REFERENCE_ONLY       accounting_reference_* and old/new certification fixtures
HISTORICAL_SCHEMA    accounting_poc_* table definitions retained in old Flyway migrations
```
