#!/usr/bin/env bash
set -euo pipefail

directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export INVESTORY_SMOKE_IMAGE="${1:?pass the published Java image by digest}"
if [[ ! "$INVESTORY_SMOKE_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]]; then
  echo 'Pass a registry image pinned by digest.' >&2
  exit 1
fi

project="investory-assistant-smoke-$RANDOM-$$"
compose=(docker compose --project-name "$project" --file "$directory/compose.yaml")
trap '"${compose[@]}" down --volumes --remove-orphans' EXIT
if ! "${compose[@]}" up --wait --wait-timeout 240; then
  "${compose[@]}" logs --tail=100
  exit 1
fi
if ! "${compose[@]}" exec -T fixture python /smoke/check.py http://investory:8080; then
  "${compose[@]}" logs --tail=100
  exit 1
fi
