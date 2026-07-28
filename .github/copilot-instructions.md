# GitHub Copilot Instructions

Use `AGENTS.md` as the canonical project guide.

## Copilot-Specific Overlay

- Generate Java in the repository's existing Google Java Format style with two-space
  indentation and clean imports.
- Prefer small completions consistent with nearby code; inspect existing services, repositories,
  DTOs, and tests before introducing a new abstraction.
- Do not guess dependency versions or framework APIs. Use `pom.xml` and existing code as the
  source of truth.
- For PostgreSQL inspection from the coding environment, prefer the JDBC driver with `jshell` or
  small Java snippets. Do not assume `psql` is installed or try it first.
- Schema changes belong in versioned Flyway SQL under
  `src/main/resources/sql/migration`, never as Java-side DDL.
- Do not duplicate project architecture or version facts here; update `AGENTS.md` instead.
