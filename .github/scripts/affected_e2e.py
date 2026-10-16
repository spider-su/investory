#!/usr/bin/env python3
"""Run browser ITs owned by changed Maven modules and their consumers."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

# Selection must not create untracked bytecode that changes its own Git impact.
sys.dont_write_bytecode = True
from docs_only_scope import is_documentation_path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def reactor(root: Path) -> tuple[dict[str, set[str]], dict[str, list[str]]]:
    projects = {}

    def visit(directory: Path) -> None:
        project = ET.parse(directory / "pom.xml").getroot()
        name = project.findtext("m:artifactId", namespaces=NS)
        if not name or name in projects:
            raise ValueError("Missing or duplicate reactor artifactId")
        projects[name] = (directory.relative_to(root).as_posix(), project)
        for module in project.findall("m:modules/m:module", NS):
            visit(directory / str(module.text))

    visit(root)
    dependencies = {}
    suites = {}
    for path, project in projects.values():
        dependencies[path] = {
            projects[str(dependency.text)][0]
            for dependency in project.findall("m:dependencies/m:dependency/m:artifactId", NS)
            if dependency.text in projects
        }
        suites[path] = sorted(
            test.stem for test in (root / path / "src/test/java").rglob("*IT.java")
            if "ui" in test.relative_to(root / path / "src/test/java").parts
        )
    return dependencies, suites


def select(root: Path, paths: list[str] | None) -> dict:
    dependencies, suites = reactor(root)
    owners = sorted(path for path, tests in suites.items() if tests)
    reason = "unknown impact: all browser-test owners"
    selected = owners
    if paths is not None and all(is_documentation_path(path) for path in paths):
        selected, reason = [], "documentation-only or unchanged candidate"
    elif paths is not None:
        changed = set()
        for path in paths:
            if is_documentation_path(path):
                continue
            matches = [module for module in dependencies if module != "." and path.startswith(module + "/")]
            if not matches or path.endswith("/pom.xml"):
                break  # Root/build/shared tooling or uncertain graph: keep all owners.
            changed.add(max(matches, key=len))
        else:
            affected = set(changed)
            while True:
                expanded = affected | {module for module, deps in dependencies.items() if deps & affected}
                if expanded == affected:
                    break
                affected = expanded
            selected = sorted(set(owners) & affected)
            reason = "changed modules and transitive consumers: " + ", ".join(sorted(affected))
    tests = sorted({test for owner in selected for test in suites[owner]})
    return {"modules": selected, "tests": tests, "reason": reason}


def changed_paths(root: Path, baseline: str) -> list[str] | None:
    if not re.fullmatch(r"[0-9a-f]{40}", baseline):
        return None
    try:
        tracked = subprocess.check_output(
            ["git", "diff", "--no-renames", "--name-only", "-z", baseline, "--"], cwd=root)
        untracked = subprocess.check_output(
            ["git", "ls-files", "--others", "--exclude-standard", "-z"], cwd=root)
        return sorted({path for path in (tracked + untracked).decode().split("\0") if path})
    except (subprocess.CalledProcessError, UnicodeDecodeError):
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-sha", default="")
    parser.add_argument("--plan-only", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        plan = select(root, changed_paths(root, args.baseline_sha))
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"Cannot determine browser test owners: {error}")
        return 127  # Environment/configuration problem; never silently skip E2E.
    print("Affected E2E: " + json.dumps(plan, sort_keys=True), flush=True)
    if not plan["tests"] or args.plan_only:
        return 0
    # Preinstalled Chromium and Docker/Testcontainers are prerequisites; no provisioning.
    return subprocess.call([
        "mvn", "-B", "-pl", ",".join(plan["modules"]), "-am",
        "initialize", "test-compile", "failsafe:integration-test", "failsafe:verify",
        "-Dit.test=" + ",".join(plan["tests"]), "-Dfailsafe.failIfNoSpecifiedTests=false",
    ], cwd=root, env={**os.environ, "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD": "1"})


if __name__ == "__main__":
    raise SystemExit(main())
