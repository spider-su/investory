#!/usr/bin/env python3
"""Fail-closed, read-only gate for a future nightly release workflow."""

import json
import sys


def assess(develop_sha: str, main_sha: str, checks: list[dict], required: list[str] | None = None, *, unreleased_commits: int | None = None) -> tuple[str, str]:
    if not develop_sha or not main_sha:
        return "fail", "missing branch SHA"
    if unreleased_commits is not None and (type(unreleased_commits) is not int or unreleased_commits < 0):
        return "fail", "invalid unreleased commit count"
    if unreleased_commits == 0:
        return "skip", "develop is already included in main; no release needed"
    if develop_sha == main_sha:
        return "skip", "develop and main are identical; no release needed"
    if not checks:
        return "fail", f"no checks reported for exact develop SHA {develop_sha}"
    missing = set(required or []) - {c.get("name") for c in checks}
    if missing:
        return "fail", "missing required jobs: " + ", ".join(sorted(missing))
    mismatched = [c for c in checks if c.get("head_sha") != develop_sha]
    if mismatched:
        return "fail", "check result does not match exact develop SHA"
    failures = [c for c in checks if c.get("conclusion") != "success"]
    if failures:
        return "fail", "one or more exact-SHA checks are missing, pending, or failed"
    return "preflight", "exact-SHA checks pass; promotion and production rollout remain disabled"


if __name__ == "__main__":
    try:
        data = json.load(sys.stdin)
        from pathlib import Path
        required = [line.strip() for line in Path(".github/required-checks.txt").read_text().splitlines() if line.strip() and not line.startswith("#")]
        state, reason = assess(data["develop_sha"], data["main_sha"], data["checks"], required, unreleased_commits=data.get("unreleased_commits"))
        print(json.dumps({"state": state, "reason": reason}))
        sys.exit(1 if state == "fail" else 0)
    except (KeyError, TypeError, json.JSONDecodeError) as exc:
        print(json.dumps({"state": "fail", "reason": f"invalid preflight input: {exc}"}))
        sys.exit(1)
