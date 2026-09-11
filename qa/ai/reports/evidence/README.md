# Evidence

This directory is the conventional location for mission artifacts that are intentionally retained:
screenshots, traces, sanitized HTML, and console/network excerpts. Name files with the mission and
timestamp, for example `dashboard-read-only-2026-09-09T120000Z.png`.

Do not commit credentials, cookies, authorization headers, raw API payloads containing personal data,
or broad browser profiles. Large generated artifacts should remain in the browser runner's ignored
output directory and be linked from the report only when the artifact store is controlled.
