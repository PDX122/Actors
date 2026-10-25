import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def verify(live=True):
    failures = []
    assets = ROOT / "src/main/resources/assets/fbactors/filters"
    catalog = json.loads((assets / "catalog.json").read_text())
    ids = [entry["id"] for entry in catalog]
    if len(ids) != len(set(ids)) or len(ids) != 44:
        failures.append("Look catalog must contain 44 distinct filters")
    if any(entry["origin"] != "Actors" for entry in catalog):
        failures.append("Every look must be an original Actors shader")
    if {p.stem for p in assets.glob("*.fsh")} != set(ids + ["compose", "actor_mosaic", "actor_extent"]):
        failures.append("Shader assets must exactly match the original collection")
    for name in ids + ["compose", "actor_mosaic", "actor_extent"]:
        if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
            failures.append("Invalid shader id: " + name)
            continue
        file = assets / (name + ".fsh")
        if not file.exists():
            failures.append("Missing shader: " + name)
        elif "void main(" not in file.read_text():
            failures.append("Shader entry point is missing: " + name)
    registered = json.loads((ROOT / "src/main/resources/fbactors.mixins.json").read_text())["client"]
    for name in ["MixinActorLookContext", "MixinActorLookMaterials", "MixinActorMosaicDraw"]:
        if name not in registered:
            failures.append("Actor look mixin is not registered: " + name)
    if "MixinLookRenderer" not in registered:
        failures.append("Look renderer mixin is not registered")
    import verify_mixins as signatures
    for folder in sorted((ROOT / "versions").iterdir()):
        props_file = folder / "gradle.properties"
        if not props_file.exists():
            continue
        props = signatures.read_props(props_file)
        adapter = ROOT / "compat" / props["compat"] / "java/pdx/dev/fbactors/filters/FilterRenderBridge.java"
        if not adapter.exists():
            failures.append(folder.name + ": missing render target adapter")
        context = ROOT / "compat" / props["compat"] / "java/pdx/dev/fbactors/mixin/MixinActorLookContext.java"
        materials = context.with_name("MixinActorLookMaterials.java")
        bridge = adapter.with_name("ActorLookBridge.java")
        draw_hook = context.with_name("MixinActorMosaicDraw.java")
        for required in [context, materials, bridge, draw_hook]:
            if not required.exists():
                failures.append(folder.name + ": missing actor look adapter " + required.name)
        if live:
            jar = signatures.find_game_jar(props["mc_version"])
            javap = signatures.find_javap()
            if jar is None or javap is None:
                failures.append(folder.name + ": mapped game jar and javap are required")
                continue
            import subprocess
            draw_source = draw_hook.read_text()
            draw_class = re.search(r'Mixin\(targets = "([^"]+)"', draw_source).group(1)
            draw_code = subprocess.run([str(javap), "-classpath", str(jar), "-p", "-c", draw_class],
                capture_output=True, text=True, check=True).stdout
            calls = re.findall(r'target = "L([^;]+);([^"(]+)([^"\n]+)"', draw_source)
            if not calls:
                failures.append(folder.name + ": mosaic draw hooks are missing")
            for owner, name, descriptor in calls:
                if owner + "." + name + ":" + descriptor not in draw_code:
                    failures.append(folder.name + ": mosaic draw call is missing: " + name)
            for method in re.findall(r'WrapOperation\(method = "([^"]+)"', draw_source):
                if not re.search(r"\b" + method + r"\(", draw_code):
                    failures.append(folder.name + ": mosaic draw container is missing: " + method)
            print("ok: " + folder.name + " mosaic capture draw hooks", flush=True)
            renderer = signatures.javap_text(javap, jar, "net.minecraft.client.renderer.entity.LivingEntityRenderer", True)
            import subprocess
            descriptors = subprocess.run([str(javap), "-classpath", str(jar), "-p", "-s",
                "net.minecraft.client.renderer.entity.LivingEntityRenderer"], capture_output=True, text=True, check=True).stdout
            wrapped = re.search(r'WrapMethod\(method = "([^"(]+)([^"\n]+)"', context.read_text())
            if wrapped is None or "descriptor: " + wrapped.group(2) not in descriptors:
                failures.append(folder.name + ": actor render wrapper descriptor does not match")
            method = "render" if props["compat"] in ["mc1211", "mc1214", "mc1215", "mc1218"] else "submit"
            if not re.search(r"public void " + method + r"\(", renderer):
                failures.append(folder.name + ": actor render context hook is missing")
            if props["compat"] != "mc1211" and "extractRenderState(T, S, float);" not in renderer:
                failures.append(folder.name + ": actor render state hook is missing")
            factory = "net.minecraft.client.renderer.rendertype.RenderTypes" if props["compat"] in ["mc12111", "mc261", "mc262", "mc263"] else "net.minecraft.client.renderer.RenderType"
            factories = signatures.javap_text(javap, jar, factory, True)
            names = re.search(r"method = \{([^}]+)\}", materials.read_text()).group(1)
            for name in re.findall(r'"([A-Za-z]+)"', names):
                if not re.search(r"\b" + name + r"\(net\.minecraft\.resources\.", factories):
                    failures.append(folder.name + ": actor material hook missing: " + name)
            print("ok: " + folder.name + " actor context, state and material hooks", flush=True)
            code = signatures.javap_text(javap, jar, "net.minecraft.client.renderer.GameRenderer", True)
            if not re.search(r"public void renderLevel\((?:net.minecraft.client.DeltaTracker)?\);", code):
                failures.append(folder.name + ": unsupported world render method")
            elif "public void close();" not in code:
                failures.append(folder.name + ": renderer close method is missing")
            else:
                print("ok: " + folder.name + " look render and cleanup hooks", flush=True)
    return failures


if __name__ == "__main__":
    failures = verify("--no-live" not in sys.argv)
    for failure in failures:
        print("FAIL: " + failure)
    sys.exit(1 if failures else 0)
