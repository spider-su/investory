#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <up|validate|down> <issue-id> [workspace]" >&2
  exit 2
}

[[ $# -ge 2 ]] || usage

action="$1"
issue_id="$2"
workspace="${3:-$(pwd)}"

if [[ ! "$issue_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Invalid issue id: $issue_id" >&2
  exit 2
fi

workspace="$(cd "$workspace" && pwd)"
export COMPOSE_PROJECT_NAME="investory-issue-${issue_id}"

case "$action" in
  up)
    devcontainer up \
      --workspace-folder "$workspace" \
      --id-label "investory.issue=${issue_id}"
    ;;
  validate)
    devcontainer exec \
      --workspace-folder "$workspace" \
      bash scripts/agent-validate.sh
    ;;
  down)
    docker compose \
      --project-name "$COMPOSE_PROJECT_NAME" \
      --file "$workspace/.devcontainer/compose.yml" \
      down --volumes --remove-orphans
    ;;
  *)
    usage
    ;;
esac
