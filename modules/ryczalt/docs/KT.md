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
source or imported legacy fact
        -> normalized Ryczalt fact
        -> AccountingPeriod
        -> RYCZALT / VAT / ZUS calculators
        -> persisted calculation snapshots
        -> obligations and payment checks
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
- A counterparty rule match is exact and deterministic. No match or multiple matches means
  `NEEDS_REVIEW`.

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
- `modules/ryczalt/src/main/java/.../migration`: one-way legacy import; not a runtime adapter.
- `app/src/main/java/.../ryczalt/web`: REST controllers and response DTOs.
- `adapters/web-ui/src/main/java/.../ui/accounting`: server-rendered Web controller and UI contract.
- `app/src/main/java/.../ui/accounting/InProcessRyczaltWebAccountingClient.java`: active Web bridge.

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
POST /periods/{month}/settle
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
Ryczalt repositories or application services. The older `InProcessRyczaltControllerAccountingClient`
path is
kept only for legacy `AccountingRestClient` compatibility.

The MVC controller should remain thin: read a Web contract, populate the model, select a template,
and send lifecycle commands through the client. Unsupported upload/KSeF/bank/filing operations must
remain explicit unsupported features; do not fake successful integration behavior.

## 6. Review points and current answers

### Architecture

- **Direct controller injection is temporary migration debt.** `InProcessRyczaltWebAccountingClient`
  exists to simulate a REST client without a loopback HTTP call. It preserves the current
  controller response contract, but bypasses HTTP filters, request logging, exception translation,
  and serialization. The target design is for REST and Web adapters to call
  `RyczaltAccountingApi`/`RyczaltAccountingFacade` directly through a shared application boundary.
- Do not move repositories, persistence entities, or tax services into the Web adapter while this
  bridge remains. Treat the bridge as a cutover aid and remove it when the shared boundary is ready.
- The main Ryczalt path is currently **synchronous**. REST commands, invoice approval, bank import,
  KSeF import, settlement, invalidation, and calculation calls run in the request/application
  transaction that invokes them. No Ryczalt domain-event queue or scheduled tax batch currently
  drives this pipeline. Existing scheduled jobs belong to other application concerns.
- Imports invalidate affected calculations; invalidation is not the same as recalculation. A caller
  must invoke the calculation application service to create a new snapshot.

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
- Lifecycle commands have no idempotency-key contract. `settle` is mostly repeat-safe because
  existing payment pairs are not duplicated, but `freeze` and `reopen` are state-guarded commands:
  retrying an already completed transition currently returns a conflict/error rather than a
  documented idempotent success. Add explicit idempotency semantics before using automatic retries.

### Ingestion reality

- Native application services for CSV bank import and KSeF import exist, but the active native
  Ryczalt accounting REST controller does not expose those commands yet.
- During migration, the old `/accounting` routes call `LegacyAccountingApiBridge`, which delegates
  bank and KSeF acquisition to `RyczaltBankApi` and `RyczaltKsefApi` for native periods. This is the
  current ingestion path. Third-party KSeF evidence and JPK/filing remain on legacy behavior.
- The native invoice upload flow is candidate-based and purchase-oriented. The configured
  recognition provider may still be unsupported; an explicit unsupported-provider error is expected.

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
| Invoice recognition/approval | Partial; candidate flow exists, purchase upload is the current flow |
| KSeF acquisition | Native application service, exposed through the legacy compatibility route during cutover |
| Bank transaction import | Native application service, exposed through the legacy compatibility route during cutover |
| External ZUS/eZUS verification | Not supported |
| Filing/JPK submission | Not supported |

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

There is no separate Ryczalt production feature flag found in the current code. Rollout is controlled
by route/controller profiles and the compatibility bridge: the Ryczalt Web controller is active while
the old Accounting MVC controller is limited to `legacy-accounting-compat`. Treat production cutover
and removal of the bridge as an explicit deployment decision, not an implicit test result.

## 9. First places to read

1. `modules/ryczalt/README.md` — staged migration and capability matrix.
2. `modules/ryczalt/src/main/java/.../application/RyczaltAccountingApi.java` — application boundary.
3. `RyczaltAccountingFacade` and `RyczaltPersistenceAdapter` — orchestration and storage.
4. `app/.../ryczalt/web/RyczaltAccountingRestController.java` — active REST contract.
5. `V01.026__ryczalt_persistence.sql` plus later `V01.027+` migrations — schema evolution.
6. `docs/development/testing.md` — test ownership and database-test rules.

For investment semantics, read `docs/domain/portfolio-accounting.md`; it describes the separate
brokerage accounting domain and should not be copied into Ryczalt tax calculations.
