# Accounting user API

The user-facing accounting boundary is profile-scoped under `/api/profiles/{profileId}/accounting`.
`GET /months/{month}/overview` is the aggregate read for the `/accounting` page; issues, documents, bank transactions, payments, filings, and reconciliation have separate detail reads.

Writes are explicit action endpoints (`confirm`, `file`, `settle`, `lock`, `reopen`). Document recognition returns a review candidate; `POST /documents` is the user-reviewed persistence step. Upload and bank multipart requests are preserved as source evidence before processing.

The API returns stable view DTOs. Accounting calculation snapshots, JDBC rows, and repositories are internal implementation details.
