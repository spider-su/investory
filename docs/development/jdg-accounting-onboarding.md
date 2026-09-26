# JDG accounting onboarding MVP

The backend onboarding contract is profile-scoped and reuses the existing Investory authorization
and native Ryczalt profile. It does not create a second accounting engine or require KSeF.

## Endpoints

```text
GET  /api/profiles/{profileId}/onboarding
POST /api/profiles/{profileId}/onboarding/company/lookup
POST /api/profiles/{profileId}/onboarding/company/confirm
GET  /api/profiles/{profileId}/onboarding/configuration
POST /api/profiles/{profileId}/onboarding/accounting/confirm
GET  /api/profiles/{profileId}/onboarding/zus
POST /api/profiles/{profileId}/onboarding/zus
GET  /api/profiles/{profileId}/onboarding/ksef
POST /api/profiles/{profileId}/onboarding/ksef/skip
POST /api/profiles/{profileId}/onboarding/complete
GET  /api/profiles/{profileId}/accounting/readiness?month=YYYY-MM
POST /api/profiles/{profileId}/accounting/periods/{YYYY-MM}/activity-confirmation
```

All operations require authentication. Reads require profile membership; writes require the profile
owner or administrator. The profile id is always checked by `AuthorizationService`.

The only activatable configuration is JDG, RYCZALT at 12%, monthly PIT, active monthly VAT, and a
non-future accounting start date. ZUS onboarding stores JDG/UoP, sickness-insurance, health-method,
and full-social inputs used by the existing calculation domain. Unsupported choices return a stable
error message code such as `accounting_configuration_not_supported` or `zus_configuration_invalid`.

Company lookup uses server-side official-provider adapters. GUS BIR is used for identity and address
when `GUS_BIR_API_KEY` is configured; the VAT White List adapter supplies VAT status and available
identity fields. Provider failures are returned as partial lookup warnings, not as a false
`company_not_found` result. Manual confirmation is explicitly marked by its `source` value.

Migration `V01.023__ryczalt_onboarding.sql` adds the profile-scoped onboarding draft. Existing
profiles are initialized as completed compatibility records with the supported configuration and
remain usable without a new onboarding prompt. KSeF is independent: `/ksef/skip` stores `SKIPPED`,
and completion never requires KSeF credentials.

## Home readiness

The readiness endpoint is the backend-owned source for the mobile Home context. It reports company,
accounting, ZUS, KSeF, selected-period, and individual calculation states. `NO_INVOICES` means only
that Investory has no invoices for the selected period; it never means zero revenue. KSeF may be
`OPTIONAL` and does not block Home, manual invoice entry, invoice review, or supported calculations.

The `no-activity` endpoint stores an authenticated, profile-and-period-scoped `NO_REVENUE`
confirmation with timestamp, actor, and invoice/transaction snapshot. A later invoice makes the
confirmation stale in the readiness assessment. The migration is
`V01.024__ryczalt_period_activity_confirmation.sql`.

Provider settings:

```text
GUS_BIR_API_KEY
GUS_BIR_URL
VAT_WHITE_LIST_URL
```

The provider clients have no credentials in source or cached lookup payloads.
