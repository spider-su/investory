# Docs

Focused supporting documents live here. `AGENTS.md` remains the canonical source for current
architecture, runtime behavior, database shape, API surface, and developer workflow.

## Root documentation

- [`../README.md`](../README.md): product scope, calculations, limitations, and exact setup.
- [`../AGENTS.md`](../AGENTS.md): canonical engineering guide.
- [`../ROADMAP.md`](../ROADMAP.md): future work and current priorities only.
- [`../CHANGELOG.md`](../CHANGELOG.md): completed work and documentation history.

## Supporting documents

- [`DEV_CONTAINER.md`](DEV_CONTAINER.md): reproducible Java, Maven, PostgreSQL, and Docker development
  environment.
- [`ghostfolio-compatibility-report.md`](ghostfolio-compatibility-report.md): endpoint-by-endpoint
  compatibility matrix for the Ghostfolio frontend surface.
- [`pipeline-testing-plan.md`](pipeline-testing-plan.md): staged import-to-UI validation plan,
  reconciliation tooling, implemented checkpoints, and known gaps.
- [`test-data-refactor-notes.md`](test-data-refactor-notes.md): test-fixture refactor notes for shared
  portfolio scenario data.

Use this folder for targeted design notes, compatibility reports, migration notes, and focused plans
that would add too much detail to the public README. Do not duplicate the project overview,
architecture source of truth, agent instructions, roadmap, or changelog here.
