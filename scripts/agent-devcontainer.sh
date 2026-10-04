#!/usr/bin/env bash
set -uo pipefail

usage() {
  echo "Usage: $0 <up|validate|down> <issue-id> [workspace] [--result-file path]" >&2
}

if [[ $# -lt 2 ]]; then
  usage
  exit 2
fi

action="$1"
issue_id="$2"
shift 2

result_file=""
workspace_arg=""

finish() {
  local status="$1"
  local exit_code="$2"
  local message="$3"

  if [[ -n "$result_file" ]]; then
    if ! python3 - "$result_file" "$status" "$exit_code" "$message" <<'PY'
import json
import sys
from pathlib import Path

result_path, status, exit_code, message = sys.argv[1:]
Path(result_path).write_text(
    json.dumps({
        "status": status,
        "exit_code": int(exit_code),
        "message": message,
    }),
    encoding="utf-8",
)
PY
    then
      echo "Could not write Dev Container result file: $result_file" >&2
      exit 1
    fi
  fi
  exit "$exit_code"
}

while (($#)); do
  case "$1" in
    --result-file)
      if [[ $# -lt 2 || -z "$2" ]]; then
        usage
        exit 2
      fi
      result_file="$2"
      shift 2
      ;;
    --workspace)
      if [[ $# -lt 2 || -z "$2" ]]; then
        usage
        exit 2
      fi
      workspace_arg="$2"
      shift 2
      ;;
    --*)
      echo "Unknown option: $1" >&2
      finish "environment_failure" 2 "Unknown option: $1"
      ;;
    *)
      if [[ -n "$workspace_arg" ]]; then
        echo "Unexpected argument: $1" >&2
        finish "environment_failure" 2 "Unexpected argument: $1"
      fi
      workspace_arg="$1"
      shift
      ;;
  esac
done

if [[ ! "$issue_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Invalid issue id: $issue_id" >&2
  finish "environment_failure" 2 "Invalid issue id: $issue_id"
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
default_workspace="$(cd -- "$script_dir/.." && pwd)"
workspace="${workspace_arg:-$default_workspace}"

if [[ ! -d "$workspace" ]]; then
  echo "Workspace does not exist: $workspace" >&2
  finish "environment_failure" 1 "Workspace does not exist: $workspace"
fi

workspace="$(cd -- "$workspace" && pwd)"

if [[ ! -f "$workspace/.devcontainer/devcontainer.json" ]]; then
  echo "Dev Container config not found in: $workspace" >&2
  finish "environment_failure" 1 "Dev Container config not found in: $workspace"
fi

export INVESTORY_WORKSPACE="$workspace"
export COMPOSE_PROJECT_NAME="investory-issue-${issue_id}"

case "$action" in
  up)
    if devcontainer up \
      --workspace-folder "$workspace" \
      --id-label "investory.issue=${issue_id}"; then
      finish "success" 0 "Dev Container started for issue $issue_id."
    else
      exit_code=$?
      finish "environment_failure" "$exit_code" \
        "Failed to start Dev Container for issue $issue_id."
    fi
    ;;
  validate)
    if docker compose \
      --project-name "$COMPOSE_PROJECT_NAME" \
      -f "$workspace/.devcontainer/compose.yml" \
      exec \
      -T \
      --user vscode \
      --workdir /workspaces/investory \
      app \
      bash scripts/agent-validate.sh; then
      finish "success" 0 "Validation succeeded for issue $issue_id."
    else
      exit_code=$?
      finish "project_validation_failure" "$exit_code" \
        "Validation failed for issue $issue_id."
    fi
    ;;
  down)
    if docker compose \
      --project-name "$COMPOSE_PROJECT_NAME" \
      --file "$workspace/.devcontainer/compose.yml" \
      down --volumes --remove-orphans; then
      finish "success" 0 "Dev Container stopped for issue $issue_id."
    else
      exit_code=$?
      finish "environment_failure" "$exit_code" \
        "Failed to stop Dev Container for issue $issue_id."
    fi
    ;;
  *)
    echo "Unknown action: $action" >&2
    usage
    finish "environment_failure" 2 "Unknown action: $action"
    ;;
esac
