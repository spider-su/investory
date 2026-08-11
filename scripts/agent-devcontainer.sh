#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <up|validate|down> <issue-id> [workspace]" >&2
  exit 2
}

[[ $# -ge 2 ]] || usage

action="$1"
issue_id="$2"

if [[ ! "$issue_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Invalid issue id: $issue_id" >&2
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
default_workspace="$(cd -- "$script_dir/.." && pwd)"
workspace="${3:-$default_workspace}"

if [[ ! -d "$workspace" ]]; then
  echo "Workspace does not exist: $workspace" >&2
  exit 1
fi

workspace="$(cd -- "$workspace" && pwd)"

if [[ ! -f "$workspace/.devcontainer/devcontainer.json" ]]; then
  echo "Dev Container config not found in: $workspace" >&2
  exit 1
fi

export INVESTORY_WORKSPACE="$workspace"
export COMPOSE_PROJECT_NAME="investory-issue-${issue_id}"

case "$action" in
  up)
    devcontainer up \
      --workspace-folder "$workspace" \
      --id-label "investory.issue=${issue_id}"
    ;;
  validate)
    docker compose \
      --project-name "$COMPOSE_PROJECT_NAME" \
      -f "$workspace/.devcontainer/compose.yml" \
      exec \
      -T \
      --user vscode \
      --workdir /workspaces/investory \
      app \
      bash scripts/agent-validate.sh
    ;;
  down)
    docker compose \
      --project-name "$COMPOSE_PROJECT_NAME" \
      --file "$workspace/.devcontainer/compose.yml" \
      down --volumes --remove-orphans
    ;;
  *)
    echo "Unknown action: $action" >&2
    echo "Expected: up, validate, or down" >&2
    exit 2
    ;;
esac
