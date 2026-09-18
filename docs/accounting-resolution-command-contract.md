# Accounting resolution command contract

## V1 public command surface

There is no public mobile issue-resolution command in the current backend.
This is intentional: the backend emits no `CHOICE` or `MATCH` resolution, and
no existing service accepts a finite backend-issued answer for an accounting
issue.

The versioned mobile accounting API is read-oriented:

- `GET /api/v1/profiles/{profileId}/accounting/months/{month}` is the
  authoritative post-action refresh source.
- `GET` and `PUT /api/v1/profiles/{profileId}/accounting/auto-approval` are
  existing profile settings APIs, not issue commands.

## Existing authoritative mutations

The unversioned accounting workspace has durable operations that must not be
presented as a generic mobile issue resolver:

| Operation | Existing endpoint | Input identity | Safeguards | Why it is not an issue command |
| --- | --- | --- | --- | --- |
| Review source document | `GET /api/profiles/{profileId}/accounting/documents/review` | `sourceReference` | Reads only a current `REVIEW_REQUIRED` KSeF source in the profile | It returns a complete review candidate, not a resolution option |
| Save reviewed document | `POST /api/profiles/{profileId}/accounting/documents` | Full `ReviewedDocument` | Profile write authorization, full field validation, duplicate detection, transactional staging and reconciliation | Mobile would need to supply accounting facts, not a canonical action plus option |
| Auto-approval settings | `PUT /api/v1/profiles/{profileId}/accounting/auto-approval` | Profile settings | Profile write authorization | It changes automation settings, not an active issue |

## Authorization and errors

All existing mutations verify `AuthorizationService.canWrite(profileId,
authentication)`. Reads verify `canRead`. The shared REST error handler maps:

- malformed or invalid input to `400`;
- invalid authentication through Spring Security to `401`;
- unauthorized profile access to `403`;
- missing resources to `404` where the endpoint has a resource lookup;
- lifecycle conflicts to `409`.

There is deliberately no invented `422` or issue-specific stale response while
no issue command exists. A future command must load the current issue from the
profile/month, reject inactive or changed issues with `409`, and reject a
non-offered option with `422`.

## Staleness, idempotency, transactions, and auditability

`saveReviewedResult` naturally recognizes a duplicate canonical document, while
`saveReviewed` is transactional for review persistence and directly required
staging reconciliation. It is not sufficient proof of idempotency for a future
generic resolution command.

No issue-resolution audit event exists because no such command exists. A future
command must persist an auditable decision identity, actor, profile, source or
issue identity, timestamp, and prior/current relevant state in the same
transaction as the domain mutation.

## Future command admission rule

Before publishing `CHOICE` or `MATCH`, the backend must already have all of:

1. a stable public command identifier;
2. canonical backend-issued option values (never localized labels);
3. a profile-scoped authoritative service that validates current issue state;
4. transaction-safe persistence and duplicate/concurrency protection; and
5. tests for authorization, stale issue, invalid command/option, retry, and
   refreshed accounting-month state.

Until then, unknown commands remain display-only and no mutating production
smoke call is permitted.
