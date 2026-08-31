#!/usr/bin/env python3
"""Enforce the checked-in aggregate JaCoCo baseline."""

import json
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


def main() -> int:
    report = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/target/site/jacoco-aggregate/jacoco.xml")
    baseline = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("docs/quality/coverage-baseline.json")
    root = ET.parse(report).getroot()
    expected = json.loads(baseline.read_text(encoding="utf-8"))
    actual = {"line": covered_ratio(root, "LINE"), "branch": covered_ratio(root, "BRANCH")}
    failures = [
        f"{name}: {actual[name]:.4%} < baseline {expected[name]:.4%}"
        for name in actual
        if actual[name] + 1e-12 < expected[name]
    ]
    print("Aggregate JaCoCo: " + ", ".join(f"{k}={v:.2%}" for k, v in actual.items()))
    if failures:
        print("Coverage gate failed: " + "; ".join(failures), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
