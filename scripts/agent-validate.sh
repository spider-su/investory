#!/usr/bin/env bash
set -euo pipefail

mvn -B spotless:check
mvn -B test
mvn -B package -DskipTests

# Baseline is supplied by the Worker; absent/unknown impact selects all browser owners.
python3 .github/scripts/affected_e2e.py "$@"
