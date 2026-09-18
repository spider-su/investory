# Mobile accounting resolution gap audit

## Scope and evidence

Audited on `develop` from `de7405e1382f6ac454f057501b66804b60091444`.

The mobile month response is produced by `AccountingUserFacade.issue(...)` and
`AccountingUserFacade.resolution(...)`, then mapped by
`AccountingMobileResponse.Resolution.from(...)`.

`Resolution.Choice` and `Resolution.Match` are response-model variants only.
No production code creates either variant. The only emitted resolutions are:

| Issue code family | Issue kind | Resolution type | Command/options | Source reference | Existing authoritative operation | Persisted action | Mobile V1 action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `MISSING_PAYMENT_CONFIGURATION`, `MISSING_TAXPAYER_CONFIGURATION`, `MISSING_EFFECTIVE_TAX_PROFILE`, `MISSING_ZUS_RULE_INPUT` | `SETUP` | `SETUP` | No command or options; web path `/profiles/{profileId}/accounting` | None | Existing mobile `GET`/`PUT` auto-approval settings only | Auto-approval profile settings | Navigation/display only; no secret or admin setup |
| `MISSING_VAT_CLASSIFICATION`, `MISSING_EXPLICIT_VAT_RATE`, `UNSUPPORTED_VAT_RATE`, `MISSING_COUNTERPARTY_IDENTIFIER`, `MISSING_JPK_EVIDENCE_CLASSIFICATION`, `SOURCE_REVIEW_REQUIRED` | `NEEDS_ANSWER` | `NONE` | No command or options | Usually the source reference | `GET /api/profiles/{profileId}/accounting/documents/review` plus `POST /api/profiles/{profileId}/accounting/documents` | Full reviewed document stages source evidence and reconciles it | Still missing: this is a complete document-review form, not a finite issue-option command |
| Any remaining blocking issue, including FX, source failures, and filing readiness | `BLOCKED` | `NONE` | No command or options | Optional | None proven safe for user action | None | Display only |
| Informational issue | `INFO` | `NONE` | No command or options | Optional | None | None | Display only |

## Existing domain capabilities

- Document review is durable and transactional in `AccountingUserFacade.saveReviewed`.
  It validates the complete review payload, stages source evidence, and runs staging
  reconciliation. `saveReviewedResult` recognizes a canonical duplicate before
  staging again.
- Bank matching is automatic in `AccountingStagingReconciliationService`; it has
  no persisted user-selected candidate operation and exports no candidate list.
- Classification is supplied as part of a full reviewed document. There is no
  standalone classification-choice service or canonical finite option set for an
  issue command.
- Mobile auto-approval settings are already profile-scoped at
  `GET`/`PUT /api/v1/profiles/{profileId}/accounting/auto-approval` and are not
  an issue-resolution command.

## Resolution conclusion

No generic issue resolver is safe to publish in V1. A mobile client must not
reconstruct a document review from issue text, nor submit arbitrary bank or
document identifiers for a match. Therefore:

- `NONE` remains non-executable.
- `SETUP` remains navigation-only. It does not grant access to KSeF, bank, or
  admin credentials.
- `CHOICE` is **STILL MISSING** because no existing authoritative finite-choice
  operation is emitted.
- `MATCH` is **DEFERRED** because no existing candidate-selection persistence
  operation exists.
- Document review is **DEFERRED** for a dedicated mobile review contract. Reusing
  the existing full web payload as a generic issue command would violate the
  stable command and option identity requirements.

The refreshed month endpoint remains authoritative after any existing mobile
settings update or future resolution command.
