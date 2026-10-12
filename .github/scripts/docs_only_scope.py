#!/usr/bin/env python3
"""Classify a Git diff for the lightweight documentation-only CI path."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import PurePosixPath


DOC_SUFFIXES = {".md", ".mdx", ".rst", ".adoc", ".txt"}
DOC_ROOTS = {"docs", ".codex"}
DOC_NAMES = {"README.md", "CHANGELOG.md", "ROADMAP.md", "AGENTS.md"}
CI_SUPPORT_FILES = {
    ".github/workflows/tests.yml",
    ".github/scripts/docs_only_scope.py",
    ".github/scripts/required_check_names.py",
    ".github/required-checks.txt",
}


def is_documentation_path(path: str) -> bool:
    candidate = PurePosixPath(path)
    if candidate.name in DOC_NAMES:
        return True
    if candidate.parts and candidate.parts[0] in DOC_ROOTS:
        return candidate.suffix.lower() in DOC_SUFFIXES
    return False


def is_docs_only(paths: list[str], *, allow_ci_support: bool) -> bool:
    if not paths:
        return False
    for path in paths:
        if is_documentation_path(path):
            continue
        if allow_ci_support and path in CI_SUPPORT_FILES:
            continue
        return False
    return True


def classify(paths: list[str], *, allow_ci_support: bool, full_ci: bool) -> bool:
    """Return docs_only; an explicit full-CI request always runs every job."""
    if full_ci:
        return False
    return is_docs_only(paths, allow_ci_support=allow_ci_support)


def self_test() -> None:
    cases = [
        (["README.md", "docs/architecture/security.md"], False, True),
        (["README.md", "app/src/main/App.java"], False, False),
        (["README.md", ".github/workflows/tests.yml"], False, False),
        (["README.md", ".github/workflows/tests.yml"], True, True),
        ([".github/workflows/tests.yml"], True, True),
        ([".github/scripts/docs_only_scope.py", "app/src/main/App.java"], True, False),
        ([], True, False),
    ]
    for paths, allow_ci_support, expected in cases:
        actual = is_docs_only(paths, allow_ci_support=allow_ci_support)
        if actual != expected:
            raise AssertionError(
                f"scope mismatch for {paths}: expected {expected}, got {actual}"
            )
    if classify(["README.md"], allow_ci_support=False, full_ci=True):
        raise AssertionError("full CI request must disable the documentation-only path")
    if not classify(["README.md"], allow_ci_support=False, full_ci=False):
        raise AssertionError("documentation-only policy must apply without a full CI request")


def main() -> int:
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        print("Documentation scope classifier checks passed.")
        return 0

    base_sha = os.getenv("BASE_SHA", "")
    head_sha = os.getenv("HEAD_SHA", "")
    allow_ci_support = os.getenv("DOCS_ONLY_CI_APPROVED", "false").lower() == "true"
    full_ci = os.getenv("FULL_CI_REQUESTED", "false").lower() == "true"
    paths: list[str] = []
    docs_only = False
    if base_sha and head_sha and set(base_sha) != {"0"}:
        result = subprocess.run(
            ["git", "diff", "--name-only", "-z", f"{base_sha}...{head_sha}"],
            check=False,
            capture_output=True,
        )
        if result.returncode == 0:
            paths = [path for path in result.stdout.decode().split("\0") if path]
            docs_only = classify(paths, allow_ci_support=allow_ci_support, full_ci=full_ci)

    print("Changed paths:")
    for path in paths:
        print(f"- {path}")
    print(f"Full CI requested: {full_ci}")
    print(f"Documentation-only: {docs_only}")
    output = os.getenv("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"docs_only={str(docs_only).lower()}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
