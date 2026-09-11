# Security architecture

This document describes the current application security boundary and the constraints that matter for deployment. Exact route mappings and configuration keys remain authoritative in `app/src/main/java/com/smartbox/investory/config/SecurityConfig.java` and application configuration.

## Authentication and roles

Investory uses stateless HTTP Basic authentication and BCrypt password encoding. Runtime users are loaded from `investory.app_users`; configured fallback principals remain available for local development.

- `ADMIN` is global and can access every profile and administrative operation.
- `PROFILE_OWNER` is scoped to a profile and can read and change that profile.
- `PROFILE_USER` is scoped to a profile and is read-only.

The capability matrix is: `ADMIN` = read/write/manage integrations/administer; `PROFILE_OWNER` = profile read/write; `PROFILE_USER` = profile read only.

The scope is stored in `investory.profile_memberships` (`user_id`, `profile_id`, `role`). Existing `portfolios.user_id` owners are migrated to `OWNER`, preserving their previous write access. A role without a profile membership grants no profile access.

Backend authorization is authoritative: profile reads require membership or `ADMIN`; profile writes, imports, refresh/rebuild operations, and other state-changing requests require `OWNER` or `ADMIN`. Integration configuration and `/api/v1/admin/**` operations require `ADMIN`. Authenticated denials return `403`; missing authentication returns `401`.

The global investment maintenance endpoints (`/api/v1/investment/maintenance/**`) are `ADMIN`-only because they do not carry a profile ID and can rebuild shared reporting state. Profile-scoped REST operations carry the profile ID in `/api/v1/portfolios/{portfolioId}/...`; it is not accepted as a query parameter. The dashboard query keeps its internal dashboard query model, but the HTTP path is the authority for the portfolio scope.

The landing/error/static assets and `/actuator/health` are public. Exact matcher behavior should be read from `SecurityConfig` when changing routes.

## Session and CSRF model

The application is stateless and disables form login. CSRF protection is currently disabled globally. This matches the current HTTP Basic/API-oriented security model but means UI write routes do not receive browser-CSRF protection.

Do not describe CSRF as implemented until the roadmap item for UI POST protection is completed and tested.

## Secrets

Production must supply explicit admin/user credentials through configuration and must not use the development `change-me-*` defaults. Integration credentials and provider tokens must not be committed to source control.

`INVESTORY_INTEGRATION_MASTER_KEY` protects persisted integration secrets. Treat changing or losing that key as an operational security event because encrypted integration configuration may become unreadable.

## Data isolation

Profile isolation is enforced at the MVC boundary before profile handlers run. Controllers identify the profile from `/portfolios/{portfolioId}/...`; the membership query is against that exact ID. The frontend receives capability flags (`canEdit`, `canImport`, `canManageProfile`, `canManageIntegrations`) for UX only and cannot replace backend checks.

Internal service calls must still pass the intended profile ID; HTTP callers cannot bypass the boundary by changing only a URL ID.

## Exposure rules

Recommended production posture:

- keep read authentication enabled;
- terminate TLS at the deployment/reverse-proxy boundary;
- expose only the application routes intentionally required by the operator;
- keep database and provider credentials out of logs and repository files;
- do not expose PostgreSQL directly to the public network;
- use strong unique passwords for both configured principals;
- treat administrative import/refresh/write routes as privileged operations.

## Change rules

A security change must update this document when it changes authentication type, role semantics, public-route policy, session/CSRF behavior, secret handling, or data-isolation assumptions. Tests should cover the security contract for representative read and write routes.
