#!/usr/bin/env python3
"""Require every repository integration test to occur exactly once in CI."""

from collections import Counter
from pathlib import Path
import re
import sys


WORKFLOW = Path(".github/workflows/tests.yml")
SOURCE_ROOTS = (
    Path("app/src/test"),
    Path("modules/investment/src/test"),
    Path("modules/retirement/src/test"),
)


def main() -> int:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    registered = re.findall(
        r"(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9]*IT)(?=[,\"\s])", workflow
    )
    counts = Counter(registered)
    source = {path.stem for root in SOURCE_ROOTS for path in root.rglob("*IT.java")}
    duplicate = sorted(name for name in source if counts[name] > 1)
    missing = sorted(name for name in source if counts[name] == 0)
    unknown = sorted(name for name in counts if name not in source)
    if duplicate or missing or unknown:
        print("Integration-test registration drift detected.")
        if duplicate:
            print("registered more than once:", ", ".join(duplicate))
        if missing:
            print("not registered:", ", ".join(missing))
        if unknown:
            print("registered without a source IT:", ", ".join(unknown))
        return 1
    print(f"Checked {len(source)} integration tests: each registered exactly once.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
