# Security architecture

This document describes the current application security boundary and the constraints that matter for deployment. Exact route mappings and configuration keys remain authoritative in `app/src/main/java/com/smartbox/investory/config/SecurityConfig.java` and application configuration.

## Authentication and roles

Investory currently uses stateless HTTP Basic authentication with an in-memory user store and BCrypt password encoding. There are two configured principals:

- `ADMIN` has roles `ADMIN` and `USER`;
- `USER` has role `USER`.

`POST`, `PUT`, and `DELETE` requests require `ADMIN`. `GET` requests require authentication by default and may be made public only when `app.security.read-authentication-required=false` is explicitly configured for a trusted environment.

The landing/error/static assets and `/actuator/health` are public. Exact matcher behavior should be read from `SecurityConfig` when changing routes.

## Session and CSRF model

The application is stateless and disables form login. CSRF protection is currently disabled globally. This matches the current HTTP Basic/API-oriented security model but means UI write routes do not receive browser-CSRF protection.

Do not describe CSRF as implemented until the roadmap item for UI POST protection is completed and tested.

## Secrets

Production must supply explicit admin/user credentials through configuration and must not use the development `change-me-*` defaults. Integration credentials and provider tokens must not be committed to source control.

`INVESTORY_INTEGRATION_MASTER_KEY` protects persisted integration secrets. Treat changing or losing that key as an operational security event because encrypted integration configuration may become unreadable.

## Data isolation

Authentication is not equivalent to tenant isolation. The current application is a personal/single-owner deployment: portfolio data is not scoped by an authenticated owner identity across all financial tables.

Do not expose one instance to mutually untrusted users until per-user data scoping is implemented and verified. The corresponding work remains in `../../ROADMAP.md`.

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
