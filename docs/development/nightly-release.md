# Nightly release boundary

[Issue #183](https://github.com/spider-su/investory/issues/183) uses a release coordinator in
[private ops-autopilot](https://github.com/spider-su/ops-autopilot), with its operator runbook at
`docs/operations/investory-nightly-release.md`. The owner approved private placement on 2026-10-18.
The previous public preflight workflow is superseded; there is one release execution flow.

This public repository runs Tests and Docker Build and Publish only. Successful image scanning and
SBOM upload are followed by the `investory-image-release-<attempt>` artifact: exact source SHA, image repository,
build digest and Actions run/attempt. The private coordinator must validate that receipt against the
successful exact-main run, registry metadata and deployed pod image IDs. A matching mutable latest
tag or a green preflight alone is not deployment evidence.

No Kubernetes credentials, privileged release runner, personal gh token or production webhook belong
in this public release path. No new automatic rollback, production restart or active nightly schedule
is introduced here. Private activation still requires trusted identity, enforceable environment
approval, scoped access, read-only dry-run evidence and explicit operator approval.

For recovery use the private runbook, retained release receipt and the existing approved GitOps
procedure. Inspect uncertain operations before retrying. A mutable latest tag is not a rollback target.
