# Investory Ryczalt KT Notes

Short technical handoff describes the current code, with
focus on the Ryczalt module.

## 1. Product and domain

Basic tax vocabulary:

- **Ryczalt**: revenue tax calculated from eligible revenue and a rate; costs do not reduce the
  ryczałt base in the usual way.
- **VAT**: input/output VAT and the deductible input VAT result.
- **ZUS**: social/health obligations represented as a separate calculation and obligation.
- **Obligation**: an amount to pay, such as RYCZALT, VAT, or ZUS.
- **Payment evidence**: bank transactions matched to obligations. Approval of an invoice is a
  separate concern from proof that it was paid.

The Ryczalt processing unit is one profile and one calendar month (`YearMonth`). Monetary source
facts are normalized to PLN before tax calculations. Historical FX facts are persisted and reused.

## 2. Main Ryczalt flow

```text
source or imported native fact
        -> normalized Ryczalt fact
        -> AccountingPeriod
        -> RYCZALT / VAT / ZUS calculators
        -> persisted calculation snapshots
        -> obligations, settlement, and payment checks
        -> completeness/reconciliation result
        -> freeze or correction
```

Important rules:

- Calculators are pure domain logic where possible. They should not read repositories or Spring
  services.
- Results are persisted with input fingerprints, rule versions, calculator versions, and timestamps.
- Recalculation is targeted to open or dirty periods. Frozen historical periods are read-only.
- Reopening is explicit and requires a reason. Corrections invalidate affected calculations.
- Missing or stale FX must fail the affected calculation; never silently treat an unconverted amount
  as PLN.
- PIT-28 is a narrow annual Web preview over Investory-managed JDG invoices only. It consumes
  persisted `bookedNetPln`, supports one annual Ryczalt rate and PLN/EUR/USD source currencies, and
  blocks incomplete or unsupported facts.
- A counterparty rule match is exact and deterministic. No match or multiple matches means
  `NEEDS_REVIEW`.
- Obligation due dates are calculated in the native Java application boundary. `RYCZALT` and `ZUS`
  are due on the 20th of the next month; `VAT` on the 25th. Weekends and Polish public holidays
  move to the next working day. Mobile and Web display the returned date and never derive it locally.

## 3. Counterparties and invoice decisions

`Counterparty` is a profile-owned legal supplier/customer. Identity uses `taxIdentifier + country`
when a tax identifier exists. Names and aliases do not merge counterparties.

`alias` is presentation-only. Display uses the alias when nonblank, otherwise `legalName`.

`CounterpartyRule` stores reusable matching and accounting decisions:

- source/document/service matching fields;
- classification, VAT treatment, VAT deduction ratio, and ryczałt rate;
- `autoApprove`;
- `PaymentVerificationPolicy`: `REQUIRED` or `NOT_REQUIRED`.

Invoice approval states are `NEEDS_REVIEW` and `APPROVED`. Approval origin is manual, rule-based, or
migration. Payment verification remains independent: an approved invoice can still need payment
evidence.

## 4. Database schema

Schema is PostgreSQL, owned by Flyway. Production schema changes belong in
`app/src/main/resources/sql/migration`.

Core Ryczalt tables:

| Table | Purpose |
|---|---|
| `ryczalt_period` | Profile/month lifecycle and calculation state. Unique per profile/year/month. |
| `ryczalt_invoice` | Normalized income/cost invoice facts and tax fields. |
| `ryczalt_transaction` | Normalized bank/payment transaction facts. |
| `ryczalt_calculation` | Versioned RYCZALT/VAT/ZUS result snapshots and fingerprints. |
| `ryczalt_obligation` | Amounts due for RYCZALT, VAT, and ZUS. |
| `ryczalt_payment_match` | Automatic/manual transaction-to-obligation matches. |
| `ryczalt_fx_rate` | Historical provider FX facts. |
| `ryczalt_source_reference` | Provenance and idempotent source identity. |
| `ryczalt_counterparty` | Profile-owned legal counterparties. |
| `ryczalt_counterparty_rule` | Counterparty-specific accounting and payment rules. |
| `ryczalt_invoice_candidate` | Uploaded/recognized invoice candidate before approval. |
| `ryczalt_profile` | Native taxpayer identity and payment configuration for annual previews and exports. |

The first Ryczalt persistence migration is `V01.026__ryczalt_persistence.sql`. Later migrations add
lifecycle history, payment matching, source identity, counterparties, candidate state, and approval
integrity. Read the complete migration chain before changing an existing table.

The old `accounting_*` tables remain during migration. They are reference/legacy evidence, not the
new canonical Ryczalt source of truth.

## 5. Application and API layers

Important code locations:

- `modules/ryczalt/src/main/java/.../domain`: records, enums, calculators, rules, checkers.
- `modules/ryczalt/src/main/java/.../application`: use cases and `RyczaltAccountingApi`.
- `modules/ryczalt/src/main/java/.../persistence`: JPA entities, repositories, persistence adapter.
- Legacy migration/import code is removed from the active source tree. Historical migration SQL is
  retained only for the later database cleanup.
- `app/src/main/java/.../ryczalt/web`: REST controllers and response DTOs.
- `adapters/web-ui/src/main/java/.../ui/accounting`: server-rendered Web controller and UI contract.
- `app/src/main/java/.../ui/accounting/InProcessRyczaltWebAccountingClient.java`: active native Web client; it calls native controllers and does not depend on the legacy bridge.

Native REST base path:

```text
/api/profiles/{profileId}/accounting
```

Read endpoints:

```text
GET /periods
GET /periods/{month}
GET /periods/{month}/invoices
GET /periods/{month}/transactions
GET /periods/{month}/obligations
GET /periods/{month}/issues
GET /payments?from=YYYY-MM&to=YYYY-MM&type=...
GET /invoices?month=YYYY-MM&counterpartyId=...
```

Lifecycle commands:

```text
POST /periods/{month}/freeze       body: { "reason": "..." }
POST /periods/{month}/reopen       body: { "reason": "..." }
```

Counterparty endpoints are under:

```text
/api/profiles/{profileId}/accounting/counterparties
```

They support list/detail, alias update, and rule CRUD. Invoice recognition/approval is under:

```text
POST /invoices/recognize
GET  /invoices/candidates/{candidateKey}
POST /invoices
```

All profile-scoped reads and writes perform ownership checks through `AuthorizationService`.

### Web adapter note

The active server-rendered Web path uses `InProcessRyczaltWebAccountingClient`. It injects native
Ryczalt REST controllers and calls them directly, preserving REST request/response behavior without a
loopback HTTP call. This is the current migration pattern. Do not add a direct Web dependency on
Ryczalt repositories or application services.

The MVC controller should remain thin: read a Web contract, populate the model, select a template, and send commands through the client. Upload, KSeF, bank import, lifecycle commands, and the JPK download are native; JPK remains a read-only projection and is not submitted externally.

## 6. Review points and current answers

### Architecture

- `InProcessRyczaltWebAccountingClient` is intentional. The active Web path is
  `RyczaltWebAccountingClient -> InProcessRyczaltWebAccountingClient -> native REST controllers`.
  It avoids loopback HTTP while preserving the REST seam. Do not replace it with direct Web
  repository or application-service access.
- The main Ryczalt path is currently **synchronous**. REST commands, invoice approval, bank import,
  KSeF import, settlement, invalidation, and calculation calls run in the request/application
  transaction that invokes them. No Ryczalt domain-event queue or scheduled tax batch currently
  drives this pipeline. Existing scheduled jobs belong to other application concerns.
- Imports and month-input changes invalidate affected calculations; invalidation is not the same as
  recalculation. The calculation command aggregates persisted facts, writes a new snapshot, creates
  obligations, and settles already-imported transactions.

### Domain edge cases

- **FX dates:** `FxRateDatePolicy` selects the previous calendar business day by skipping Saturday
  and Sunday. `NbpFxRateAdapter` then selects the latest NBP table on or before that date, so a
  Monday document uses Friday and an NBP holiday can use the last available earlier table. Polish
  public holidays are not modeled explicitly; verify this before treating the behavior as a full tax
  compliance implementation. Missing rates still fail the calculation.
- **Partial payments:** one obligation may have multiple payment matches. The checker allocates
  compatible transactions in date order and reports `PARTIALLY_PAID` until the allocated amount
  reaches the obligation amount.
- **Overpayments:** allocated payment above the obligation amount reports `OVERPAID`. Manual matching
  rejects allocation above either the remaining obligation or remaining transaction amount, so an
  overpayment must be represented by a real excess transaction/allocation policy rather than by
  exceeding one match.
- **Counterparty learning loop:** resolving a `NEEDS_REVIEW` candidate does not automatically create
  a new rule. Rule creation/update is explicit counterparty rule CRUD. If automatic learning is
  desired, define approval-to-rule criteria and audit behavior first.

### API contract questions

- Current native list endpoints return plain `List` values. There is no `Pageable`, cursor, offset,
  limit, or total-count contract. This is acceptable for current expected volume, but pagination is
  an API change to plan before large invoice/payment histories are exposed.
- Lifecycle commands have no idempotency-key contract. `freeze` and `reopen` are state-guarded commands: retrying an already completed transition currently returns a conflict/error rather than a documented idempotent success. Add explicit idempotency semantics before using automatic retries.

### Ingestion reality

- Native CSV bank import, KSeF sync, invoice recognition, month input settings, and calculation
  commands persist canonical Ryczalt facts.
- The old `/accounting` compatibility routes and bridge have been removed. Native bank and KSeF commands are exposed through dedicated native REST controllers. Third-party KSeF evidence and external JPK filing/submission remain outside the native module; native JPK XML download is supported.
- The native invoice upload flow is candidate-based and purchase-oriented. Web upload creates a
  candidate, then a review form approves it or leaves it in `NEEDS_REVIEW`; source amounts and dates
  remain server-owned. `rememberRule` must be explicitly selected.
- `MANUALLY_CONFIRMED` is the final mobile payment decision for an explicitly confirmed cost
  invoice. It is distinct from `MATCHED`, is reversible to `UNMATCHED` while the period is open, and
  is rejected for frozen periods or bank-matched invoices.

### Data retention and deletion

- `ryczalt_counterparty` and `ryczalt_invoice` currently have no `deleted_at` soft-delete column.
  The native API has no general counterparty or invoice delete operation. Counterparty rule delete
  is a hard delete.
- Profile/period foreign-key cascades can remove dependent rows. Frozen calculation snapshots are
  not a GDPR deletion mechanism and should not be silently changed by cleanup operations. Any PII
  retention or erasure feature needs a documented policy, audit treatment, and frozen-period impact
  decision.

## 7. Native capability status

| Capability | Status |
|---|---|
| RYCZALT calculation | Native |
| VAT calculation | Native |
| ZUS calculation | Native |
| Persistence and calculation snapshots | Native |
| Payment detection and matching | Native |
| Historical NBP FX lookup | Native |
| Counterparties and rules | Native |
| Invoice recognition/approval | Native Web upload, candidate review, and approval |
| Counterparty rule editing | Native Web add, update, delete, and explicit rule remembering |
| Manual invoice payment | Native Web and REST; cost invoices only |
| KSeF acquisition | Native sync control for sales and purchases; no filing/submission |
| Bank transaction import | Native CSV import control; no bank provider connection |
| External ZUS/eZUS verification | Not supported |
| JPK XML download | Native read-only `GET /api/profiles/{profileId}/accounting/periods/{month}/jpk`; submission not supported |
| ZUS DRA XML download | Native unsigned draft `GET /api/profiles/{profileId}/accounting/periods/{month}/zus-dra`; signing/submission not supported |

## 8. Tests, CI, and rollout

Use repository-local Maven caches in managed workspaces:

```bash
MAVEN_USER_HOME=$PWD/.m2 \
  ./mvnw -Dmaven.repo.local=$PWD/.m2/repository test
```

Useful focused checks:

```bash
MAVEN_USER_HOME=$PWD/.m2 \
  ./mvnw -Dmaven.repo.local=$PWD/.m2/repository \
  -pl app,adapters/web-ui -am test

MAVEN_USER_HOME=$PWD/.m2 \
  ./mvnw -Dmaven.repo.local=$PWD/.m2/repository \
  -pl app,adapters/web-ui -am spotless:check
```

Architecture rules are in `app/src/test/java/com/smartbox/investory/architecture/LayerDependencyTest.java`.
Migration validation starts from an empty database; fast database tests use the committed snapshot at
`test-support/src/main/resources/db/snapshot/schema.sql`. Update the snapshot when a migration changes
the application-facing schema.

CI is defined in `.github/workflows/tests.yml`. The main stages are:

- Docs: Markdown links and integration-test registration.
- Formatting: Spotless.
- Unit: full Maven reactor test/package job.
- App startup: Spring composition smoke test.
- Backend integration matrix: migration, accounting/Ryczalt, imports, reconciliation, exports, and
  REST suites.
- UI setup/execution: Chromium-backed browser tests with PostgreSQL fixtures and uploaded artifacts.

The native Web controller and native REST routes are the only active accounting paths. Legacy controllers and adapters have been removed. Historical Accounting source and schema artifacts are retained only for separate cleanup/reference purposes.

## 9. First places to read

1. `modules/ryczalt/README.md` — staged migration and capability matrix.
2. `modules/ryczalt/src/main/java/.../application/RyczaltAccountingApi.java` — application boundary.
3. `RyczaltAccountingFacade` and native persistence repositories — orchestration and storage.
4. `app/.../ryczalt/web/RyczaltAccountingRestController.java` — active REST contract.
5. `V01.026__ryczalt_persistence.sql` plus later `V01.027+` migrations — schema evolution.
6. `docs/development/testing.md` — test ownership and database-test rules.

For investment semantics, read `docs/domain/portfolio-accounting.md`; it describes the separate
brokerage accounting domain and should not be copied into Ryczalt tax calculations.
