
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import xml.etree.ElementTree as ET

VERSIONS = ['1.21.1', '1.21.4', '1.21.5', '1.21.8', '1.21.10', '1.21.11', '26.1', '26.2', '26.3']


def read(path):
    return path.read_text(encoding='utf-8-sig', errors='replace') if path.is_file() else ''


def load(path, default=None):
    return json.loads(read(path)) if path.is_file() else default


def stamp(path):
    return datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat()


def identifier(value):
    return re.sub(r'[^a-zA-Z0-9_-]', '-', value)


def target(name):
    return next((version for version in VERSIONS if name == version or name.startswith(version + '-')), None)


def sanitize(value, root):
    value = value.replace('\u00c3\u2014', ' x ').replace('\u00e2\u20ac\u2122', "'").replace('\u00e2\u20ac\u201d', '; ')
    value = value.replace('\u2014', '; ').replace('&mdash;', '; ').replace('&#8212;', '; ')
    value = value.replace(str(root), '<checkout>').replace(root.as_posix(), '<checkout>')
    value = re.sub(r'[A-Za-z]:[\\/]+Users[\\/]+[^\\/\s\"\']+', '<user>', value)
    value = re.sub(r'/Users/[^/\s]+', '<user>', value)
    return value


def tail(path):
    if not path.is_file():
        return ''
    with path.open('rb') as stream:
        stream.seek(max(0, path.stat().st_size - 350_000))
        return stream.read().decode('utf-8', errors='replace')


def failure_context(directory, root):
    text = tail(directory / 'gradle.log') + '\n' + tail(directory / 'harness.log')
    signatures = ['ConcurrentModificationException', 'UnsatisfiedLinkError', 'UnsupportedPacketException',
                  'Dear ImGui Assertion Failed', 'InvalidInjectionException', 'OutOfMemoryError', 'error:']
    for signature in signatures:
        position = text.find(signature)
        if position >= 0:
            return sanitize(text[max(0, text.rfind('\n', 0, position) + 1):position + 1600], root).strip()
    return sanitize(tail(directory / 'harness.log')[-1800:], root).strip()


def client_entry(directory, batch, root):
    verdict_path = directory / 'verdict.txt'
    if not verdict_path.exists() and not (directory / 'harness.log').exists() and not (directory / 'gradle.log').exists():
        return None
    fields = dict(line.split('=', 1) for line in read(verdict_path).splitlines() if '=' in line)
    quality = load(directory / 'visual-quality.json', {})
    outcome = fields.get('VERDICT', 'ERROR')
    issues = quality.get('issues', [])
    status = 'passed' if outcome == 'PASS' else 'failed' if outcome == 'FAIL' else 'error'
    if outcome == 'PASS' and issues:
        status = 'failed'
    summary = fields.get('SUMMARY', 'Client exited without a saved verdict. See the failure context below.')
    if issues:
        summary = f"{len(issues)} visual check(s) flagged. Client verdict: {outcome}. " + summary
    version = target(directory.name)
    kind = 'converted' if 'conversion' in batch or 'converted' in directory.name else 'native'
    if 'boundary' in batch:
        kind = 'boundary'
    checks = [{'name': 'Client harness', 'status': 'passed' if outcome == 'PASS' else status, 'detail': sanitize(fields.get('SUMMARY', ''), root)}]
    for filename, name in [('recording.txt', 'Recording and sharing'), ('camera-export.txt', 'Camera export')]:
        content = read(directory / filename)
        if content:
            checks.append({'name': name, 'status': 'passed' if content.startswith('PASS') else 'failed', 'detail': sanitize(content.splitlines()[0], root)})
    if quality:
        checks.append({'name': 'Visual checks', 'status': 'failed' if issues else 'passed',
                       'detail': f"{quality.get('images', 0)} captures; {quality.get('motion_pairs', 0)} animation pairs; {len(issues)} flags."})
    if quality.get('ui_sequence_frames'):
        checks.append({'name': 'Vulkan morph and menu stability', 'status': 'failed' if issues else 'passed',
                       'detail': f"{quality['ui_sequence_frames']} UI frames, including animation and cache replacement; largest menu change {quality.get('ui_max_changed_fraction',0):.2%}."})
    differences=quality.get('world_frame_differences', [])
    if differences:
        checks.append({'name': 'Morph library world stability',
                       'status': 'passed' if max(differences)<=.35 else 'failed',
                       'detail': f"{len(differences)+1} world frames; largest changed-pixel fraction {max(differences):.2%}."})
    clipboard = read(directory / 'copy-log.txt')
    if clipboard:
        checks.append({'name': 'Error window: copy log', 'status': 'passed' if 'Copied error details' in clipboard else 'incomplete', 'detail': sanitize(clipboard.strip(), root)})
    metadata = load(directory / 'video-metadata.json', {})
    stream = next((s for s in metadata.get('streams', []) if s.get('codec_type') == 'video'), None)
    if stream:
        checks.append({'name': 'Decoded video', 'status': 'passed',
                       'detail': f"{stream['codec_name']}; {stream['width']} x {stream['height']}; {stream.get('nb_read_frames', '?')} frames."})
    event_time = verdict_path if verdict_path.exists() else directory / ('harness.log' if (directory / 'harness.log').exists() else 'gradle.log')
    entry = {'id': identifier(batch + '-' + directory.name), 'type': 'run', 'kind': kind,
             'batch': batch, 'version': version, 'timestamp': stamp(event_time), 'status': status,
             'title': ('ReplayMod import' if kind == 'converted' else 'Export boundary check' if kind == 'boundary' else 'Client run') + ' / ' + ('26.1.2' if version == '26.1' else version),
             'summary': sanitize(summary, root), 'checks': checks, 'issues': [sanitize(i, root) for i in issues],
             'context': failure_context(directory, root) if status != 'passed' else '', 'images': [], 'tests': []}
    return entry


def build_log(root, qa, build):
    from PIL import Image
    routes = {}

    def register(url, path):
        if not path.is_file():
            raise FileNotFoundError(path)
        routes['/' + url] = path.resolve()
        return url

    assets = root / 'tools/qa_site'
    for name in ('index.html', 'app.js', 'styles.css', 'Inter-LICENSE.txt'):
        register(name, assets / name)
    for path in sorted(assets.glob('inter-*.ttf')):
        register(path.name, path)
    for path in sorted((assets / 'icons').iterdir()):
        if path.is_file():
            register('icons/' + path.name, path)
    routes['/'] = routes['/index.html']
    register('logo.png', root / 'src/main/resources/assets/fbactors/logo.png')
    build.mkdir(parents=True, exist_ok=True)
    report = build / 'report.md'
    report.write_text(sanitize(read(qa / 'report.md'), root), encoding='utf-8')
    register('report.md', report)
    entries, seen = [], set()
    candidates = []
    old = root / 'build/release-qa'
    if old.exists():
        candidates.extend((directory, 'release-qa') for directory in old.iterdir() if directory.is_dir() and target(directory.name))
    batches = root / 'build/release-batches'
    for batch in sorted(batches.iterdir()):
        if not batch.is_dir() or batch.name == 'production-final':
            continue
        candidates.extend((directory, batch.name) for directory in batch.iterdir() if directory.is_dir() and target(directory.name))
    for directory, batch in candidates:
        resolved = str(directory.resolve()).casefold()
        if resolved in seen:
            continue
        seen.add(resolved)
        entry = client_entry(directory, batch, root)
        if not entry:
            continue
        visual_files = sorted(directory.glob('visuals/*/editor-ui.png'))
        if entry['issues']:
            flagged = {issue.split(':')[0] for issue in entry['issues']}
            chosen = [p for p in visual_files if p.parent.name.split('-', 1)[-1] in flagged][:2]
        else:
            chosen = [p for p in visual_files if 'morph-cat-sitting' in p.parent.name][:1]
        world_files=sorted(directory.glob('visuals/*morph-world-stability/world-*.png'))
        if world_files:
            chosen.extend([world_files[0],world_files[-1]])
        for scenario in ('morph-ui-stability','morph-ui-cache'):
            sequence=sorted(directory.glob('visuals/*'+scenario+'/sequence/*/editor-ui.png'))
            if sequence:chosen.extend([sequence[0],sequence[-1]])
        if not chosen and entry['status'] != 'passed':
            chosen = visual_files[-1:]
        for index, source in enumerate(chosen):
            key = entry['id'] + '-' + str(index)
            thumbnail = build / 'log-thumbnails' / (key + '.webp')
            thumbnail.parent.mkdir(parents=True, exist_ok=True)
            if not thumbnail.exists() or thumbnail.stat().st_mtime < source.stat().st_mtime:
                with Image.open(source) as image:
                    image = image.convert('RGB')
                    image.thumbnail((600, 375))
                    temporary = thumbnail.with_suffix('.tmp')
                    image.save(temporary, 'WEBP', quality=80)
                    temporary.replace(thumbnail)
            label=source.parent.name.split('-',1)[-1]
            if source.parent.parent.name=='sequence':label=source.parents[2].name.split('-',1)[-1]+' frame '+source.parent.name
            entry['images'].append({'label': label,
                                    'thumbnail': register('thumbs/' + key + '.webp', thumbnail),
                                    'url': register('media/' + key + '.png', source)})
        entries.append(entry)
    for version in VERSIONS:
        for suite, label in [('test', 'Unit tests'), ('gpuTest', 'OpenGL tests'), ('nativeGpuTest', 'Native GPU tests')]:
            files = sorted((root / 'versions' / version / 'build/test-results' / suite).glob('TEST-*.xml'))
            if not files:
                continue
            tests = []
            for file in files:
                xml = ET.parse(file).getroot()
                for test in xml.findall('testcase'):
                    failure = test.find('failure')
                    if failure is None:
                        failure = test.find('error')
                    skipped = test.find('skipped') is not None
                    tests.append({'suite': test.get('classname', xml.get('name', '')).split('.')[-1],
                                  'name': test.get('name', ''), 'seconds': test.get('time', '0'),
                                  'status': 'failed' if failure is not None else 'skipped' if skipped else 'passed',
                                  'failure': sanitize(failure.text or failure.get('message', ''), root)[:4000] if failure is not None else ''})
            failures = sum(t['status'] == 'failed' for t in tests)
            skipped = sum(t['status'] == 'skipped' for t in tests)
            entries.append({'id': 'tests-' + version + '-' + suite, 'type': 'tests', 'kind': suite,
                            'version': version, 'batch': 'JUnit XML', 'timestamp': stamp(max(files, key=lambda p: p.stat().st_mtime)),
                            'status': 'failed' if failures else 'incomplete' if skipped else 'passed',
                            'title': label + ' / ' + ('26.1.2' if version == '26.1' else version),
                            'summary': f'{len(tests)} tests; {failures} failures; {skipped} skipped.',
                            'tests': tests, 'issues': [], 'checks': [], 'images': [], 'context': ''})
    logs = sorted(set(root.glob('build/production*.log')) | set(root.glob('build/release-validation*.log')) | set(root.glob('build/conversion*tests.log')))
    for path in logs:
        content = read(path)
        outcomes = list(re.finditer(r'BUILD (SUCCESSFUL|FAILED)', content))
        if not outcomes:
            continue
        failed = outcomes[-1].group(1) == 'FAILED'
        context = ''
        if failed:
            relevant = [line for line in content.splitlines() if 'error:' in line or 'Execution failed' in line or 'FAILED' in line or 'Test >' in line]
            context = sanitize('\n'.join(relevant[:15]), root)
        entries.append({'id': identifier(path.stem), 'type': 'build', 'kind': 'build', 'version': None,
                        'batch': path.name, 'timestamp': stamp(path), 'status': 'failed' if failed else 'passed',
                        'title': path.stem.replace('-', ' ').capitalize(),
                        'summary': outcomes[-1].group(0) + ('. Compiler/build failure context is preserved below.' if failed else '.'),
                        'tests': [], 'issues': [], 'checks': [], 'images': [], 'context': context})
    updates = [
        ('export-boundary-final', 'Camera exports keep the final tick', 'Camera-only export ranges now use the exclusive clip end. The regression runs decoded 56 frames for fourteen clips on 1.21.1, 26.2 and 26.3. A single-tick clip is also covered by a unit test.'),
        ('conversion-final-2', 'ReplayMod conversion completed the full harness', 'The 26.2 fixture now separates login, configuration and play traffic, filters connection packets, restores the local player, and translates relative entity movement. Unsupported payloads and movement formats stop before writing a replay.'),
        ('render-repair', 'Timeline mutation and preview state fixes verified', 'The actor list can be read safely while the replay thread updates it. The reruns also verify morph previews after repairing the 26.2 OpenGL state cache.'),
        ('production-check-3', 'Parallel client isolation repaired', 'Each client gets its own FFmpeg native cache. Process cleanup now matches an exact version directory, so 1.21.1 does not match 1.21.10 or 1.21.11. Legacy preview matrices and unavailable morph entries were also corrected.'),
    ]
    for batch, title_text, summary in updates:
        proof = [entry for entry in entries if entry['type'] == 'run' and entry['batch'] == batch and entry['status'] == 'passed']
        if proof:
            entries.append({'id': 'update-' + batch, 'type': 'update', 'kind': 'update', 'version': None,
                            'batch': batch, 'timestamp': max(e['timestamp'] for e in proof), 'status': 'fixed',
                            'title': title_text, 'summary': summary, 'tests': [], 'issues': [], 'checks': [], 'images': [],
                            'context': '', 'references': [e['id'] for e in proof]})
    entries.sort(key=lambda entry: entry['timestamp'], reverse=True)
    for entry in entries:
        if entry['status'] not in ('failed', 'error'):
            continue
        later = next((e for e in entries if e['status'] == 'passed' and e['type'] == entry['type']
                      and e['version'] == entry['version'] and e['kind'] == entry['kind'] and e['timestamp'] > entry['timestamp']), None)
        if later:
            entry['laterPass'] = later['id']
    gallery = []
    quality = load(qa / '26.3/visual-quality.json', {})
    for relative in quality.get('files', []):
        source = qa / '26.3' / Path(relative.replace('\\', '/'))
        name = source.parent.name.split('-', 1)[-1]
        key = 'latest-' + name
        thumbnail = build / 'log-thumbnails' / (key + '.webp')
        thumbnail.parent.mkdir(parents=True, exist_ok=True)
        if not thumbnail.exists() or thumbnail.stat().st_mtime < source.stat().st_mtime:
            with Image.open(source) as image:
                image = image.convert('RGB')
                image.thumbnail((600, 375))
                image.save(thumbnail, 'WEBP', quality=82)
        names = {'camera-8': 'Shoulder camera', 'morph-cat-sitting': 'Cat sitting pose', 'looks': 'Looks gallery',
                 'settings-4': 'Camera and boat settings', 'error-recovery': 'Error recovery window',
                 'morph-zombie-sitting': 'Zombie sitting pose', 'actor-morph-picker': 'Actor morph picker'}
        item = {'id': name, 'label': names.get(name, name.replace('-', ' ').capitalize()), 'version': '26.3',
                'thumbnail': register('thumbs/' + key + '.webp', thumbnail),
                'url': register('media/' + key + '.png', source)}
        motion = source.with_name('motion.png')
        if motion.exists():
            item['motion'] = register('media/' + key + '-motion.png', motion)
        gallery.append(item)
    test_summary = load(qa / 'test-summary.json')
    current = load(qa / 'evidence.json')
    result = {'project': 'Flashback Actors', 'version': '0.11.1', 'repository': 'https://github.com/PDX122/Actors',
              'versions': VERSIONS, 'entries': entries, 'gallery': gallery, 'remaining': load(qa / 'review.json')['remaining'],
              'stats': {'runs': sum(e['type'] == 'run' for e in entries), 'historicalFailures': sum(e['status'] in ('failed', 'error') for e in entries),
                        'unitTests': sum(t['tests'] for t in test_summary if t['suite'] == 'test'),
                        'gpuTests': sum(t['tests'] for t in test_summary if t['suite'] != 'test'),
                        'currentVersionsPassed': sum(e['client'] == 'PASS' and not e['visual_issues'] and not e['video_issues'] for e in current)}}
    build.mkdir(parents=True, exist_ok=True)
    output = build / 'data.json'
    def clean(value):
        if isinstance(value, str):
            return sanitize(value, root)
        if isinstance(value, list):
            return [clean(item) for item in value]
        if isinstance(value, dict):
            return {key: clean(item) for key, item in value.items()}
        return value
    result = clean(result)
    output.write_text(json.dumps(result), encoding='utf-8')
    register('data.json', output)
    return routes, result
