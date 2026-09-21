# Ryczalt accounting REST contract

This is the target common contract for web and mobile. It is intentionally smaller than the
legacy `AccountingUserApi` and contains accounting facts and lifecycle commands, not screen models.

The native read foundation is implemented in `RyczaltAccountingQueryService` with Ryczalt-owned
read models and profile-scoped repository queries. The common resources below are now exposed by
`RyczaltAccountingRestController`; older month/mobile routes remain compatibility routes.

## Consumer evidence

The mobile client was inspected at `/home/alex/projects/ryczalt_it` (`investory-accounting-mobile`).
Its API paths are defined in `src/api/accountingPaths.ts` and calls are made by
`src/api/accountingApi.ts`. The mobile mapper is `src/api/mappers/accountingMapper.ts`.

| Current endpoint | Web | Mobile | Current owner | Target |
| --- | --- | --- | --- | --- |
| `GET /api/profiles/{profileId}/accounting/months` | no direct current mobile use | no | legacy Accounting | replace with `/accounting/periods` |
| `GET /api/profiles/{profileId}/accounting/months/{month}/overview` | yes | no | legacy Accounting DTO | replace with `/accounting/periods/{month}` |
| `GET /api/v1/profiles/{profileId}/accounting/months/{month}` | no | yes | `AccountingMobileResponse` over legacy DTO | replace with `/accounting/periods/{month}` |
| `GET /api/profiles/{profileId}/accounting/months/{month}/documents` | yes | no | legacy Accounting DTO | replace with `/accounting/periods/{month}/invoices` |
| `GET /api/v1/profiles/{profileId}/accounting/months/{month}/documents` | no | yes | mobile DTO over legacy DTO | replace with `/accounting/periods/{month}/invoices` |
| `GET .../bank-transactions` | web only | no | legacy Accounting | replace with `/accounting/periods/{month}/transactions` when native sync exists |
| `GET .../payments` | web only | no | legacy Accounting | native `/accounting/payments` |
| `GET .../payments/history` and `/api/v1/.../payments/history` | web and mobile | yes | legacy Accounting | transitional legacy routes; not the native contract |
| `GET/PUT /api/v1/.../auto-approval` | no | yes | legacy Accounting | temporary; product ownership decision required |
| `GET/PUT .../counterparties` | web and mobile read path | mobile read only | legacy Accounting | temporary; not native Ryczalt yet |
| document recognition/save | web and mobile | mobile uses both | legacy Accounting | temporary ingestion workflow; do not call it invoice resource |
| KSeF, bank import, filing, staging commands | web | no mobile use found | legacy Accounting | temporary or remove by product decision |
| settle, lock, reopen | web | no mobile use found | old routes use the app bridge; native routes use `RyczaltAccountingApi` | retain common commands during migration, with native `freeze` replacing `lock` |

The mobile client consumes monthly facts for revenue, Ryczałt/VAT/ZUS amounts, payment rows,
issues, document rows, source/review/payment statuses, bank/reconciliation summaries, filing
summary, and allowed action identifiers. It does not perform accounting arithmetic. Its client
models also contain presentation state such as `MATCH`, `WARNING`, `attention`, and formatted
labels; those remain mobile-owned.

The mobile client does not currently consume `summary.totalObligations`; it uses
`paymentSummary.totalOutstanding` for the payable headline. These are different concepts and must
not be merged in the new contract.

## Stable native resources

These resources do not delegate to `AccountingUserApi`.

All common resources remain profile-scoped:

```text
GET  /api/profiles/{profileId}/accounting/periods
GET  /api/profiles/{profileId}/accounting/periods/{month}
GET  /api/profiles/{profileId}/accounting/periods/{month}/invoices
GET  /api/profiles/{profileId}/accounting/periods/{month}/transactions
GET  /api/profiles/{profileId}/accounting/periods/{month}/obligations
GET  /api/profiles/{profileId}/accounting/periods/{month}/issues
GET  /api/profiles/{profileId}/accounting/payments

GET  /api/profiles/{profileId}/accounting/counterparties
GET  /api/profiles/{profileId}/accounting/counterparties/{id}
PUT  /api/profiles/{profileId}/accounting/counterparties/{id}/alias
GET  /api/profiles/{profileId}/accounting/counterparties/{id}/rules
POST /api/profiles/{profileId}/accounting/counterparties/{id}/rules
PUT  /api/profiles/{profileId}/accounting/counterparties/{id}/rules/{ruleId}
DELETE /api/profiles/{profileId}/accounting/counterparties/{id}/rules/{ruleId}
GET  /api/profiles/{profileId}/accounting/invoices?month=YYYY-MM&counterpartyId={id}

POST /api/profiles/{profileId}/accounting/periods/{month}/settle
POST /api/profiles/{profileId}/accounting/periods/{month}/freeze
POST /api/profiles/{profileId}/accounting/periods/{month}/reopen
```

The top-level period response is a factual summary only:

```text
period: month, status
amounts: revenue, ryczalt, vat, zus, totalObligations
counts: invoices, transactions
obligations: expectedCount, paidCount, outstandingAmount
completeness: status and issue count
allowedActions: SETTLE, FREEZE, REOPEN
```

Detailed invoices, transactions, obligations, and issues are separate collections. No filing,
KSeF transport, bank DTO, JPA entity, Thymeleaf model, or mobile screen model crosses this
contract.

Amounts are decimal JSON strings, dates are ISO local dates, and lifecycle values are stable enum
identifiers such as `OPEN`, `DIRTY`, `CALCULATED`, `PAID`, and `FROZEN`. `allowedActions` contains
typed identifiers, never button labels.

`GET /api/profiles/{profileId}/accounting/payments` returns payment/obligation history across
periods and accepts `from`, `to`, and optional `type` query parameters.

Counterparty responses include `id`, `legalName`, `alias`, `displayName`, `taxIdentifier`,
`country`, `ruleCount`, and `invoiceCount`. Counts are profile-scoped. `RequiredInput` uses
`field`, `inputType`, `required`, structured options (`value`, `labelKey`), `dependsOn`, and
`dependsOnValues`; empty `requiredInputs` is valid and does not imply approval.

Invoice responses expose a compact counterparty (`id`, `legalName`, `alias`) and the persisted
`approvalStatus`, `approvalMethod`, `paymentVerificationPolicy`, and canonical `paymentStatus`.
`NOT_REQUIRED` always returns `NOT_REQUIRED`; required payment currently returns native persisted
matching status, defaulting to `UNMATCHED` when no reliable invoice-level match exists.
Counterparty-rule `vatDeductionRatio` and `ryczaltRate` are JSON strings on both
responses and requests; responses use plain decimal notation without scientific notation.

## Migration policy

The current `/api/v1` mobile routes and `/api` legacy routes are temporary compatibility routes.
They cannot be removed until the mobile repository is switched to the common resources and the web
adapter uses the same application query/command operations. No `/v2` is required: backend and
mobile are controlled repositories and can migrate atomically. The stable resources already use the
native query/lifecycle layer.

`AccountingMobileResponse` should be deleted after the mobile mapper consumes the common factual
DTOs. `AccountingPageController` should receive a web assembler over the same Ryczalt query
operations, without constructing `AccountingUserApi` records.
# Invoice recognition and approval

`POST /api/profiles/{profileId}/accounting/invoices/recognize` accepts multipart field `file`. The server stores a native candidate and returns its `candidateKey`, source identity, source amounts/dates, counterparty resolution, approval state, typed `requiredInputs`, and decimal values as plain JSON strings.

`POST /api/profiles/{profileId}/accounting/invoices` accepts `candidateKey`, optional decision fields (`counterpartyId`, `classification`, `vatTreatment`, `vatDeductionRatio`, `ryczaltRate`, `paymentVerificationPolicy`), `approve`, and explicit `rememberRule`. Decimal request values are strings. Recognized source facts are reloaded from the server-side candidate; the client cannot replace amounts, dates, or reference by resending them. The selected UI month is not accepted as authoritative; the candidate accounting date selects the period. Frozen periods and duplicate canonical references reject the save.

Recognition facts and approval decisions are separate. The client cannot submit arbitrary source amounts or dates. Uploads use a stable SHA-256 source reference and do not persist file contents.
