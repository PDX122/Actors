"""Merge supplied ICBM audio byte-for-byte. Never import plane or unrelated scene sounds."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--overlay", type=Path)
parser.add_argument("--fallback-dir", type=Path, help="Previously extracted original Ogg clips if the earlier archive was moved")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
names = ["alert_klaxon", "silo_door_open", "launch", "reentry_scream", "detonation_close", "detonation_distant"]
destination = root / "src/main/resources/assets/fbactors/sounds/events/icbm"
destination.mkdir(parents=True, exist_ok=True)
manifest = {}
archive = zipfile.ZipFile(args.archive) if args.archive.is_file() else None
try:
    for name in names:
        member = f"icbm_sound_pack/ogg/icbm_{name}.ogg"
        if archive:
            data = archive.read(member)
        elif args.fallback_dir:
            data = (args.fallback_dir / f"icbm_{name}.ogg").read_bytes()
        else:
            raise FileNotFoundError(args.archive)
        if not data.startswith(b"OggS") or len(data) > 4_000_000:
            raise ValueError(f"Invalid ICBM audio: {name}")
        (destination / f"{name}.ogg").write_bytes(data)
        manifest[name] = {"archive": args.archive.name, "file": member, "sha256": hashlib.sha256(data).hexdigest()}
finally:
    if archive:
        archive.close()
if args.overlay:
    # Fixed allowlist: Engine_2149981270 and Flying_142317661 are plane sounds,
    # never substitutes for the missing missile Engine_167570993.
    selected = {
        "launch": "Launch_3106177504.ogg", "reentry_scream": "Incoming_6916316612.ogg",
        "detonation_close": "MainExplosion2_1391727615.ogg",
        "detonation_distant": "Explode_130768997.ogg",
        "silo_door_open": "Moving_2233170723.ogg", "engine_sustain": "Launch_3106177504.ogg",
        "stage_cap": "Cap_9057591742.ogg", "stage_deploy": "Deploy_9056323321.ogg",
        "stage_clank": "Clank_3165576478.ogg", "ignition": "Initiate_771710038.ogg",
        "prepare_windup": "WindUp_5489013969.ogg", "prepare_stopped": "Stopped_2229031168.ogg", "impact_bass": "Bass_8406997653.ogg",
        "impact_crackle": "Crackling_8234114045.ogg", "impact_wind": "Wind2_5113213873.ogg",
    }
    # Preserve the earlier sustained launch as the missing missile engine fallback.
    (destination / "engine_sustain.ogg").write_bytes((destination / "launch.ogg").read_bytes())
    manifest["engine_sustain"] = dict(manifest["launch"], fallback="Missile Engine_167570993 is missing in the newer pack.")
    with zipfile.ZipFile(args.overlay) as overlay:
        import csv
        import io
        rows = list(csv.DictReader(io.StringIO(overlay.read("icbm_audio/manifest.csv").decode("utf-8-sig"))))
        allowed = {r["file"] for r in rows if r["status"] == "OK" and r["category"] in {"Missile", "Missile - Thruster", "Explosion", "Silo - Launch Mechanism"}}
        for name, file in selected.items():
            member = "icbm_audio/" + file
            if file not in allowed or member not in overlay.namelist():
                if name in manifest:
                    manifest[name]["fallback"] = "Newer-pack replacement unavailable. Retained earlier clip."
                continue
            data = overlay.read(member)
            if not data.startswith(b"OggS") or len(data) > 4_000_000:
                raise ValueError(f"Invalid ICBM overlay audio: {name}")
            (destination / f"{name}.ogg").write_bytes(data)
            manifest[name] = {"archive": args.overlay.name, "file": member, "sha256": hashlib.sha256(data).hexdigest()}
    names = list(manifest)
    manifest["alert_klaxon"]["fallback"] = "New Siren_6456981311 is missing; retained the earlier alert."
    if manifest["engine_sustain"]["archive"] == args.overlay.name:
        manifest["engine_sustain"]["role_note"] = "Available missile launch used for sustained boost; missing missile Engine_167570993 is not substituted with plane audio."
    if manifest["silo_door_open"]["archive"] == args.overlay.name:
        manifest["silo_door_open"]["role_note"] = "Available silo launch mechanism movement replaces the missing hatch clips."
(destination / "source.json").write_text(json.dumps({"source": "User-supplied ICBM sound archives", "adaptation": "Ogg audio copied byte-for-byte. Namespace, gain, pitch and playback timing configured by the mod. Plane assets excluded.", "files": manifest}, indent=2) + "\n", encoding="utf-8")
sounds = {f"icbm.{name}": {"sounds": [{"name": f"fbactors:events/icbm/{name}", "stream": False}]} for name in names}
(root / "src/main/resources/assets/fbactors/sounds.json").write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")
print(f"Imported {len(names)} original ICBM clips.")
