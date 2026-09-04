#!/usr/bin/env python3
"""Enforce aggregate, critical-package, and changed-critical-code JaCoCo gates."""

import argparse
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def covered_ratio(root: ET.Element, counter_type: str) -> float:
    for counter in root.findall("counter"):
        if counter.get("type") == counter_type:
            missed = int(counter.get("missed", "0"))
            covered = int(counter.get("covered", "0"))
            total = missed + covered
            return covered / total if total else 1.0
    raise ValueError(f"Missing JaCoCo counter: {counter_type}")


def aggregate_package_ratio(root: ET.Element, prefix: str, counter_type: str) -> float:
    missed = 0
    covered = 0
    for package in root.iter("package"):
        name = package.get("name", "")
        if name != prefix and not name.startswith(prefix + "/"):
            continue
        for counter in package.findall("counter"):
            if counter.get("type") == counter_type:
                missed += int(counter.get("missed", "0"))
                covered += int(counter.get("covered", "0"))
    total = missed + covered
    if not total:
        raise ValueError(f"No {counter_type} coverage found for package prefix: {prefix}")
    return covered / total


def changed_lines(base_sha: str) -> dict[str, set[int]]:
    if not base_sha:
        return {}
    try:
        diff = subprocess.run(
            [
                "git",
                "diff",
                "--unified=0",
                "--no-color",
                f"{base_sha}...HEAD",
                "--",
                "*.java",
            ],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        print(f"Changed-code coverage skipped: cannot diff from {base_sha}")
        return {}

    files: dict[str, set[int]] = {}
    current: str | None = None
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current = line[6:]
            files.setdefault(current, set())
        elif line.startswith("@@ ") and current:
            match = re.search(r"\+(\d+)(?:,(\d+))?", line)
            if match:
                start = int(match.group(1))
                count = int(match.group(2) or "1")
                files[current].update(range(start, start + count))
    return files


def changed_critical_ratios(
    root: ET.Element, base_sha: str, critical_prefixes: set[str]
) -> tuple[float, float, int] | None:
    changed = changed_lines(base_sha)
    if not changed:
        return None

    line_covered = 0
    line_total = 0
    branch_covered = 0
    branch_total = 0
    source_roots = (
        "app",
        "modules/shared",
        "modules/investment",
        "modules/longterm",
        "modules/profile",
        "modules/retirement",
        "integrations",
        "adapters/web-ui",
    )
    for package in root.iter("package"):
        package_name = package.get("name", "")
        if not any(
            package_name == prefix or package_name.startswith(prefix + "/")
            for prefix in critical_prefixes
        ):
            continue
        package_path = package_name.replace("/", "/")
        for source in package.findall("sourcefile"):
            source_name = source.get("name", "")
            candidates = {
                f"{root_name}/src/main/java/{package_path}/{source_name}"
                for root_name in source_roots
            }
            touched = set().union(*(changed.get(candidate, set()) for candidate in candidates))
            for line in source.findall("line"):
                if int(line.get("nr", "0")) not in touched:
                    continue
                covered = int(line.get("ci", "0"))
                missed = int(line.get("mi", "0"))
                if covered + missed == 0:
                    continue
                line_total += 1
                line_covered += covered > 0
                branch_covered += int(line.get("cb", "0"))
                branch_total += int(line.get("cb", "0")) + int(line.get("mb", "0"))

    if not line_total:
        return None
    return (
        line_covered / line_total,
        branch_covered / branch_total if branch_total else 1.0,
        line_total,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", nargs="?", default="app/target/site/jacoco-aggregate/jacoco.xml")
    parser.add_argument("baseline", nargs="?", default="docs/quality/coverage-baseline.json")
    parser.add_argument("--changed-from", default="")
    args = parser.parse_args()
    report = Path(args.report)
    baseline = Path(args.baseline)
    root = ET.parse(report).getroot()
    expected = json.loads(baseline.read_text(encoding="utf-8"))
    actual = {"line": covered_ratio(root, "LINE"), "branch": covered_ratio(root, "BRANCH")}
    failures = [
        f"{name}: {actual[name]:.4%} < baseline {expected[name]:.4%}"
        for name in actual
        if actual[name] + 1e-12 < expected[name]
    ]
    print("Aggregate JaCoCo: " + ", ".join(f"{k}={v:.2%}" for k, v in actual.items()))
    critical = expected.get("critical", {})
    for prefix, thresholds in critical.get("packages", {}).items():
        for counter_type, key in (("LINE", "line"), ("BRANCH", "branch")):
            actual_ratio = aggregate_package_ratio(root, prefix, counter_type)
            threshold = float(thresholds[key])
            print(f"Critical package {prefix} {key}={actual_ratio:.2%}")
            if actual_ratio + 1e-12 < threshold:
                failures.append(
                    f"{prefix} {key}: {actual_ratio:.4%} < critical {threshold:.4%}"
                )

    changed = changed_critical_ratios(root, args.changed_from, set(critical.get("packages", {})))
    if changed:
        line_ratio, branch_ratio, line_count = changed
        thresholds = critical.get("changed", {})
        print(
            f"Changed critical code: lines={line_ratio:.2%}, branches={branch_ratio:.2%}, "
            f"executable lines={line_count}"
        )
        if line_ratio + 1e-12 < float(thresholds.get("line", 0)):
            failures.append(
                f"changed critical line coverage: {line_ratio:.4%} < {float(thresholds['line']):.4%}"
            )
        if branch_ratio + 1e-12 < float(thresholds.get("branch", 0)):
            failures.append(
                f"changed critical branch coverage: {branch_ratio:.4%} < {float(thresholds['branch']):.4%}"
            )
    elif args.changed_from:
        print("Changed critical code: no executable critical lines changed")

    if failures:
        print("Coverage gate failed: " + "; ".join(failures), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
