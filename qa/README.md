# QA workspace

This directory contains repeatable QA mission definitions and their reports. It is not a second
test framework and it does not replace executable tests under `app/src/test`.

- [`ai/`](ai/): instructions and missions for Codex-led exploratory browser QA.
- [`ui/`](ui/): page-focused scenarios consumed by the generic UI skills.

Keep credentials, cookies, personal data, and large generated browser artifacts out of Git. Reports
may link to local or CI-retained evidence paths when those paths are safe to share.
