"""Build once, run independent game clients concurrently, then collect all evidence."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from datetime import datetime, timezone
import argparse
import ctypes
import json
import os
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent.parent
VERSIONS = ['1.21.1', '1.21.4', '1.21.5', '1.21.8', '1.21.10', '1.21.11', '26.1', '26.2', '26.3']


def capacity():
    class Memory(ctypes.Structure):
        _fields_ = [('length', ctypes.c_ulong), ('load', ctypes.c_ulong)] + [
            (name, ctypes.c_ulonglong) for name in ['physical', 'available', 'page', 'available_page', 'virtual', 'available_virtual', 'extended']]
    memory = Memory()
    memory.length = ctypes.sizeof(memory)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory))
    available_gib = memory.available / 1024**3
    # Reserve room for the desktop, the Gradle daemon and image processing.
    return max(1, min(4, (os.cpu_count() or 4) // 4, int(max(3, available_gib - 2) // 3)))


def quality(directory):
    from PIL import Image, ImageChops
    manifest = directory / 'visuals/manifest.txt'
    if not manifest.exists():
        return {'issues': ['No completed screenshot manifest'], 'images': 0, 'motion_pairs': 0}
    names = manifest.read_text().splitlines()[1:]
    issues, images, pairs, stability = [], [], 0, []
    ui_frames, ui_max_change = 0, 0.0
    for index, name in enumerate(names):
        path = directory / 'visuals' / f'{index:03}-{name}' / 'editor-ui.png'
        if not path.exists():
            issues.append(f'{name}: missing image')
            continue
        with Image.open(path) as original:
            image = original.convert('RGB')
            samples = image.resize((160, 100))
            colors = samples.getcolors(16000) or []
            black = sum(n for n, c in colors if max(c) < 8) / 16000
            if black > .95 or len(colors) < 20:
                issues.append(f'{name}: empty or unreadable frame')
            if name.startswith('morph-') and not name.startswith('morph-catalog-'):
                model = image.crop((560, 280, 660, 380))
                values = model.getcolors(model.width * model.height) or []
                dominant = max((n for n, c in values), default=0)
                bright = sum(n for n, c in values if max(c) > 70)
                if model.width * model.height - dominant < 25 or bright < 25:
                    issues.append(f'{name}: empty inspector preview')
            if name.startswith('morph-catalog-'):
                for card,(x,y) in enumerate(((44,198),(246,198),(44,350),(246,350))):
                    label=image.crop((x+3,y+115,x+190,y+140))
                    if sum(n for n,c in (label.getcolors(label.width*label.height) or []) if min(c)>120)<5:
                        continue  # Empty slot on the final catalog page.
                    model=image.crop((x+20,y+5,x+174,y+110))
                    values=model.getcolors(model.width*model.height) or []
                    if model.width*model.height-max((n for n,c in values),default=0)<25:
                        issues.append(f'{name}: empty catalog card {card+1}')
            if name.startswith('morph-ui-'):
                frames=sorted(path.parent.glob('sequence/*/editor-ui.png'))
                ui_frames+=len(frames)
                if len(frames)!=90:issues.append(f'{name}: expected 90 UI frames, got {len(frames)}')
                previous=None
                for frame in frames:
                    with Image.open(frame) as source:
                        menu=source.convert('RGB').crop((12,36,770,182))
                        model=source.convert('RGB').crop((510,260,690,360))
                    if name=='morph-ui-stability' and previous is not None:
                        changed=sum(n for n,c in (ImageChops.difference(previous,menu).getcolors(menu.width*menu.height) or []) if max(c)>50)/(menu.width*menu.height)
                        ui_max_change=max(ui_max_change,changed)
                        if changed>.02:issues.append(f'{name}: menu flicker in {frame.parent.name} ({changed:.1%} pixels)')
                    previous=menu
                    if sum(n for n,c in (model.getcolors(model.width*model.height) or []) if max(c)>70)<25:
                        issues.append(f'{name}: missing inspector model in {frame.parent.name}')
            if name == 'morph-world-stability':
                frames=sorted(path.parent.glob('world-*.png'))
                if len(frames)!=12:
                    issues.append(f'{name}: expected 12 world frames, got {len(frames)}')
                previous=None
                for frame in frames:
                    with Image.open(frame) as source:
                        world=source.convert('RGB').resize((320,180))
                    values=world.getcolors(320*180) or []
                    if sum(n for n,c in values if max(c)<8)/(320*180)>.95:
                        issues.append(f'{name}: world capture is empty in {frame.name}')
                    if previous is not None:
                        difference=ImageChops.difference(previous,world)
                        changed=sum(n for n,c in (difference.getcolors(320*180) or []) if max(c)>50)/(320*180)
                        stability.append(round(changed,6))
                        if changed>.35:
                            issues.append(f'{name}: large world flash in {frame.name} ({changed:.1%} pixels)')
                    previous=world
            motion = path.with_name('motion.png')
            if name.endswith(('-walking', '-running')) and motion.exists():
                pairs += 1
                with Image.open(motion) as later:
                    diff = ImageChops.difference(image.crop((485, 275, 710, 385)), later.convert('RGB').crop((485, 275, 710, 385)))
                    if diff.getbbox() is None:
                        issues.append(f'{name}: preview did not animate between captures')
            images.append(str(path.relative_to(directory)))
    return {'issues': issues, 'images': len(images), 'motion_pairs': pairs, 'world_frame_differences': stability, 'ui_sequence_frames': ui_frames, 'ui_max_changed_fraction': ui_max_change, 'files': images}


def build_gallery(output,versions):
    # One batch-wide montage per feature groups all versions for visual comparison.
    from PIL import Image, ImageDraw
    features = {}
    extras=sorted(p.name for p in output.iterdir() if p.is_dir() and p.name.endswith(('-converted','-boundary')))
    for version in [*versions,*extras]:
        directory = output / version
        evidence = directory / 'visual-quality.json'
        if not evidence.exists():
            continue
        for relative in json.loads(evidence.read_text()).get('files', []):
            image = directory / relative
            features.setdefault(image.parent.name[4:], []).append((version, image))
    gallery = output / 'review'
    gallery.mkdir(exist_ok=True)
    links = []
    for feature, entries in features.items():
        canvas = Image.new('RGB', (1200, ((len(entries) + 2) // 3) * 280), '#11151a')
        draw = ImageDraw.Draw(canvas)
        for i, (version, path) in enumerate(entries):
            x, y = (i % 3) * 400, (i // 3) * 280
            draw.text((x + 6, y + 5), version + ' / ' + feature, fill='white')
            with Image.open(path) as image:
                image = image.convert('RGB'); image.thumbnail((392, 252)); canvas.paste(image, (x + 4, y + 24))
        canvas.save(gallery / (feature + '.jpg'), quality=90)
        links.append(f'<a href="{feature}.jpg"><img width="360" loading="lazy" src="{feature}.jpg"><br>{feature}</a>')
    (gallery / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Cross-version visual review</title><style>body{background:#15191e;color:white;font:16px sans-serif}a{display:inline-block;color:white;margin:12px;vertical-align:top}</style>' + '\n'.join(links))
    return gallery


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--versions', nargs='+', choices=VERSIONS, default=VERSIONS)
    parser.add_argument('--jobs', type=int, default=0, help='0 chooses a CPU and RAM bound concurrency limit')
    parser.add_argument('--focus', action='store_true')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--timeout', type=int, default=600)
    parser.add_argument('--review-only',action='store_true',help='Rebuild reports and galleries from an existing batch')
    args = parser.parse_args()
    if len(set(args.versions)) != len(args.versions):
        parser.error('Each target may run only once per batch')
    output = (args.output or ROOT / 'build/release-batches' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')).resolve()
    if args.review_only:
        versions=json.loads((output/'run.json').read_text())['versions']
        environment=os.environ.copy();environment['FBACTORS_QA_ROOT']=str(output)
        result=subprocess.run([sys.executable,str(ROOT/'tools/release_report.py')],cwd=ROOT,env=environment)
        print(build_gallery(output,versions),flush=True)
        return result.returncode
    if output.exists() and any(output.iterdir()):
        parser.error('Output directory must be empty so old verdicts cannot count as new evidence')
    output.mkdir(parents=True, exist_ok=True)
    jobs = min(len(args.versions), args.jobs or capacity())
    if jobs < 1:
        parser.error('--jobs must be positive or zero for automatic capacity')
    print(f'Batch: {output}\nConcurrent clients: {jobs}', flush=True)
    if not args.skip_build:
        with (output / 'compile.log').open('w') as log:
            result = subprocess.run([str(ROOT / 'gradlew.bat'), 'buildAll',
                ':versions:1.21.11:gpuTest', ':versions:26.3:nativeGpuTest', '--console=plain', '--max-workers=2'],
                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            print('Prebuild failed. See compile.log', flush=True)
            return 1
    (output / 'run.json').write_text(json.dumps({'versions': args.versions, 'jobs': jobs, 'focus': args.focus, 'started_utc': datetime.now(timezone.utc).isoformat()}, indent=2))

    def run(version):
        directory = output / version
        directory.mkdir(exist_ok=True)
        command = ['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'tools/harness.ps1'),
                   '-Version', version, '-ArtifactDirectory', str(directory), '-TimeoutSec', str(args.timeout)]
        if args.focus:
            command.append('-VisualFocus')
        started = time.monotonic()
        with (directory / 'harness.log').open('w') as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        verdict = directory / 'verdict.txt'
        status = verdict.read_text().splitlines()[0].removeprefix('VERDICT=') if verdict.exists() else 'ERROR'
        visual = quality(directory)
        (directory / 'visual-quality.json').write_text(json.dumps(visual, indent=2))
        return {'version': version, 'verdict': status, 'exit_code': result.returncode, 'seconds': round(time.monotonic() - started, 1),
                'visual_issues': len(visual['issues']), 'images': visual['images']}

    results = []
    with ThreadPoolExecutor(max_workers=jobs) as pool:
        tasks = {pool.submit(run, version): version for version in args.versions}
        for future in as_completed(tasks):
            version = tasks[future]
            try:
                result = future.result()
            except Exception as failure:
                result = {'version': version, 'verdict': 'ERROR', 'error': str(failure), 'visual_issues': 1}
            results.append(result)
            (output / 'clients.json').write_text(json.dumps(sorted(results, key=lambda item: item['version']), indent=2))
            print(f"{version}: {result['verdict']}, visual issues={result['visual_issues']} ({len(results)}/{len(tasks)})", flush=True)
    environment = os.environ.copy()
    environment['FBACTORS_QA_ROOT'] = str(output)
    report_result = subprocess.run([sys.executable, str(ROOT / 'tools/release_report.py')], cwd=ROOT, env=environment)
    gallery = build_gallery(output,args.versions)
    print(f'Complete. Review: {gallery / "index.html"}', flush=True)
    return int(report_result.returncode != 0 or any(r['verdict'] != 'PASS' or r['visual_issues'] for r in results))


if __name__ == '__main__':
    raise SystemExit(main())
