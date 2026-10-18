# Nightly release preflight

The workflow is manual-only and has no active schedule. The proposed nightly time is 01:30 UTC (02:30/03:30 Europe/Warsaw depending on daylight saving), pending operator approval. `workflow_dispatch` performs a read-only preflight; no input enables production actions. It serializes runs and skips when `develop` has no commits outside `main`, including the normal post-release merge-commit case where their SHA values differ. For a candidate, it reads all jobs from one exact `develop` SHA push-CI run and its specific attempt, requiring every name in `.github/required-checks.txt` and fails closed if results are missing, pending, unsuccessful, or refer to another SHA.

## Current boundary

This workflow is intentionally preflight-only. It does not promote branches, publish images, or access Kubernetes. The repository is public, while the known rollout access is operator-local; that is not authorization to expose host credentials to Actions. Do not add kubeconfig, personal tokens, credentials, or production environment access to this workflow. Before enabling promotion/deployment, an operator must verify trusted actor/default-branch/environment restrictions, protected merge requirements, exact-SHA full regression, Docker image provenance/digest, Argo ownership, rollout image identity, and read-only smoke endpoints. Until those gates are verified and implemented through an approved mechanism, a green preflight is not a release.

## Inspecting a run

Open **Actions → Nightly release preflight**. Compare the logged `main` and `develop` SHAs. A no-change run is a successful skip. For a candidate, inspect the exact-SHA check results and the job summary; a preflight result does not mean production was deployed. Missing or unsuccessful check evidence is a failure, not a waiver.

## Manual recovery

No automatic rollback is performed. For a failed future rollout, stop further release attempts, inspect the Deployment, pod events, readiness, and image IDs without printing credentials, and use the authorized operator/GitOps procedure to restore the previously recorded known-good image digest (or revert the approved release commit through the protected PR flow). Wait for rollout readiness and repeat read-only health/API smoke checks. Do not use a mutable `latest` tag as the recovery reference; record the exact digest before rollout. Escalate unresolved database migration or data-integrity issues before retrying.
