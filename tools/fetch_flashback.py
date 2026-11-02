#!/usr/bin/env python3
"""Fetch the proprietary Flashback jars from Modrinth for CI.

Why this exists: the compile jars (libs/) and game-test jars
(versions/<mc>/run/mods/) are built from a proprietary mod and cannot live in
git, which used to mean only the maintainer's machine could compile. Our
release jars contain zero Flashback classes (compile-only use), so downloading
the dependency at build time is standard practice, not redistribution.

Each target pins two jars in versions/<mc>/gradle.properties:
`flashback_jar` (compile, e.g. flashback-0.39.7-for-MC1.21.1.jar) and
`flashback_run_jar` (game test, e.g. Flashback-0.39.10-for-MC1.21.1.jar).
This script resolves both on Modrinth (public API, no key), verifies sha512,
and installs them at those exact filenames.

Usage:
    python3 tools/fetch_flashback.py                  # fetch what's missing
    python3 tools/fetch_flashback.py --check           # resolve only, print URLs
    python3 tools/fetch_flashback.py --force           # redownload everything
    python3 tools/fetch_flashback.py --versions 1.21.11,26.3
"""

from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIBS = ROOT / "libs"
API = "https://api.modrinth.com/v2/project/flashback/version"
USER_AGENT = "PDX122/Actors CI (https://github.com/PDX122/Actors)"


def read_props(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def modrinth_versions(mc: str) -> list:
    url = API + "?game_versions=" + urllib.parse.quote(f'["{mc}"]')
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def parse_libs_name(name: str) -> tuple[str, str] | None:
    """flashback-0.39.7-for-MC1.21.1.jar -> (0.39.7, 1.21.1)."""
    lower = name.lower()
    if not lower.startswith("flashback-") or "-for-mc" not in lower:
        return None
    version, _, mc = lower[len("flashback-"):].partition("-for-mc")
    mc = mc[:-len(".jar")] if mc.endswith(".jar") else mc
    return (version, mc) if version and mc else None


def resolve(mc: str, wanted_version: str, cache: dict) -> tuple[str, str, str]:
    """Return (filename, url, sha512) for an upstream Flashback file."""
    if mc not in cache:
        cache[mc] = modrinth_versions(mc)
    for entry in cache[mc]:
        if entry.get("version_number") != wanted_version:
            continue
        for file in entry.get("files", []):
            name = file.get("filename", "")
            if name.lower().endswith(f"-for-mc{mc}.jar".lower()):
                return name, file["url"], file["hashes"]["sha512"]
    raise SystemExit(
        f"No upstream Flashback {wanted_version} file for MC {mc} on Modrinth "
        f"(checked {len(cache[mc])} version entries)"
    )


def sha512_of(path: Path) -> str:
    digest = hashlib.sha512()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str, dest: Path, digest: str, attempts: int = 3) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    # Big files over a long CDN hop corrupt now and then; a checksum-gated
    # retry beats failing a 15-minute build on one bad download.
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=300) as response, open(tmp, "wb") as out:
            while True:
                chunk = response.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
        if sha512_of(tmp) == digest:
            tmp.replace(dest)
            return
        print(f"checksum mismatch for {dest.name} (attempt {attempt}/{attempts})")
    tmp.unlink(missing_ok=True)
    raise SystemExit(f"Checksum mismatch downloading {dest.name} after {attempts} attempts")


def main() -> int:
    args = sys.argv[1:]
    check_only = "--check" in args
    force = "--force" in args
    wanted = None
    if "--versions" in args:
        wanted = set(args[args.index("--versions") + 1].split(","))

    cache: dict = {}
    jobs = 0
    for vdir in sorted((ROOT / "versions").iterdir()):
        props_file = vdir / "gradle.properties"
        if not vdir.is_dir() or not props_file.exists():
            continue
        if wanted and vdir.name not in wanted:
            continue
        props = read_props(props_file)
        mc = props.get("mc_version", vdir.name)
        libs_name = props.get("flashback_jar", "")
        run_name = props.get("flashback_run_jar", "")
        if not libs_name or not run_name:
            raise SystemExit(f"{vdir.name}: gradle.properties needs flashback_jar and flashback_run_jar")
        parsed = parse_libs_name(libs_name)
        if parsed is None:
            raise SystemExit(f"{vdir.name}: cannot parse version from {libs_name}")
        libs_version, file_mc = parsed
        run_version = run_name[len("Flashback-"):].split("-for-MC")[0]

        for want_name, want_version, dest in (
            (libs_name, libs_version, LIBS / libs_name),
            (run_name, run_version, vdir / "run" / "mods" / run_name),
        ):
            # Match on the MC tag from the filename (e.g. 26.1), not the
            # full game version from props (e.g. 26.1.2): upstream files and
            # game_versions entries use the short tag.
            _, url, digest = resolve(file_mc, want_version, cache)
            if check_only:
                print(f"{vdir.name}: {want_name} <- {url}")
                continue
            if dest.exists() and not force:
                print(f"ok: {dest.relative_to(ROOT)} present")
                continue
            print(f"fetch: {want_name} from Modrinth")
            fetch(url, dest, digest)
            print(f"ok: {dest.relative_to(ROOT)}")
            jobs += 1

    if check_only:
        print("all pins resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
