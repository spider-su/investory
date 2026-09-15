# Accounting user API

The user-facing accounting boundary is profile-scoped under `/api/profiles/{profileId}/accounting`.
`GET /months/{month}/overview` is the aggregate read for the
`/profiles/{profileId}/accounting` page; issues, documents, bank transactions, payments, filings, and
reconciliation have separate detail reads.

The versioned read-only mobile boundary is `/api/v1/profiles/{profileId}/accounting`:
`GET /months/{month}` returns the operational month overview and
`GET /months/{month}/documents` returns profile/month-scoped sale and purchase documents. These
responses are explicit REST DTOs; the internal `ReferenceSummary` is intentionally omitted. The
mobile boundary reuses the same profile authorization and `AccountingUserFacade` as the existing
web/API routes. `Summary.totalObligations` is the domain-calculated VAT + ryczałt + ZUS obligation before recorded payments. It is available even when filing is not ready. `PaymentSummary.totalOutstanding` is narrower: it sums unpaid amounts from issued payment instructions, after applying recorded payments; when instructions cannot yet be issued (for example an unconfirmed month), it is zero. These fields must not be conflated.

Document `sourceType`/`sourceTypeLabel` identify acquisition evidence (`KSEF` or `UPLOAD`); they do not classify a PL versus EU invoice. `categoryLabel` is the backend presentation mapping for purchase categories. `importStatus` is populated only when source evidence identifies the import; `reviewStatus` and `paymentStatus` remain null where the domain has no authoritative value. The legacy `status` field is retained for compatibility and must not be interpreted as approval.


Payment rows in the mobile overview are issued obligations. Each row carries obligation type, expected amount, paid amount, outstanding amount, due date, and domain payment status. The headline obligation total remains independent of these rows and can exist before payment instructions are issued.

Writes are explicit action endpoints (`confirm`, `file`, `settle`, `lock`, `reopen`). Document recognition returns a review candidate; `POST /documents` is the user-reviewed persistence step. Upload and bank multipart requests are preserved as source evidence before processing.

The API returns stable view DTOs. Accounting calculation snapshots, JDBC rows, and repositories are internal implementation details.

Accounting is currently a single-profile POC: only `profileId=1` is supported. The profile guard and
rejection of other profile IDs are intentional POC boundaries, not generic production multi-profile
support. Multi-profile Accounting is roadmap work. Reads require authentication; mutations require
an administrator or the profile owner. Browser mutations under
`/profiles/{profileId}/accounting/**` use CSRF protection, while JSON and multipart API mutations
under `/api/**` use the explicit API security boundary. Former `/poc/accounting` mutating routes are
denied.

The acquisition lifecycle is source evidence -> staging -> reconciliation -> explicit promotion ->
canonical facts. Only `NEW` staging rows can be promoted; `MATCH`, `MISMATCH`, and `AMBIGUOUS` rows
remain blocked. JPK generation is local, XSD-validated projection output and does not submit to the
tax authority. Repeated generation with the same calculation fingerprint returns the stored artifact.

Bank CSV uploads preserve source evidence before parsing and accept the canonical columns
`booking_date`, `related_period`, `reference`, `counterparty`, `currency`, `amount`, and `note` in
comma- or semicolon-delimited CSV. UTF-8 BOM, quoted fields, and CRLF line endings are supported.
# Accounting API scope

All Accounting endpoints are under `/api/profiles/{profileId}/accounting`.
The requested profile is checked against the existing profile identity and
HTTP authorization before application work. Profile-owned source evidence,
staging rows, canonical facts, lifecycle state, filings, and confirmations are
isolated by that profile. Browser mutations use CSRF protection; API reads and
writes do not use a loopback HTTP hop from the server-rendered UI.

Undated source evidence remains outside month-specific queues until explicitly
assigned. `NEW` staging rows alone can be promoted; `MISMATCH` and `AMBIGUOUS`
rows remain blocked.
