# AGENTS.md

Canonical entry point for coding-agent guidance. Keep this file small.
Load only the scoped guidance needed for the current task.

## Scoped Guidance

- Backend/Java work: read `AGENTS.backend.md` when the task touches `src/main/java`,
  `src/test/java`, Maven/build configuration, HTTP/security, imports, integrations, schedulers,
  or server-side portfolio logic.
- SQL/data work: read `AGENTS.sql.md` when the task touches Flyway migrations, schema, views,
  projections, reconciliation SQL, persistence invariants, or live database investigation.
- Frontend work: read `AGENTS.frontend.md` when the task touches `src/main/resources/static`,
  `src/main/resources/templates`, dashboard rendering, CSS, JavaScript, or browser behavior.
- Cross-cutting tasks may read more than one scoped file, but only when the task actually crosses
  those boundaries.
- Do not preload unrelated scoped guidance.

## Communication Style

- Use brief caveman-style English in user-facing commentary and final responses.
- Keep code, commands, paths, API names, class names, and technical identifiers exact.
- Prefer precision over simplified phrasing when the two conflict.

## Stack Discipline

- Keep the existing stack unless the user explicitly requests a change or the current stack cannot
  satisfy a hard technical requirement: Spring Boot, Java, Maven, PostgreSQL, Flyway, JPA,
  Thymeleaf, and the existing frontend/runtime choices.

## Execution Autonomy

- Routine repository-local edits and non-destructive build, test, lint, and verification commands
  are pre-approved.
- Do not stop for permission unless the action is destructive, external, or requires platform-level
  escalation.

## Change Discipline

- Inspect the smallest relevant scope first; expand only when evidence requires it.
- Make the smallest change that solves the task. Avoid unrelated refactoring.
- Do not reintroduce removed APIs, tables, views, or components as shortcuts.
- Update the matching scoped guidance when a durable invariant changes.
- Keep `ROADMAP.md` future-facing; completed work belongs in history/release notes.
