"""Verify custom-model dispatcher hooks against each mapped Minecraft jar."""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def verify(live=True):
    import verify_mixins as signatures
    failures = []
    registered = json.loads((ROOT / "src/main/resources/fbactors.mixins.json").read_text())["client"]
    if "MixinCustomMobRenderer" not in registered:
        failures.append("Custom model dispatcher mixin is not registered")
    for folder in sorted((ROOT / "versions").iterdir()):
        props_file = folder / "gradle.properties"
        if not props_file.exists():
            continue
        props = signatures.read_props(props_file)
        source = ROOT / "compat" / props["compat"] / "java/pdx/dev/fbactors/mixin/MixinCustomMobRenderer.java"
        if not source.exists():
            failures.append(folder.name + ": missing custom model dispatcher adapter")
            continue
        if not live:
            continue
        jar, javap = signatures.find_game_jar(props["mc_version"]), signatures.find_javap()
        if jar is None or javap is None:
            failures.append(folder.name + ": game jar/javap required for custom models")
            continue
        result = subprocess.run([str(javap), "-p", "-s", "-c", "-classpath", str(jar),
            "net.minecraft.client.renderer.entity.EntityRenderDispatcher"], capture_output=True, text=True, timeout=60)
        code = result.stdout
        entity = "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"
        state = "(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"
        context = "EntityRenderers.createEntityRenderers:(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"
        if result.returncode or entity not in code or code.count(context) != 1:
            failures.append(folder.name + ": entity lookup / renderer context hook changed")
        if props["compat"] not in ("mc1211", "mc1214") and state not in code:
            failures.append(folder.name + ": render-state dispatcher hook changed")
        print("ok: " + folder.name + " custom model renderer hooks", flush=True)
    return failures
