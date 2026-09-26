# JDG onboarding MVP inventory

Status: inspection completed before implementation on branch `codex/jdg-onboarding-mvp`.

## Existing implementation

### Backend

- Authentication exposes `/api/v1/auth/login`, `/api/v1/auth/me`, logout and invitation acceptance. Login resolves an existing profile; no onboarding state or profile-creation endpoint was found.
- The Ryczalt module already owns authoritative accounting periods, calculations, obligations, payment history, invoice import and manual payment actions.
- `ryczalt_profile` is already persisted and contains taxpayer identity, NIP, tax office code, VAT/ryczalt/ZUS payment accounts, UoP flag and owner identity fields. A reader and export-oriented `RyczaltProfile` record already exist.
- ZUS calculation inputs and 2026 rules already exist; the current persisted profile exposes only a limited `has_uop` fact to the reader.
- KSeF authorization/synchronization already exists under the Ryczalt API and is exposed through `/api/profiles/{profileId}/accounting/ksef`. It is an integration operation, not an onboarding state.
- No GUS, CEIDG or VAT White List company-lookup adapter was found in the inspected application/module paths.
- Existing profile data is migrated by `V01.022__ryczalt_profile.sql`; the migration preserves the legacy `accounting_poc_profile` rows.

### Mobile

- `AuthContext` signs in, accepts invitations and starts isolated demo mode, then loads the existing accounting profile ID and opens `AppNavigator` directly.
- No onboarding navigator, company lookup client, accounting-configuration confirmation or ZUS onboarding screen exists.
- `MoreScreen` contains Settings, Counterparties, Notifications, reports and sign-out. No KSeF settings screen or onboarding integration step was found.
- `MockAccountingRepository` provides deterministic demo accounting data and does not call external services.
- The existing design system, localization, light/dark theme and API repository should be reused.

## Required implementation changes

1. Add a backend-owned onboarding/profile contract with explicit states, idempotent retry behavior and migration-safe defaults for existing profiles.
2. Add a validated NIP lookup boundary. Provider adapters must be explicit; unavailable providers must not fabricate live data.
3. Add supported-configuration validation for JDG, ryczałt 12%, monthly PIT and active monthly VAT. Keep unsupported configurations representable but not activatable.
4. Add only the ZUS facts required by the existing calculation engine, including an explicit unsupported state when those facts cannot be calculated safely.
5. Add onboarding endpoints for company lookup, company confirmation, supported configuration confirmation, ZUS facts and completion/KSeF skip. Keep KSeF credentials outside onboarding state.
6. Add mobile three-step onboarding after authentication and before Home, with a separate optional KSeF step and demo path.
7. Add Settings views for confirmed company, accounting, ZUS and KSeF state, reusing existing More/Settings surfaces.
8. Add backend, mobile, demo, accessibility, localization and migration tests from the brief.

## Known boundary

The current working trees contain unrelated uncommitted changes. They were preserved; this branch was created from those working trees. No existing files were reset or discarded.

## Implementation boundary

The first implementation slice adds `ryczalt_onboarding` persistence with a completed backfill for existing profiles, authenticated company/configuration/ZUS/completion endpoints, NIP checksum validation, and the official VAT White List lookup adapter. Provider failures remain unavailable states; live mode does not synthesize company data.

The current backend has no CEIDG/GUS credentialed adapter and no separate profile-creation flow. The MVP therefore uses VAT White List for the available live lookup, requires explicit JDG confirmation, and assumes the authenticated profile already exists (as in the current invitation flow). Stronger JDG verification and self-service profile provisioning remain deployment follow-ups.
