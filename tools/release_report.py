"""Build a release evidence index and contact sheets from actual harness output."""
from pathlib import Path
import json
import subprocess
import shutil
import os
import csv
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
QA = Path(os.environ.get("FBACTORS_QA_ROOT", str(ROOT / "build/release-qa")))
VERSIONS = ("1.21.1", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1", "26.2", "26.3")


def text(path):
    return path.read_text(encoding="utf-8-sig", errors="replace") if path.exists() else ""


def fresh(path, start):
    return path.exists() and path.stat().st_mtime >= start


def sheets(directory, images, prefix):
    for page in range(0, len(images), 18):
        batch = images[page:page + 18]
        canvas = Image.new("RGB", (1200, ((len(batch) + 2) // 3) * 280), "#11151a")
        draw = ImageDraw.Draw(canvas)
        for i, image in enumerate(batch):
            x, y = (i % 3) * 400, (i // 3) * 280
            label = image.stem if image.parent.name == 'export-frames' else image.parent.name
            draw.text((x + 8, y + 6), label[:50], fill="white")
            with Image.open(image) as source:
                source = source.convert("RGB")
                source.thumbnail((392, 252))
                canvas.paste(source, (x + 4, y + 24))
        canvas.save(directory / f"{prefix}-{page // 18 + 1:02}.jpg", quality=90)


def report():
    QA.mkdir(parents=True, exist_ok=True)
    versions = json.loads((QA / 'run.json').read_text())['versions'] if (QA / 'run.json').exists() else VERSIONS
    lines = ["# Release QA evidence", "", "**Batch evidence collected. Review visual-quality.json and the comparison gallery before release sign-off.**", "",
             "Physical audio and authenticated skin-service checks require an environment with those services available. Conversion evidence is recorded separately from native replay results.", "",
             "Generated from saved harness verdicts, JUnit XML, framebuffer captures and decoded video metadata.", "",
             "A client PASS requires fresh recording and export evidence as well as completed screenshots. Visual approval is recorded separately.", "",
             "| Minecraft target | Unit tests | Replay harness | Recording | Camera MP4 | UI captures | Visual issues |", "|---|---:|---|---|---|---:|---:|"]
    data = []
    for version in versions:
        directory = QA / version
        if not directory.exists():
            lines.append(f"| {version} | pending | pending | pending | pending | 0 | pending |")
            continue
        start = (directory / "fixture.zip").stat().st_mtime if (directory / "fixture.zip").exists() else float("inf")
        unit = []
        unit_pending = False
        for result in (ROOT / "versions" / version / "build/test-results/test").glob("TEST-*.xml"):
            try:
                unit.append(ET.parse(result).getroot())
            except (OSError, ET.ParseError):
                unit_pending = True
        count = sum(int(x.attrib["tests"]) for x in unit)
        failures = sum(int(x.attrib["failures"]) + int(x.attrib["errors"]) for x in unit)
        verdict = text(directory / "verdict.txt").splitlines()
        verdict = verdict[0].replace("VERDICT=", "") if verdict else "pending"
        recording = text(directory / "recording.txt") if fresh(directory / "recording.txt", start) else "pending"
        export = text(directory / "camera-export.txt") if fresh(directory / "camera-export.txt", start) else "pending"
        manifest = directory / "visuals/manifest.txt"
        names = text(manifest).splitlines()[1:] if fresh(manifest, start) else []
        images = [directory / "visuals" / f"{i:03}-{name}" / "editor-ui.png" for i, name in enumerate(names)]
        images = [p for p in images if fresh(p, start)]
        if images:
            sheets(directory, images, "ui-sheet")
        video = directory / "camera-modes.mp4"
        metadata = None
        video_issues = []
        if fresh(video, start) and video.stat().st_size > 1024 and shutil.which("ffprobe"):
            probe = subprocess.run(["ffprobe", "-v", "error", "-count_frames", "-show_streams", "-of", "json", str(video)], capture_output=True, text=True)
            if probe.returncode == 0:
                metadata = json.loads(probe.stdout)
                (directory / "video-metadata.json").write_text(probe.stdout, encoding="utf-8")
                frames = directory / "export-frames"
                frames.mkdir(exist_ok=True)
                subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(video), "-vf", "select=eq(mod(n\\,4)\\,2)", "-fps_mode", "vfr", str(frames / "mode-%02d.png")], check=True)
                decoded=sorted(frames.glob("mode-*.png"))
                with (directory/'camera-export.csv').open() as trace:
                    expected_modes=len({row['mode'] for row in csv.DictReader(trace)})
                stream=next(s for s in metadata['streams'] if s['codec_type']=='video')
                fixture=directory/'camera-export-fixture.json'
                # Earlier evidence explicitly requested an end tick one tick shorter.
                expected_frames=json.loads(fixture.read_text())['expectedFrames'] if fixture.exists() else expected_modes*4-1
                if stream['codec_name']!='h264' or (stream['width'],stream['height'])!=(640,360) or int(stream['nb_read_frames'])!=expected_frames:
                    video_issues.append('Encoded video does not match the camera fixture')
                if len(decoded)!=expected_modes:
                    video_issues.append('Missing decoded camera mode frames')
                for frame in decoded:
                    with Image.open(frame) as image:
                        colors=image.convert('RGB').resize((160,90)).getcolors(14400) or []
                        if len(colors)<20 or sum(n for n,c in colors if max(c)<8)>14400*.95:
                            video_issues.append(f'{frame.name}: blank camera export frame')
                sheets(directory, decoded, "export-sheet")
        if metadata is None:
            video_issues.append('No decoded video evidence')
        state = {"version": version, "unit_tests": count, "unit_failures": failures, "unit_results_pending": unit_pending, "client": verdict,
                 "recording": recording.split(":")[0], "export": export.split(":")[0], "screenshots": len(images), "video": metadata}
        visual = directory / 'visual-quality.json'
        state['visual_issues'] = len(json.loads(visual.read_text())['issues']) if visual.exists() else None
        state['video_issues'] = video_issues
        data.append(state)
        unit_summary = 'pending (results being written)' if unit_pending else f'{count}, {failures} failures'
        lines.append(f"| {version} | {unit_summary} | {verdict} | {state['recording']} | {state['export']} | {len(images)} | {state['visual_issues']} |")
    for directory in sorted(QA.glob('*-converted')):
        verdict=text(directory/'verdict.txt').splitlines()
        lines += ['', '## ReplayMod conversion fixture', '',
                  f"{directory.name}: {verdict[0] if verdict else 'pending'}. See conversion.txt, camera-export.txt and the visual captures in that folder.", '']
    for directory in sorted(QA.glob('*-boundary')):
        verdict=text(directory/'verdict.txt').splitlines()
        fixture=directory/'camera-export-fixture.json'
        expected=json.loads(fixture.read_text())['expectedFrames'] if fixture.exists() else 'pending'
        lines += ['', '## Export boundary regression', '',
                  f"{directory.name}: {verdict[0] if verdict else 'pending'}. Expected {expected} frames for all fourteen camera clips; decoded video evidence is saved with the supplemental run.", '']
    lines += ["", "## Coverage", "",
              "- Every target: unit tests across actor simulation, camera math and export timing, take editing and storage, undo, emotes, morphs, equipment, skin lookup validation, voice data, conversion, timeline, UI geometry and failure recovery.",
              "- Real replay harness: standing/walking/flying actor playback, morph render state, gear override, emote resolution, clone identity and recorded path continuity.",
              "- Real recorder: setup confirmation, countdown, scripted performer movement and crouch/sprint inputs, item-use capture, recording stop, take binary roundtrip and sharing bundle roundtrip.",
              "- Real export: H.264 MP4, nonzero start tick, fourteen camera clips and accurate-position telemetry. ffprobe checks the encoded stream and ffmpeg extracts a frame for each mode.",
              "- UI framebuffer captures: nine settings pages, cameras and timeline, boat handling, looks, effects, actor setup, skins, actor morph picker, body/action combinations, subtitles, themes, changelog, credits and deliberate recovery. Animated actions retain a second frame.",
              "- Separate GPU suites: all look shaders through OpenGL and native Vulkan, alpha/state restoration, actor masking and preview geometry.",
              "", "## Coverage limits", "",
              "These checks do not prove physical microphone capture, audible output, live authenticated skin services, arbitrary third-party mod combinations, every custom user model/shader, or mouse drag and keyboard interaction end to end. Scripted calls and unit tests cover the underlying handlers; opening a window is not a successful interaction test.",
              "", "A complete production sign-off requires visual review of the contact sheets and explicit disposition of these gaps. No automatic production-ready verdict is inferred from compilation or screenshots alone.", ""]
    (QA / "report.md").write_text("\n".join(lines), encoding="utf-8")
    (QA / "evidence.json").write_text(json.dumps(data, indent=2), encoding="utf-8")
    print("Wrote", QA / "report.md")
    return int(len(data)!=len(versions) or any(s['client']!='PASS' or s['unit_failures'] or s['unit_results_pending'] or not s['unit_tests'] or s['visual_issues'] or s['video_issues'] for s in data))


if __name__ == "__main__":
    raise SystemExit(report())
