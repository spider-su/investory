# Application module

`app` is the executable Spring Boot composition layer. It owns application configuration, security,
Flyway migrations, user/profile web adapters, server-rendered UI composition, and native HTTP
integration between the business modules and the running application.

Business rules remain in `modules/*`; `app` wires their public APIs and owns cross-cutting runtime
concerns. Database migrations and environment-specific configuration belong here. Controllers in
this module are adapters and must not bypass module APIs to reach business persistence.

The local development profile and test configuration are for local/test operation only. Production
security and user identity must remain explicit and must not depend on development conveniences.

Run locally from the repository root with:

```text
./mvnw -pl app -am spring-boot:run
```

See [`docs/architecture/overview.md`](../docs/architecture/overview.md),
[`docs/architecture/security.md`](../docs/architecture/security.md), and
[`docs/development/dev-container.md`](../docs/development/dev-container.md).
