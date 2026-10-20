#!/usr/bin/env python3
"""Verify every compat mixin descriptor against the real game signatures.

Why this exists: mixin handlers declaring the wrong parameter list compile
fine and then crash the game at startup with ``InvalidInjectionException:
Invalid descriptor`` (seen on 1.21.11 for both ``EntityRenderer.shouldRender``,
which lost its ``float tickDelta``, and ``KeyboardHandler.keyPress``, which
became ``(long, int, KeyEvent)`` from 1.21.10 on). ``javac`` cannot catch that,
so this script does: for each ``versions/*/gradle.properties`` entry it
resolves the real Minecraft jar from the Fabric Loom cache, runs ``javap``,
and asserts each compat mixin source declares matching parameters. It also
asserts the Flashback jars each target compiles and game-tests against are
actually present.

Usage:
    python3 tools/verify_mixins.py [--no-live] [--no-flashback]

``--no-live`` skips the Loom-cache ``javap`` lookup and checks against the
hardcoded ground-truth tables only (useful on machines without a Gradle cache).

``--no-flashback`` turns missing Flashback jars (``libs/`` compile jars and
per-target ``run/mods/`` jars) from failures into warnings. Those jars are
built locally from a proprietary mod and cannot travel with the repo, so
public CI - which has no way to obtain them - checks signatures only. A
developer machine with the jars present should run without the flag.

Exit status is 0 when every version passes, 1 otherwise.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSIONS = ROOT / "versions"
COMPAT = ROOT / "compat"
LIBS = ROOT / "libs"
LOOM_CACHE = Path(os.path.expanduser("~")) / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"

# Fallback ground truth, verified with javap against the Loom cache on
# 2026-10-08. Keys are compat dirs; values are (target, needs_tick_delta).
# "renderer"  -> @Mixin(EntityRenderer) on shouldRender
# "dispatcher"-> @Mixin(EntityRenderDispatcher) on render
EXPECTED = {
    "mc1211": ("dispatcher", False),   # render(E, DDD, yaw, tickDelta, ...)
    "mc1214": ("dispatcher", False),   # render(E, DDD, yaw, ...)
    "mc1215": ("dispatcher", False),
    "mc1218": ("dispatcher", False),
    "mc12110": ("renderer", False),    # shouldRender(E, Frustum, DDD)
    "mc12111": ("renderer", False),
    "mc261": ("renderer", False),
    "mc262": ("renderer", False),
    "mc263": ("renderer", True),       # shouldRender(E, Frustum, DDD, F)
}

DISPATCHER_NEEDS_TWO_FLOATS = {"mc1211"}

# Keyboard hook shape per compat dir: "glfw" means keyPress(long,int,int,int,int),
# "event" means keyPress(long,int,KeyEvent). Changed at 1.21.10 (and 26.x).
KEYBOARD = {
    "mc1211": "glfw",
    "mc1214": "glfw",
    "mc1215": "glfw",
    "mc1218": "glfw",
    "mc12110": "event",
    "mc12111": "event",
    "mc261": "event",
    "mc262": "event",
    "mc263": "event",
}

SOUND_MIXIN = "src/main/java/pdx/dev/fbactors/mixin/MixinLevelSound.java"

# Level.playSound hooks in MixinLevelSound: (key, descriptor, required).
# Only the Entity-first BlockPos overload exists on every target, so only it
# is required; every other overload hooks with require = 0.
SOUND_HOOKS = (
    ("entity_blockpos",
     "playSound(Lnet/minecraft/world/entity/Entity;"
     "Lnet/minecraft/core/BlockPos;"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;FF)V",
     True),
    ("player_blockpos",
     "playSound(Lnet/minecraft/world/entity/player/Player;"
     "Lnet/minecraft/core/BlockPos;"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;FF)V",
     False),
    ("entity_ddd",
     "playSound(Lnet/minecraft/world/entity/Entity;DDD"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;FF)V",
     False),
    ("player_ddd",
     "playSound(Lnet/minecraft/world/entity/player/Player;DDD"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;FF)V",
     False),
    ("entity_ddd_novol",
     "playSound(Lnet/minecraft/world/entity/Entity;DDD"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;)V",
     False),
    ("player_ddd_novol",
     "playSound(Lnet/minecraft/world/entity/player/Player;DDD"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;)V",
     False),
    ("entity_entity",
     "playSound(Lnet/minecraft/world/entity/Entity;"
     "Lnet/minecraft/world/entity/Entity;"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;FF)V",
     False),
    ("player_entity",
     "playSound(Lnet/minecraft/world/entity/player/Player;"
     "Lnet/minecraft/world/entity/Entity;"
     "Lnet/minecraft/sounds/SoundEvent;"
     "Lnet/minecraft/sounds/SoundSource;FF)V",
     False),
)

# Fallback ground truth: SoundEvent playSound overloads present per compat dir,
# verified with javap against the Loom cache on 2026-10-20. The first parameter
# changed from Player to Entity at 1.21.5, so mc1211/mc1214 carry the Player set
# (plus the Entity BlockPos overload) and everything newer carries Entity only.
# Holder-carrying overloads are deliberately unhooked (see MixinLevelSound) and
# are excluded here: 1.21.x targets carry one (Player/Entity-first DDD Holder),
# 26.3 carries two (DDD Holder plus an Entity-target Holder).
SOUND_EXPECTED = {
    "mc1211": frozenset({"entity_blockpos", "player_blockpos", "player_ddd",
                          "player_ddd_novol", "player_entity"}),
    "mc1214": frozenset({"entity_blockpos", "player_blockpos", "player_ddd",
                          "player_ddd_novol", "player_entity"}),
    "mc1215": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                          "entity_entity"}),
    "mc1218": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                          "entity_entity"}),
    "mc12110": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                           "entity_entity"}),
    "mc12111": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                           "entity_entity"}),
    "mc261": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                         "entity_entity"}),
    "mc262": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                         "entity_entity"}),
    "mc263": frozenset({"entity_blockpos", "entity_ddd", "entity_ddd_novol",
                         "entity_entity"}),
}


def read_props(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    return props


def find_javap() -> str | None:
    which = shutil.which("javap")
    if which:
        return which
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        cand = Path(java_home) / "bin" / ("javap.exe" if os.name == "nt" else "javap")
        if cand.exists():
            return str(cand)
    return None


def find_game_jar(mc_version: str) -> Path | None:
    """Locate the Loom minecraft-merged jar for an mc_version."""
    if not LOOM_CACHE.exists():
        return None
    for child in LOOM_CACHE.iterdir():
        if child.name == mc_version or child.name.startswith(mc_version + "-"):
            jars = list(child.glob("*.jar"))
            if jars:
                return jars[0]
    return None


def javap_text(javap: str, jar: Path, cls: str, private: bool = False) -> str:
    """Full javap output for one class, newlines flattened for matching."""
    cmd = [javap, "-classpath", str(jar), cls]
    if private:
        cmd.insert(1, "-p")
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.SubprocessError):
        return ""
    return " ".join((r.stdout + r.stderr).split())


def live_floats(mc_version: str, javap: str | None, target: str) -> int | None:
    """Count float params on the mixin's target method in the real game jar.

    Returns None when the jar or javap is unavailable (caller falls back).
    """
    if javap is None:
        return None
    jar = find_game_jar(mc_version)
    if jar is None:
        return None
    if target == "renderer":
        text = javap_text(javap, jar, "net.minecraft.client.renderer.entity.EntityRenderer")
        m = re.search(r"shouldRender\(T,\s*net\.minecraft\.client\.renderer\.culling\.Frustum,(.*?)\)", text)
    else:
        text = javap_text(
            javap, jar, "net.minecraft.client.renderer.entity.EntityRenderDispatcher"
        )
        m = re.search(r"void render\(E, double, double, double,(.*?)com\.mojang", text)
    if not m:
        return None
    return m.group(1).count("float")


def check_mixin_live(compat: str, src: str, target: str, live_tick: bool) -> list[str]:
    """Assert the mixin source matches the live game signature.

    ``live_tick`` is whether the real target method takes a trailing
    tick-delta float (shouldRender) or a second float after yaw
    (dispatcher render).
    """
    errors: list[str] = []
    if target == "renderer":
        if 'method = "shouldRender"' not in src:
            errors.append(f"{compat}: expected an @Inject on shouldRender")
        has_tick = "float tickDelta" in src
        if live_tick and not has_tick:
            errors.append(f"{compat}: game takes tickDelta but mixin omits it")
        if not live_tick and has_tick:
            errors.append(
                f"{compat}: game shouldRender has NO tickDelta but mixin declares "
                "'float tickDelta' -> InvalidInjectionException at startup"
            )
    else:
        if 'method = "render"' not in src:
            errors.append(f"{compat}: expected an @Inject on render")
        if "float yaw" not in src:
            errors.append(f"{compat}: dispatcher render mixin should declare 'float yaw'")
        has_two = "float tickDelta" in src
        if live_tick and not has_two:
            errors.append(f"{compat}: game render takes (yaw, tickDelta) but mixin omits tickDelta")
        if not live_tick and has_two:
            errors.append(f"{compat}: game render takes only yaw but mixin declares tickDelta")
    return errors


def check_mixin(compat: str, src: str, target: str, needs_tick: bool) -> list[str]:
    errors: list[str] = []
    if target == "renderer":
        if "@Mixin(EntityRenderer.class)" not in src:
            errors.append(f"{compat}: expected @Mixin(EntityRenderer.class)")
        if 'method = "shouldRender"' not in src:
            errors.append(f"{compat}: expected an @Inject on shouldRender")
        has_tick = "float tickDelta" in src
        if needs_tick and not has_tick:
            errors.append(f"{compat}: game takes tickDelta but mixin omits it")
        if not needs_tick and has_tick:
            errors.append(
                f"{compat}: game shouldRender has NO tickDelta but mixin declares "
                "'float tickDelta' -> InvalidInjectionException at startup"
            )
    else:
        if "@Mixin(EntityRenderDispatcher.class)" not in src:
            errors.append(f"{compat}: expected @Mixin(EntityRenderDispatcher.class)")
        if 'method = "render"' not in src:
            errors.append(f"{compat}: expected an @Inject on render")
        if "float yaw" not in src:
            errors.append(f"{compat}: dispatcher render mixin should declare 'float yaw'")
        wants_two = compat in DISPATCHER_NEEDS_TWO_FLOATS
        has_two = "float tickDelta" in src
        if wants_two and not has_two:
            errors.append(f"{compat}: game render takes (yaw, tickDelta) but mixin omits tickDelta")
        if not wants_two and has_two:
            errors.append(f"{compat}: game render takes only yaw but mixin declares tickDelta")
    return errors


def live_key_shape(mc_version: str, javap: str | None) -> str | None:
    """Return 'event' or 'glfw' for the real KeyboardHandler.keyPress shape.

    Returns None when the jar or javap is unavailable (caller falls back).
    keyPress is private on the newer targets, hence javap -p.
    """
    if javap is None:
        return None
    jar = find_game_jar(mc_version)
    if jar is None:
        return None
    text = javap_text(
        javap, jar, "net.minecraft.client.KeyboardHandler", private=True
    )
    m = re.search(r"keyPress\(long, int, (.*?)\)", text)
    if not m:
        return None
    return "event" if "KeyEvent" in m.group(1) else "glfw"


def check_keyboard(compat: str, src: str, shape: str) -> list[str]:
    """Assert the compat MixinKeyboardHandler matches its game's keyPress shape."""
    errors: list[str] = []
    if "@Mixin(KeyboardHandler.class)" not in src:
        errors.append(f"{compat}: expected @Mixin(KeyboardHandler.class)")
    imports_event = "import net.minecraft.client.input.KeyEvent" in src
    if shape == "event":
        if not imports_event:
            errors.append(f"{compat}: game keyPress takes KeyEvent but mixin lacks the import")
        if "keyPress(JIIII)V" in src:
            errors.append(
                f"{compat}: game keyPress is (long, int, KeyEvent) but mixin declares "
                "the five-int shape -> InvalidInjectionException at startup"
            )
        if "KeyEvent event" not in src:
            errors.append(f"{compat}: KeyEvent-shape handler should take 'KeyEvent event'")
    else:
        if "keyPress(JIIII)V" not in src:
            errors.append(f"{compat}: game keyPress is five-int but mixin lacks that descriptor")
        if imports_event:
            errors.append(f"{compat}: game has no KeyEvent but mixin imports it (won't compile)")
    return errors


def check_sound_mixin(src: str) -> list[str]:
    """Assert MixinLevelSound declares each playSound hook with the right flag.

    The required overload must hook without ``require = 0`` (a missing target
    must fail loudly); every optional overload must pass ``require = 0`` or a
    target that dropped it crashes startup with InvalidInjectionException.
    """
    errors: list[str] = []
    if "@Mixin(Level.class)" not in src:
        errors.append(f"{SOUND_MIXIN}: expected @Mixin(Level.class)")
    for key, desc, required in SOUND_HOOKS:
        needle = f'method = "{desc}"'
        if needle not in src:
            errors.append(f"{SOUND_MIXIN}: missing hook for {key} ({desc})")
            continue
        # The @Inject annotation owning this descriptor is the text between
        # this method="..." and the previous @Inject boundary.
        seg = src.split(needle)[0].rsplit("@Inject", 1)[-1]
        tail = src.split(needle, 1)[1].split("@Inject", 1)[0].split("private void", 1)[0]
        annot = seg + tail
        has_opt = re.search(r"require\s*=\s*0", annot) is not None
        if required and has_opt:
            errors.append(
                f"{SOUND_MIXIN}: required hook {key} passes require = 0 "
                "(a missing target must fail, not silently skip)"
            )
        if not required and not has_opt:
            errors.append(
                f"{SOUND_MIXIN}: optional hook {key} lacks require = 0 -> "
                "InvalidInjectionException on targets without it"
            )
    return errors


def live_sound_overloads(
    mc_version: str, javap: str | None
) -> tuple[set[str], list[str]] | None:
    """Return (present SoundEvent overload keys, unknown overloads) for Level.

    Returns None when the jar or javap is unavailable (caller falls back to
    SOUND_EXPECTED). Holder-carrying overloads are skipped: the mixin
    deliberately does not hook them.
    """
    if javap is None:
        return None
    jar = find_game_jar(mc_version)
    if jar is None:
        return None
    text = javap_text(javap, jar, "net.minecraft.world.level.Level")
    if "playSound(" not in text:
        return None
    present: set[str] = set()
    unknown: list[str] = []
    for m in re.finditer(r"playSound\((.*?)\)", text):
        sig = m.group(1)
        if "Holder" in sig:
            continue
        if "SoundEvent" not in sig:
            unknown.append(f"playSound({sig})")
            continue
        first_player = "player.Player" in sig
        if "BlockPos" in sig:
            present.add("player_blockpos" if first_player else "entity_blockpos")
        elif re.search(r"double, double, double", sig):
            if sig.count("float") >= 2:
                present.add("player_ddd" if first_player else "entity_ddd")
            else:
                present.add("player_ddd_novol" if first_player else "entity_ddd_novol")
        elif re.search(r"Entity, net\.minecraft\.sounds\.SoundEvent", sig):
            present.add("player_entity" if first_player else "entity_entity")
        else:
            unknown.append(f"playSound({sig})")
    return present, unknown


def check_sound_target(
    vdir: str, compat: str, sigs: set[str], unknown: list[str]
) -> list[str]:
    """Assert one target's game signatures match the mixin hooks exactly."""
    errors: list[str] = []
    for u in unknown:
        errors.append(
            f"{vdir}: game has unhooked Level.{u} (silent hole: add a hook "
            f"or document why in {SOUND_MIXIN})"
        )
    if "entity_blockpos" not in sigs:
        errors.append(
            f"{vdir}: required playSound(Entity, BlockPos, ...) missing from game "
            "(required hook will crash startup)"
        )
    hooked = {key for key, _, _ in SOUND_HOOKS}
    for s in sigs - hooked:
        errors.append(
            f"{vdir}: game has Level.playSound overload '{s}' with no mixin hook "
            "(silent hole)"
        )
    expected = SOUND_EXPECTED.get(compat)
    if expected is not None and sigs != set(expected):
        errors.append(
            f"{vdir}: game overloads {sorted(sigs)} != SOUND_EXPECTED table "
            f"{sorted(expected)} (update tools/verify_mixins.py)"
        )
    return errors


def main() -> int:
    use_live = "--no-live" not in sys.argv
    allow_no_flashback = "--no-flashback" in sys.argv
    javap = find_javap() if use_live else None
    if use_live and javap is None:
        print("note: javap not found; using hardcoded signature table", flush=True)

    failures: list[str] = []
    checked = 0
    seen_sound_sigs: set[str] = set()

    for vdir in sorted(VERSIONS.iterdir()):
        props_file = vdir / "gradle.properties"
        if not vdir.is_dir() or not props_file.exists():
            continue
        props = read_props(props_file)
        mc = props.get("mc_version", vdir.name)
        compat = props.get("compat", "")
        fb_jar = props.get("flashback_jar", "")
        checked += 1

        # 1. Compile dependency: the Flashback jar this target builds against.
        # With --no-flashback (public CI, where the proprietary jars cannot
        # exist) a missing jar warns instead of failing; signatures still check.
        if not fb_jar:
            failures.append(f"{vdir.name}: gradle.properties sets no flashback_jar")
        elif not (LIBS / fb_jar).exists():
            msg = f"{vdir.name}: missing compile jar libs/{fb_jar}"
            if allow_no_flashback:
                print(f"warn: {msg} (--no-flashback: signatures only)", flush=True)
            else:
                failures.append(msg)

        # 2. Game-test dependency: a Flashback jar in run/mods for runClient.
        mods = vdir / "run" / "mods"
        run_jar_names = [j.name for j in mods.glob("Flashback-*.jar")] if mods.exists() else []
        if not run_jar_names:
            msg = (f"{vdir.name}: no Flashback jar in versions/{vdir.name}/run/mods/ "
                "(runClient game-tests run without Flashback)")
            if allow_no_flashback:
                print(f"warn: {msg} (--no-flashback: signatures only)", flush=True)
            else:
                failures.append(msg)
        elif fb_jar and not any(j.lower() == fb_jar.lower() for j in run_jar_names):
            print(
                f"warn: {vdir.name}: libs/{fb_jar} != run/mods "
                f"{sorted(run_jar_names)} (compile/test skew; "
                "update when Flashback releases matter)",
                flush=True,
            )

        # 3. Mixin descriptor check. The live game jar is the source of truth;
        # the EXPECTED table is the fallback when no Loom cache is available.
        mixin = COMPAT / compat / "java/pdx/dev/fbactors/mixin/MixinEntityRenderer.java"
        if not compat:
            failures.append(f"{vdir.name}: gradle.properties sets no compat dir")
            continue
        if not mixin.exists():
            failures.append(f"{vdir.name}: missing {mixin.relative_to(ROOT)}")
            continue
        src = mixin.read_text(encoding="utf-8")

        if "@Mixin(EntityRenderDispatcher.class)" in src:
            mixin_target = "dispatcher"
        elif "@Mixin(EntityRenderer.class)" in src:
            mixin_target = "renderer"
        else:
            failures.append(f"{vdir.name}: {compat} mixin targets neither renderer class")
            continue
        if compat in EXPECTED and EXPECTED[compat][0] != mixin_target:
            failures.append(
                f"{vdir.name}: {compat} mixin targets {mixin_target} but EXPECTED "
                f"table says {EXPECTED[compat][0]} (update tools/verify_mixins.py)"
            )
            continue

        live_n = live_floats(mc, javap, mixin_target) if use_live else None
        if live_n is not None:
            if mixin_target == "renderer":
                # shouldRender(E, Frustum, DDD) -> 0 floats; (..., DDD, F) -> 1
                live_tick, origin = live_n > 0, "javap"
            else:
                # render(E, DDD, yaw, ...) -> 1 float; (yaw, tickDelta, ...) -> 2
                live_tick, origin = live_n >= 2, "javap"
            errs = check_mixin_live(compat, src, mixin_target, live_tick)
        elif compat in EXPECTED:
            target, needs_tick = EXPECTED[compat]
            origin = "table"
            errs = check_mixin(compat, src, target, needs_tick)
            live_tick = needs_tick or (target == "dispatcher"
                                       and compat in DISPATCHER_NEEDS_TWO_FLOATS)
        else:
            failures.append(f"{vdir.name}: unknown compat '{compat}' (add to EXPECTED)")
            continue

        if errs:
            failures.extend(f"{vdir.name}: {e}" for e in errs)
        else:
            if mixin_target == "dispatcher":
                shape = "yaw+tickDelta" if live_tick else "yaw only"
            else:
                shape = "with tickDelta" if live_tick else "no tickDelta"
            print(f"ok: {vdir.name} renderer (mc {mc}, {compat} -> {mixin_target} {shape}) [{origin}]", flush=True)

        # 4. Keyboard hook check. Same live-jar source of truth, KEYBOARD table fallback.
        kb = COMPAT / compat / "java/pdx/dev/fbactors/mixin/MixinKeyboardHandler.java"
        if not kb.exists():
            failures.append(f"{vdir.name}: missing {kb.relative_to(ROOT)}")
            continue
        kbsrc = kb.read_text(encoding="utf-8")
        live_key = live_key_shape(mc, javap) if use_live else None
        if live_key is not None:
            key_shape, key_origin = live_key, "javap"
        elif compat in KEYBOARD:
            key_shape, key_origin = KEYBOARD[compat], "table"
        else:
            failures.append(f"{vdir.name}: unknown compat '{compat}' (add to KEYBOARD)")
            continue
        kerrs = check_keyboard(compat, kbsrc, key_shape)
        if kerrs:
            failures.extend(f"{vdir.name}: {e}" for e in kerrs)
        else:
            print(f"ok: {vdir.name} keyboard (mc {mc}, {compat} -> keyPress {key_shape}) [{key_origin}]", flush=True)

        # 5. Sound-signature check. Same live-jar source of truth,
        # SOUND_EXPECTED table fallback. The mixin source itself is validated
        # once below; here each target's game jar must carry the required
        # overload and exactly the optional set the table predicts.
        live_snd = live_sound_overloads(mc, javap) if use_live else None
        if live_snd is not None:
            sigs, unknown, snd_origin = live_snd[0], live_snd[1], "javap"
        elif compat in SOUND_EXPECTED:
            sigs, unknown, snd_origin = set(SOUND_EXPECTED[compat]), [], "table"
        else:
            failures.append(f"{vdir.name}: unknown compat '{compat}' (add to SOUND_EXPECTED)")
            continue
        serrs = check_sound_target(vdir.name, compat, sigs, unknown)
        if serrs:
            failures.extend(serrs)
        else:
            print(
                f"ok: {vdir.name} sound (mc {mc}, {compat} -> "
                f"{len(sigs)} overloads: {', '.join(sorted(sigs))}) [{snd_origin}]",
                flush=True,
            )
        seen_sound_sigs.update(sigs)

    # 6. Shared MixinLevelSound hook check: every descriptor present with the
    # right require flag, and no require = 0 hook names a method that exists
    # on none of the checked targets (dead hook hiding a descriptor typo).
    snd_mixin = ROOT / SOUND_MIXIN
    if not snd_mixin.exists():
        failures.append(f"missing {SOUND_MIXIN}")
    else:
        snd_src = snd_mixin.read_text(encoding="utf-8")
        merrs = check_sound_mixin(snd_src)
        if merrs:
            failures.extend(merrs)
        else:
            print(f"ok: sound mixin hooks ({len(SOUND_HOOKS)} descriptors) [source]", flush=True)
        for key, desc, required in SOUND_HOOKS:
            if not required and key not in seen_sound_sigs:
                failures.append(
                    f"{SOUND_MIXIN}: require = 0 hook {key} matches no checked "
                    "target (typo'd descriptor hiding behind require = 0?)"
                )

    print(f"checked {checked} version target(s)", flush=True)
    if failures:
        print("FAIL:", flush=True)
        for f in failures:
            print(f"  - {f}", flush=True)
        return 1
    print("all mixin signatures and Flashback jars verified", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
