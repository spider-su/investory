#!/usr/bin/env bash
set -euo pipefail

mvn -B spotless:check
mvn -B test
mvn -B package -DskipTests
