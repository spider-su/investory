#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
helper="$repo_root/scripts/agent-devcontainer.sh"
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT

workspace="$temporary/workspace"
fake_bin="$temporary/bin"
mkdir -p "$workspace/.devcontainer" "$fake_bin"
touch "$workspace/.devcontainer/devcontainer.json"
touch "$workspace/.devcontainer/compose.yml"

cat > "$fake_bin/devcontainer" <<'SH'
#!/usr/bin/env bash
printf 'devcontainer %s\n' "$*" >> "$CALL_LOG"
exit "${FAKE_DEVCONTAINER_EXIT_CODE:-0}"
SH

cat > "$fake_bin/docker" <<'SH'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$CALL_LOG"
exit "${FAKE_DOCKER_EXIT_CODE:-0}"
SH

chmod +x "$fake_bin/devcontainer" "$fake_bin/docker"
export CALL_LOG="$temporary/calls.log"
export PATH="$fake_bin:$PATH"

assert_result() {
  python3 - "$1" "$2" "$3" <<'PY'
import json
import sys

result_path, expected_status, expected_exit = sys.argv[1:]
with open(result_path, encoding="utf-8") as result_file:
    payload = json.load(result_file)
assert payload["status"] == expected_status, payload
assert payload["exit_code"] == int(expected_exit), payload
assert isinstance(payload["message"], str), payload
PY
}

up_result="$temporary/up.json"
bash "$helper" up 20 --result-file "$up_result" --workspace "$workspace"
assert_result "$up_result" success 0

validate_result="$temporary/validate.json"
set +e
FAKE_DOCKER_EXIT_CODE=7 bash "$helper" validate 20 "$workspace" \
  --result-file "$validate_result"
validate_exit=$?
set -e
[[ "$validate_exit" -eq 7 ]]
assert_result "$validate_result" project_validation_failure 7

down_result="$temporary/down.json"
set +e
FAKE_DOCKER_EXIT_CODE=9 bash "$helper" down 20 --result-file "$down_result"
down_exit=$?
set -e
[[ "$down_exit" -eq 9 ]]
assert_result "$down_result" environment_failure 9

# Preserve the existing operator-facing positional workspace interface.
bash "$helper" up 21 "$workspace"

echo "Dev Container script result-file contract passed."
