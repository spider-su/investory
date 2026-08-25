# Production operation

This document defines the minimum operational contract for running Investory outside local development. Exact environment-variable names and defaults remain authoritative in `app/src/main/resources/application.yml` and `application-prod.yml`.

## Runtime shape

Investory is one Spring Boot application backed by one PostgreSQL database. Production schema creation and changes are owned by Flyway; Hibernate validates the schema and must not create or update it. Run with the `prod` Spring profile so database and security credentials are required explicitly and Thymeleaf caching is enabled.

The application health endpoint is `/actuator/health`. Use it for deployment readiness/liveness checks. A healthy process is not by itself proof that portfolio data is current or reconciled.

## Required production configuration

At minimum provide:

- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`;
- `APP_SECURITY_ADMIN_USERNAME`, `APP_SECURITY_ADMIN_PASSWORD`, `APP_SECURITY_USER_USERNAME`, and `APP_SECURITY_USER_PASSWORD`;
- `DEVELOP_MODE=false`;
- a stable `INVESTORY_INTEGRATION_MASTER_KEY` before encrypted integration secrets are stored;
- provider credentials only for integrations that are enabled.

Do not deploy with the non-production `change-me-*` security defaults. Keep secrets outside the repository and image.

## Startup and migrations

On startup Flyway applies pending migrations in order to the `investory` schema with out-of-order migration disabled. A migration failure is a deployment failure; do not bypass it by enabling Hibernate DDL generation or editing already-applied production migrations.

Before a schema-changing release, take a database backup appropriate to the PostgreSQL deployment. Restore procedures belong to the database platform/operator; Investory does not implement its own backup format.

## Scheduling and external data

Scheduled work is globally controlled by `SCHEDULING_ENABLED`. Market prices, FX, notifications, Telegram, and OpenAI analysis additionally depend on their own configuration and provider availability. Provider failures must not be treated as permission to invent or silently substitute financial facts.

TwelveData is the primary configured market quote integration; unsupported/non-US listings may require manual prices. FX uses the configured exchange-rate provider and the canonical rules in `../domain/fx-normalization.md`. Yahoo export is an adapter surface and participates in C7 reconciliation; it is not an accounting source of truth.

## Release verification

A production candidate should satisfy, in order:

1. unit and PostgreSQL integration tests;
2. the deterministic `GoldenRebuildIT` clean-database gate;
3. documentation-link validation and formatting checks;
4. the private full-archive rebuild/reconciliation release check when applicable;
5. application reconciliation with no unexplained required-check failures;
6. a production-like smoke pass covering startup, login, dashboard, import/refresh, long-term assets, retirement planning/Analysis, restart, and read-back.

The verification contract and QUICK/GOLDEN/ARCHIVE model are defined in `../quality/reconciliation.md`. A green health endpoint or successful UI load does not replace financial verification.

## Restart and recovery expectations

Canonical accounting and planning state is persisted in PostgreSQL. Restarting the application must not change financial results solely because in-memory process state was lost. If a feature intentionally has transient state, it must not be required to reconstruct accounting or reviewed planning facts.

After an unexpected restart, verify database connectivity, Flyway status, `/actuator/health`, recent import/market/FX freshness, and reconciliation status before relying on newly displayed values.

## Production boundaries

The current deployment is designed as a personal/single-owner system, not as a securely isolated multi-tenant service. Before exposing it to multiple independent users, implement the owner/data-scoping work tracked in `../../ROADMAP.md` and review `../architecture/security.md`.
