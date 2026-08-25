#!/usr/bin/env python3
"""Fail when a repository-local Markdown link points to a missing path."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {".git", ".idea", ".m2", ".m2-local", "target", "node_modules"}
LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")


def markdown_files() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*.md")
        if not any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts)
    )


def prose_lines(path: Path):
    in_fence = False
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            continue
        if not in_fence:
            yield line_number, line


def local_target(raw_target: str) -> str | None:
    target = raw_target.strip()
    if not target or target.startswith("#") or target.startswith("<"):
        return None

    # Markdown permits an optional quoted title after the destination.
    if " \"" in target:
        target = target.split(" \"", 1)[0]
    elif " '" in target:
        target = target.split(" '", 1)[0]

    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc:
        return None

    path = unquote(parsed.path)
    return path or None


def main() -> int:
    failures: list[str] = []

    for markdown in markdown_files():
        for line_number, line in prose_lines(markdown):
            for match in LINK_RE.finditer(line):
                target = local_target(match.group(1))
                if target is None:
                    continue

                candidate = (ROOT / target.lstrip("/")) if target.startswith("/") else (markdown.parent / target)
                if not candidate.resolve().exists():
                    failures.append(
                        f"{markdown.relative_to(ROOT)}:{line_number}: missing local link {match.group(1)!r}"
                    )

    if failures:
        print("Broken repository-local Markdown links:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(f"Checked repository-local Markdown links in {len(markdown_files())} files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
