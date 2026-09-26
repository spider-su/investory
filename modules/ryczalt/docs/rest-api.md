# Ryczalt accounting REST contract

This is the target common contract for web and mobile. It is intentionally smaller than the
legacy `AccountingUserApi` and contains accounting facts and lifecycle commands, not screen models.

The native read foundation is implemented in `RyczaltAccountingQueryService` with Ryczalt-owned
read models and profile-scoped repository queries. The common resources below are now exposed by
`RyczaltAccountingRestController`; older month/mobile routes have been removed.

## Consumer evidence

The mobile client was inspected in the companion `investory-accounting-mobile` checkout.
Its API paths are defined in `src/api/accountingPaths.ts` and calls are made by
`src/api/accountingApi.ts`. The mobile mapper is `src/api/mappers/accountingMapper.ts`.

| Current endpoint | Web | Mobile | Current owner | Target |
| --- | --- | --- | --- | --- |
| native period, invoice, transaction, obligation, issue, and payment routes | web/mobile target | native Ryczalt | `RyczaltAccountingRestController` | stable native contract |
| native counterparty and invoice-recognition routes | web target | native Ryczalt | dedicated Ryczalt controllers | stable native contract |
| `POST .../periods/{month}/calculate` | web/API | no current mobile use | native Ryczalt | aggregates persisted facts and completes one month |
| `POST .../bank/import` and `POST .../ksef/sync` | web/API | no current mobile use | native Ryczalt adapters | source acquisition is native; filing remains separate |
| `GET .../periods/{month}/jpk` | web/API | no current mobile use | native Ryczalt JPK adapter | downloads a generated JPK_V7M(3) XML projection |
| `GET .../periods/{month}/zus-dra` | web/API | no current mobile use | native Ryczalt ZUS adapter | downloads an unsigned ZUS DRA KEDU draft |
| old `/api/v1` mobile and `/accounting/months` routes | removed | none in active native path | none | historical docs only |

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

POST /api/profiles/{profileId}/accounting/periods/{month}/freeze
POST /api/profiles/{profileId}/accounting/periods/{month}/reopen
POST /api/profiles/{profileId}/accounting/periods/{month}/calculate
POST /api/profiles/{profileId}/accounting/bank/import
POST /api/profiles/{profileId}/accounting/ksef/sync
GET  /api/profiles/{profileId}/accounting/periods/{month}/jpk
GET  /api/profiles/{profileId}/accounting/periods/{month}/zus-dra
GET  /api/profiles/{profileId}/accounting/pit28/{year}
```

The top-level period response is a factual summary only:

```text
period: month, status
amounts: revenue, ryczalt, vat, zus, totalObligations
counts: invoices, transactions
obligations: expectedCount, paidCount, outstandingAmount
completeness: status and issue count
allowedActions: FREEZE, REOPEN
```

Obligation `dueDate` is a server-owned ISO date. `RYCZALT` and `ZUS` use the 20th day of the
following month; `VAT` uses the 25th. If that date is a Saturday, Sunday, or Polish public holiday,
the backend moves it to the next working day. Clients display the returned value and do not calculate
tax deadlines locally. The current holiday policy includes Poland's fixed holidays and Easter,
Easter Monday, Pentecost, and Corpus Christi.

Detailed invoices, transactions, obligations, and issues are separate collections. The JPK route
returns an `application/xml` attachment named `JPK_V7M_{profileId}_{month}.xml`; it reads the
completed native period and profile-owned taxpayer configuration. It does not submit to KSeF or
the tax authority. KSeF transport, bank DTO, JPA entity, Thymeleaf model, or mobile screen model
does not cross this contract.

The ZUS DRA route returns an `application/xml` attachment named
`ZUS_DRA_{profileId}_{month}.xml`. It is an unsigned native draft based on the completed period's
ZUS amount and `ryczalt_profile` identity. It is not a signed submission, authority confirmation,
or eZUS integration.

The PIT-28 route returns a JSON draft preview. Version 1 supports only Investory-managed JDG
invoices, one annual Ryczalt rate, PLN/EUR/USD source currencies, persisted booked PLN values,
deductible contribution facts, and reconciled Ryczalt payments. It returns `READY`, `BLOCKED`, or
`UNSUPPORTED`; it does not generate official PIT-28 XML or submit to e-Deklaracje.

Amounts are decimal JSON strings, dates are ISO local dates, and public period lifecycle values are
`OPEN` and `FROZEN`. Internal calculation and obligation states are separate. `allowedActions` contains
typed identifiers, never button labels.

`GET /api/profiles/{profileId}/accounting/payments` returns payment/obligation history across
periods and accepts `from`, `to`, and optional `type` query parameters.

Counterparty responses include `id`, `legalName`, `alias`, `displayName`, `taxIdentifier`,
`country`, `ruleCount`, and `invoiceCount`. Counts are profile-scoped. `RequiredInput` uses
`field`, `inputType`, `required`, structured options (`value`, `labelKey`), `dependsOn`, and
`dependsOnValues`; empty `requiredInputs` is valid and does not imply approval.

Invoice responses expose a compact counterparty (`id`, `legalName`, `alias`, nullable
`taxIdentifier`) and the persisted
`approvalStatus`, `approvalMethod`, `paymentVerificationPolicy`, and canonical `paymentStatus`.
`NOT_REQUIRED` always returns `NOT_REQUIRED`; required payment currently returns native persisted
matching status, defaulting to `UNMATCHED` when no reliable invoice-level match exists.
Counterparty-rule `vatDeductionRatio` and `ryczaltRate` are JSON strings on both
responses and requests; responses use plain decimal notation without scientific notation.

`paymentStatus` is a stable enum: `MATCHED`, `PARTIALLY_MATCHED`, `UNMATCHED`,
`MANUALLY_CONFIRMED`, or `NOT_REQUIRED`. `MANUALLY_CONFIRMED` is only for a cost invoice and is
set by an explicit user command with a payment date. It is not bank evidence, does not mean that a
transaction was matched, and cannot be used for income invoices. `DELETE
/invoices/{invoiceId}/manual-paid` reverses it to `UNMATCHED` while the period is open; both
commands reject frozen periods. Mobile must display it as paid by manual confirmation and must not
silently convert it to `MATCHED` or infer a bank transaction.

### Native write controls

The server-rendered Web adapter uses the same native routes as other clients:

```text
POST   /api/profiles/{profileId}/accounting/invoices/recognize       multipart file
GET    /api/profiles/{profileId}/accounting/invoices/candidates/{candidateKey}
POST   /api/profiles/{profileId}/accounting/invoices                candidate approval
POST   /api/profiles/{profileId}/accounting/invoices/{invoiceId}/manual-paid
DELETE /api/profiles/{profileId}/accounting/invoices/{invoiceId}/manual-paid
POST   /api/profiles/{profileId}/accounting/counterparties/{id}/rules
PUT    /api/profiles/{profileId}/accounting/counterparties/{id}/rules/{ruleId}
DELETE /api/profiles/{profileId}/accounting/counterparties/{id}/rules/{ruleId}
POST   /api/profiles/{profileId}/accounting/bank/import              multipart CSV
POST   /api/profiles/{profileId}/accounting/ksef/sync                month and modes
GET    /api/profiles/{profileId}/accounting/periods/{month}/jpk     JPK_V7M(3) XML download
GET    /api/profiles/{profileId}/accounting/periods/{month}/zus-dra  unsigned ZUS DRA KEDU draft
```

The native month-input settings endpoint is:

```text
PUT /api/profiles/{profileId}/accounting/periods/{month}/input-settings
```

Calculation reads persisted input settings and approved invoice facts. It creates or refreshes the
three native calculations and obligations, then attempts idempotent settlement against already
imported canonical bank transactions.

Upload stores source identity and a candidate, but never accepts client-provided source amounts or
dates as authoritative. Approval creates the canonical invoice only after the user supplies the
required classification and counterparty decision. `rememberRule` is explicit. Bank CSV and KSeF
sync are native acquisition controls; they persist only `ryczalt_*` facts and invalidate affected
open-period calculations. JPK and ZUS DRA are read-only projections of a complete native period;
neither provides filing, signing, or external KSeF/eZUS submission.

## Migration policy

The current `/api/v1` mobile routes and `/api` legacy month routes have been removed.
The mobile contract is now the common factual resource contract above. No `/v2` is required:
backend and mobile are controlled repositories and can migrate atomically. The stable resources
already use the native query/lifecycle layer.

The Web adapter uses the native upload, review, approval, rule, payment, bank, and KSeF operations described above and does not reconstruct legacy Accounting DTOs.
# Invoice recognition and approval

## Canonical namespace

Native accounting REST uses `/api/profiles/{profileId}/accounting`. Invoice history is available
at `/invoices` with optional `month` and `counterpartyId` filters; period detail collections use
their dedicated `/periods/{month}/...` routes. Generated OpenAPI is served at `/v3/api-docs`.

`POST /periods/{month}/calculate` runs the native monthly RYCZALT, VAT, ZUS, and obligation cycle.
It reads persisted approved invoices and month-input settings, creates a missing period when needed,
refreshes obligations, settles compatible imported transactions, and returns the calculation result.

`POST /api/profiles/{profileId}/accounting/invoices/recognize` accepts multipart field `file`. The server stores a native candidate and returns its `candidateKey`, source identity, source amounts/dates, counterparty resolution, approval state, typed `requiredInputs`, and decimal values as plain JSON strings.

`POST /api/profiles/{profileId}/accounting/invoices` accepts `candidateKey`, optional decision fields (`counterpartyId`, `classification`, `vatTreatment`, `vatDeductionRatio`, `ryczaltRate`, `paymentVerificationPolicy`), `approve`, and explicit `rememberRule`. Decimal request values are strings. Recognized source facts are reloaded from the server-side candidate; the client cannot replace amounts, dates, or reference by resending them. The selected UI month is not accepted as authoritative; the candidate accounting date selects the period. Frozen periods and duplicate canonical references reject the save.

Recognition is idempotent by `(profileId, source, SHA-256(content))`. A retry returns the existing
candidate with `sourceState=EXISTING_CANDIDATE`; a new upload returns `NEW_CANDIDATE`. If the source
already produced a canonical invoice, recognition returns `409` and creates no candidate. Human invoice
references are searchable only and are not identity constraints.

Recognition facts and approval decisions are separate. The client cannot submit arbitrary source amounts or dates. Uploads use a stable SHA-256 source reference and do not persist file contents.
