import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INTEGER_TARGET = "Lnet/minecraft/server/level/ServerEntity;updateInterval:I"
SCHEDULE_TARGET = "Lnet/minecraft/world/entity/UpdateInterval;test(I)Z"


def cadence_sites(bytecode):
    match = re.search(r"public void sendChanges\(\);(.*?)(?=\n  (?:public|private|protected) |\Z)", bytecode, re.S)
    if match is None:
        raise ValueError("ServerEntity.sendChanges() is missing")
    body = match.group(1)
    integer = body.count("Field updateInterval:I")
    scheduled = body.count("UpdateInterval.test:(I)Z")
    if bool(integer) == bool(scheduled):
        raise ValueError("Expected exactly one supported packet cadence mechanism")
    return integer, scheduled


def check_source(source, registered):
    failures = []
    for target in (INTEGER_TARGET, SCHEDULE_TARGET):
        if target not in source:
            failures.append("Missing packet cadence target: " + target)
    if source.count('@Group(name = "fbactors$packetCadence", min = 1)') != 2:
        failures.append("Both cadence hooks must share a required injection group")
    if 'this.entity instanceof ActorClonePlayer' not in source:
        failures.append("Packet cadence must be scoped to actor clones")
    if "MixinActorPacketTracker" not in registered:
        failures.append("Packet tracker mixin is not registered")
    return failures


def verify(live=True):
    source = (ROOT / "src/main/java/pdx/dev/fbactors/mixin/MixinActorPacketTracker.java").read_text()
    registered = json.loads((ROOT / "src/main/resources/fbactors.mixins.json").read_text())["client"]
    failures = check_source(source, registered)
    if live:
        import verify_mixins as signatures
        javap = signatures.find_javap()
        if not javap:
            return failures + ["javap is required to verify native actor packet hooks"]
        for folder in sorted((ROOT / "versions").iterdir()):
            props_file = folder / "gradle.properties"
            if not props_file.exists():
                continue
            props = signatures.read_props(props_file)
            jar = signatures.find_game_jar(props["mc_version"])
            if jar is None:
                failures.append(folder.name + ": missing mapped game jar")
                continue
            try:
                code = subprocess.check_output([javap, "-p", "-c", "-classpath", str(jar),
                    "net.minecraft.server.level.ServerEntity"], text=True)
                sites = cadence_sites(code)
                print(f"ok: {folder.name} actor packet cadence ({sites[0]} integer, {sites[1]} scheduled sites)", flush=True)
            except (ValueError, subprocess.CalledProcessError) as error:
                failures.append(folder.name + ": " + str(error))
    return failures


if __name__ == "__main__":
    failures = verify("--no-live" not in sys.argv)
    for failure in failures:
        print("FAIL: " + failure)
    sys.exit(1 if failures else 0)
