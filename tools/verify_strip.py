#!/usr/bin/env python3
"""Verify strip_comments preserves every string/char literal (dry check)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from strip_comments import strip_java

LIT = re.compile(r'"(?:[^"\\]|\\.)*"|\'(?:[^\'\\\n]|\\.)*\'')

bad = 0
for raw in sys.argv[1:]:
    src = Path(raw).read_text(encoding="utf-8")
    out, _ = strip_java(src)
    a, b = sorted(set(LIT.findall(src))), sorted(set(LIT.findall(out)))
    status = "SAME" if a == b else "MISMATCH"
    if a != b:
        bad += 1
        print(raw, status, sorted(set(a) ^ set(b))[:5])
    else:
        print(raw, status, len(a), "literals")
sys.exit(1 if bad else 0)
