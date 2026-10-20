#!/usr/bin/env python3
"""Prepend the project licence header to every Java source file.

Why this exists: the header carries the licence plus a short note for anyone
reading the code in a decompiler (the readable source beats bytecode,
contributions beat copies, redistribution as your own work is not permitted).

Usage:
    python3 tools/add_headers.py            # dry run, prints what would change
    python3 tools/add_headers.py --apply    # actually writes

Idempotent: files already starting with the header marker are skipped.
Deliberately NOT run while file-split work is in flight — run it once the
splits land so no merge has to care about header lines.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

MARKER = "// Flashback Actors"

HEADER = """// Flashback Actors - MIT licence. Source: https://github.com/PDX122/Actors
// Keep the copyright notice; redistribution as your own work is not permitted.
"""

SOURCE_DIRS = [
    ROOT / "src" / "main" / "java",
    ROOT / "src" / "test" / "java",
    *(d for d in (ROOT / "compat").glob("*/java") if d.is_dir()),
]


def needs_header(text: str) -> bool:
    stripped = text.lstrip("\ufeff \t\r\n")
    return not stripped.startswith(MARKER)


def add_header(path: Path) -> None:
    raw = path.read_bytes()
    text = raw.decode("utf-8-sig")
    if not needs_header(text):
        return
    newline = "\r\n" if "\r\n" in text else "\n"
    header = HEADER.replace("\n", newline)
    path.write_text(header + newline + text.lstrip("\ufeff"), encoding="utf-8")


def main() -> int:
    apply = "--apply" in sys.argv
    changed = 0
    for base in SOURCE_DIRS:
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.java")):
            try:
                text = path.read_bytes().decode("utf-8-sig")
            except OSError:
                continue
            if needs_header(text):
                changed += 1
                print(("write " if apply else "would head ") + str(path.relative_to(ROOT)))
                if apply:
                    add_header(path)
    print(f"{changed} file(s){' headed' if apply else ' need headers'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
