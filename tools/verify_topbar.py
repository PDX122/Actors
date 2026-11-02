"""Check that the addon insertion point precedes Flashback's final Hide item."""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANCHOR = "flashback.hide_replay_ui"


def verify(live=True):
    failures = []
    mixin = (ROOT / "src/main/java/pdx/dev/fbactors/mixin/MixinEditorMenuBar.java").read_text()
    if 'stringValue=' + ANCHOR not in mixin:
        failures.append("Addon menus must be inserted before Flashback's Hide control")
    if 'StationWindow.render()' in mixin or 'EditorWindows.render()' in mixin:
        failures.append("Dockable windows must render after Flashback creates its dockspace")
    visuals = (ROOT / "src/main/java/pdx/dev/fbactors/mixin/MixinVisualsWindow.java").read_text()
    overlay = (ROOT / "src/main/java/pdx/dev/fbactors/mixin/MixinReplayUI.java").read_text()
    station = (ROOT / "src/main/java/pdx/dev/fbactors/station/StationWindow.java").read_text()
    if not any(call in visuals for call in ('StationWindow.render()', 'StationWindow::render')) or 'Actors Studio###Visuals' not in station:
        failures.append("Actors Studio must reuse Flashback's Visuals window and saved dock ID")
    if 'TimelineWindow;render()V' not in overlay or 'At.Shift.AFTER' not in overlay or 'EditorWindows.render()' not in overlay:
        failures.append("Addon windows must render in Flashback's panel phase after Timeline")
    if not live:
        return failures
    from verify_mixins import find_javap, read_props
    javap = find_javap()
    if javap is None:
        return failures + ["javap is required to verify the topbar insertion point"]
    checked = set()
    for folder in sorted((ROOT / "versions").iterdir()):
        props_file = folder / "gradle.properties"
        if not props_file.exists():
            continue
        props = read_props(props_file)
        jars = [ROOT / "libs" / props["flashback_jar"]]
        jars += list((folder / "run/mods").glob("[Ff]lashback-*.jar"))
        for jar in jars:
            if jar in checked:
                continue
            checked.add(jar)
            if not jar.exists():
                failures.append("Topbar compile jar missing: " + jar.name)
                continue
            result = subprocess.run([javap, "-classpath", str(jar), "-p", "-c",
                "com.moulberry.flashback.editor.ui.windows.MainMenuBar"],
                capture_output=True, text=True, timeout=60)
            code = result.stdout
            if result.returncode or code.count("// String " + ANCHOR) != 1:
                failures.append(jar.name + ": expected one Hide insertion point")
                continue
            tail = code[code.index("// String " + ANCHOR):]
            calls = re.findall(r"ImGui\.(menuItem|beginMenu):", tail)
            if calls != ["menuItem"]:
                failures.append(jar.name + ": Hide must remain the last topbar item")
            replay = subprocess.run([javap, "-classpath", str(jar), "-p", "-c",
                "com.moulberry.flashback.editor.ui.ReplayUI"],
                capture_output=True, text=True, timeout=60)
            code = replay.stdout
            positions = [code.find(name) for name in ("ImGui.dockSpaceOverViewport:", "VisualsWindow.render:", "TimelineWindow.render:")]
            if replay.returncode or min(positions) < 0 or positions[0] >= min(positions[1:]):
                failures.append(jar.name + ": editor panels must follow dockspace creation")
        print("ok: " + folder.name + " topbar Hide stays last", flush=True)
    return failures
