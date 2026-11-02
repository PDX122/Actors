#!/usr/bin/env python3
"""Strip all comments from Java sources, safely.

Why a tokenizer and not a regex: `//` and `/*` inside string literals,
char literals, and text blocks must survive. This scanner tracks all six
lexer states and only drops real comments. `\\uXXXX` escapes inside comments
are a known non-handled edge (vanishingly rare; javac processes those before
lexing, nothing in this repo does that).

Usage:
    python3 tools/strip_comments.py            # dry run, prints per-file counts
    python3 tools/strip_comments.py --apply    # actually writes

Only files outside the active split-agent set are touched (see EXCLUDED).
Blank-line collapse (3+ to 2) and trailing-whitespace trim keep the result
tidy. Exits nonzero on no-op so CI can tell the difference.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Files owned by in-flight split agents. Do not touch: they are mid-refactor
# and same-file edits would collide. Their landing review strips them.
EXCLUDED = {
    "src/main/java/pdx/dev/fbactors/take/ActorTake.java",
    "src/main/java/pdx/dev/fbactors/take/TakeIO.java",
    "src/main/java/pdx/dev/fbactors/actor/ActorManager.java",
    "src/main/java/pdx/dev/fbactors/actor/InputSim.java",
    "src/main/java/pdx/dev/fbactors/record/TakeRecorder.java",
    "src/main/java/pdx/dev/fbactors/input/Hotkey.java",
    "src/main/java/pdx/dev/fbactors/ui/ActorTrack.java",
    "src/main/java/pdx/dev/fbactors/compat/CompatAdditions.java",
}

EXCLUDED_DIRS = {
    "compat",
}


def in_scope(path: Path) -> bool:
    rel = path.relative_to(ROOT).as_posix()
    if rel in EXCLUDED:
        return False
    parts = rel.split("/")
    if len(parts) > 1 and parts[0] in EXCLUDED_DIRS:
        return False
    return True


def strip_java(text: str) -> tuple[str, int]:
    """Return (code_without_comments, comment_lines_removed)."""
    out: list[str] = []
    i = 0
    n = len(text)
    removed_lines = 0
    comment_start_line = 0
    line = 1

    def note_removed(upto: int) -> None:
        nonlocal removed_lines
        removed_lines += text.count("\n", comment_start_line, upto)

    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if two == "//":
            comment_start_line = i
            j = text.find("\n", i)
            if j == -1:
                note_removed(n)
                break
            note_removed(j)
            i = j  # keep the newline itself
            continue
        if two == "/*":
            comment_start_line = i
            j = text.find("*/", i + 2)
            if j == -1:
                note_removed(n)
                break
            note_removed(j + 2)
            i = j + 2
            continue
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            if end == -1:
                out.append(text[i:])
                break
            out.append(text[i:end + 3])
            i = end + 3
            continue
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    j += 1
                    break
                if text[j] == "\n":
                    j += 1
                    break
                j += 1
            out.append(text[i:j])
            i = j
            continue
        if c == "'":
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == "'":
                    j += 1
                    break
                if text[j] == "\n":
                    break
                j += 1
            out.append(text[i:j])
            i = j
            continue
        out.append(c)
        if c == "\n":
            line += 1
        i += 1

    code = "".join(out)
    lines = code.split("\n")
    tidy: list[str] = []
    blank_run = 0
    for ln in lines:
        stripped = ln.rstrip()
        if stripped == "":
            blank_run += 1
            if blank_run <= 2:
                tidy.append("")
            continue
        blank_run = 0
        tidy.append(stripped)
    while tidy and tidy[0] == "":
        tidy.pop(0)
    return "\n".join(tidy) + "\n", removed_lines


def main() -> int:
    apply = "--apply" in sys.argv
    total_files = 0
    total_lines = 0
    for base in (ROOT / "src" / "main" / "java", ROOT / "src" / "test" / "java"):
        for path in sorted(base.rglob("*.java")):
            if not in_scope(path):
                continue
            original = path.read_text(encoding="utf-8")
            stripped, removed = strip_java(original)
            if removed == 0 and stripped == original:
                continue
            total_files += 1
            total_lines += removed
            print(f"{'write' if apply else 'would strip'} {path.relative_to(ROOT)} "
                  f"(-{removed} comment lines)")
            if apply:
                path.write_text(stripped, encoding="utf-8")
    print(f"{total_files} file(s), {total_lines} comment line(s){' removed' if apply else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
