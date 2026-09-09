# AI UI QA

This is a lightweight contract for a Codex agent with browser/Playwright MCP access to explore a
deployed Investory environment. The agent supplies the execution; these files supply scope,
safety, evidence, and reporting conventions.

The canonical page-specific scenario organization is now [`qa/ui/`](../ui/). The `qa/ai/missions/`
files and reports remain as Phase 3 provenance and should not be duplicated into generic skills.

Start with [`instructions.md`](instructions.md), then copy
[`missions/exploratory-read-only.md`](missions/exploratory-read-only.md) for a focused mission.
Store the resulting report under `reports/` and reference screenshots, traces, console output, and
network evidence under `reports/evidence/` when they are retained.

Canonical behavior and expected financial facts remain in the repository sources. In particular,
use [`docs/development/testing.md`](../../docs/development/testing.md), the relevant
[`docs/quality/`](../../docs/quality/) plan, and the HappyInvestor fixture documentation and fact
classes. Do not copy those facts into a mission.
