# Test-support module

`test-support` provides reusable test-only fixtures, PostgreSQL/Testcontainers setup, snapshot
schema resources, and deterministic HappyInvestor source facts. It is consumed with Maven test
scope and is not production behavior.

## Rules

- Keep fixtures deterministic and synthetic; do not add personal or provider-sensitive data.
- Keep snapshot resources aligned with the fast database-test contract.
- Use the migration path when testing Flyway changes; use the snapshot path for fast module tests.
- Test-support may depend on public module APIs, but production modules must not depend on it.

See [`docs/development/testing.md`](../docs/development/testing.md) for test selection and database
test strategy.
