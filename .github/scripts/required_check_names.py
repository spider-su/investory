#!/usr/bin/env python3
"""Verify that tests.yml emits every required check name exactly as configured.

Skipped matrix jobs are reported by GitHub with their unexpanded name template,
so a required name such as ``BE_IT (contracts)`` exists only when the job runs.
This check expands job and matrix names from the workflow source and compares
them with ``.github/required-checks.txt`` (the names the orchestrator requires).
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "tests.yml"
REQUIRED = ROOT / ".github" / "required-checks.txt"
MATRIX_NAME = "${{ matrix.suite.name }}"
ADVISORY_MATRIX = re.compile(r"continue-on-error:\s*\$\{\{\s*matrix\.suite\.name\s*==\s*'([^']+)'\s*\}\}")


def emitted_checks(workflow: str) -> tuple[set[str], set[str]]:
    """Return (blocking, advisory) check names emitted when every job runs."""
    blocking: set[str] = set()
    advisory: set[str] = set()
    in_jobs = False
    jobs: list[list[str]] = []
    for line in workflow.splitlines():
        if line.startswith("jobs:"):
            in_jobs = True
            continue
        if in_jobs and line and not line.startswith(" "):
            in_jobs = False
        if not in_jobs:
            continue
        if re.match(r"^  [A-Za-z0-9_-]+:\s*$", line):
            jobs.append([])
        elif jobs:
            jobs[-1].append(line)
    for body in jobs:
        name = next((line.split("name:", 1)[1].strip() for line in body
                     if re.match(r"^    name:", line)), "")
        if not name:
            raise ValueError("every job needs an explicit display name")
        job_text = "\n".join(body)
        always_advisory = re.search(r"^    continue-on-error:\s*true\s*$", job_text, re.MULTILINE) is not None
        advisory_suites = set(ADVISORY_MATRIX.findall(job_text))
        if MATRIX_NAME in name:
            suites = [match.group(1).strip() for match in
                      re.finditer(r"^          - name:\s*(.+)$", job_text, re.MULTILINE)]
            if not suites:
                raise ValueError(f"matrix job {name!r} has no suite names")
            for suite in suites:
                target = advisory if always_advisory or suite in advisory_suites else blocking
                target.add(name.replace(MATRIX_NAME, suite))
        elif "${{" in name:
            raise ValueError(f"unsupported name template: {name!r}")
        else:
            (advisory if always_advisory else blocking).add(name)
    return blocking, advisory


def required_checks(text: str) -> list[str]:
    return [line.strip() for line in text.splitlines() if line.strip() and not line.startswith("#")]


def compare(required: list[str], blocking: set[str], advisory: set[str]) -> list[str]:
    errors = []
    if len(required) != len(set(required)):
        errors.append("required-checks.txt contains duplicate names")
    for name in required:
        if name in advisory:
            errors.append(f"required check is advisory (continue-on-error): {name}")
        elif name not in blocking:
            errors.append(f"required check is not emitted by tests.yml: {name}")
    for name in sorted(blocking - set(required)):
        errors.append(f"blocking check is missing from required-checks.txt: {name}")
    return errors


def self_test() -> None:
    workflow = """jobs:
  scope:
    name: Change scope
  advisory:
    name: Coverage
    continue-on-error: true
  suites:
    name: BE_IT (${{ matrix.suite.name }})
    continue-on-error: ${{ matrix.suite.name == 'slow' }}
    strategy:
      matrix:
        suite:
          - name: fast
            project: app
          - name: slow
            project: app
"""
    blocking, advisory = emitted_checks(workflow)
    assert blocking == {"Change scope", "BE_IT (fast)"}, blocking
    assert advisory == {"Coverage", "BE_IT (slow)"}, advisory
    assert compare(["Change scope", "BE_IT (fast)"], blocking, advisory) == []
    assert compare(["Change scope", "BE_IT (fast)", "BE_IT (slow)"], blocking, advisory)
    assert compare(["Change scope", "BE_IT (fast)", "BE_IT (missing)"], blocking, advisory)
    assert compare(["Change scope"], blocking, advisory)


def main() -> int:
    self_test()
    blocking, advisory = emitted_checks(WORKFLOW.read_text(encoding="utf-8"))
    required = required_checks(REQUIRED.read_text(encoding="utf-8"))
    errors = compare(required, blocking, advisory)
    print(f"Required checks: {len(required)}; blocking emitted: {len(blocking)}; advisory: {', '.join(sorted(advisory))}")
    for error in errors:
        print(f"::error::{error}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
