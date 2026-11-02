
from pathlib import Path
from urllib.parse import unquote
import shutil
import re


def plain(value):
    return str(value).replace('\u2014', '; ').replace('&mdash;', '; ').replace('&#8212;', '; ')


def cell(value):
    return plain(value).replace('|', '\\|').replace('\n', '<br>')


def export_wiki(data, routes, output):
    output.mkdir(parents=True, exist_ok=True)
    images = output / 'images'
    images.mkdir(exist_ok=True)
    copied = set()

    def image_link(image):
        relative = image['thumbnail']
        name = Path(unquote(relative)).name
        source = routes['/' + relative]
        if name not in copied:
            shutil.copy2(source, images / name)
            copied.add(name)
        return f"![{cell(image['label'])}](images/{name})"

    def save(name, lines):
        (output / name).write_text(plain('\n'.join(lines)) + '\n', encoding='utf-8')

    def record(entry):
        lines = [f"## {entry['title']}", '',
                 f"**{entry['status'].upper()}** · {entry['timestamp']} · `{entry['batch']}`", '', entry['summary'], '']
        if entry.get('laterPass'):
            later = next(e for e in data['entries'] if e['id'] == entry['laterPass'])
            lines += [f"A later {later['batch']} run passed. This earlier attempt remains failed.", '']
        for check in entry['checks']:
            lines += [f"- **{check['status'].upper()}** {check['name']}: {check['detail']}"]
        lines.append('')
        if entry['issues']:
            lines += ['### Recorded visual flags', ''] + ['- ' + issue for issue in entry['issues']] + ['']
        if entry['context']:
            context = entry['context'].replace('```', "'''")
            lines += ['### Failure context', '', '```text', context, '```', '']
        for image in entry['images']:
            lines += [image_link(image), '']
        return lines

    entries = data['entries']
    stats = data['stats']
    intro = ['# Flashback Actors test log', '',
             f"0.11.1: **{stats['currentVersionsPassed']}/9 clients passed**, {stats['unitTests']:,} unit tests and {stats['gpuTests']} GPU tests in the latest matrix.", '',
             f"The history retains {len(entries)} records, including {stats['historicalFailures']} failed or errored attempts. Older individual test XML was overwritten; the test inventory is the latest saved XML.", '',
             '[[Failed attempts|Failures]] · [[Screenshots|Images]] · [[Verified fixes|Updates]] · [[Individual tests|Tests]]', '',
             '[Interactive test log](https://pdx122.github.io/Actors/test-log/)', '']
    for item in data['gallery'][:3]:
        intro += [image_link(item), '']
    intro += ['## Saved runs and builds', '', '| UTC | Result | Record | Batch |', '|---|---|---|---|']
    for entry in entries:
        intro += [f"| {cell(entry['timestamp'][:19])} | {entry['status'].upper()} | {cell(entry['title'])} | {cell(entry['batch'])} |"]
    intro += ['', '## Coverage limits', ''] + ['- ' + item for item in data['remaining']]
    save('Test-log.md', intro)
    save('Home.md', ['# Flashback Actors', '', 'Test history and visual evidence for 0.11.1.', '',
                     '[[Test log|Test-log]] · [[Failures]] · [[Images]] · [[Updates]] · [[Tests]]', '',
                     '[Documentation](https://pdx122.github.io/Actors/)', ''])
    failed = [e for e in entries if e['status'] in ('failed', 'error')]
    lines = ['# Failed attempts', '', f'{len(failed)} saved failed or errored attempts. Later passes are retained separately.', '']
    for entry in failed:
        lines += record(entry)
    save('Failures.md', lines)
    lines = ['# All saved run details', '', 'Client attempts, build results and verified updates. Individual test results are on [[Tests]].', '']
    for entry in entries:
        if entry['type'] != 'tests':
            lines += record(entry)
    save('Runs.md', lines)
    lines = ['# Verified fixes', '']
    for entry in entries:
        if entry['type'] == 'update':
            lines += record(entry)
            lines += ['Verification: ' + ', '.join(next(e for e in entries if e['id'] == ref)['title'] for ref in entry.get('references', [])), '']
    save('Updates.md', lines)
    lines = ['# Screenshots', '', f"{len(data['gallery'])} captures from the final Minecraft 26.3 run. Failed-run images are retained on [[Failures]].", '']
    for image in data['gallery']:
        lines += ['## ' + image['label'], '', image_link(image), '']
    save('Images.md', lines)
    lines = ['# Individual test inventory', '', 'Latest saved JUnit results. Individual test XML from earlier attempts was overwritten.', '',
             '| Minecraft | Suite | Tests | Failures | Results |', '|---|---|---:|---:|---|']
    for entry in entries:
        if entry['type'] != 'tests':
            continue
        name = 'Tests-' + entry['version'] + '-' + entry['kind']
        lines += [f"| {entry['version']} | {entry['kind']} | {len(entry['tests'])} | {sum(t['status'] == 'failed' for t in entry['tests'])} | [[Open|{name}]] |"]
        tests = ['# ' + entry['title'], '', entry['summary'], '', '| Result | Class | Test | Seconds |', '|---|---|---|---:|']
        for test in entry['tests']:
            tests += [f"| {test['status'].upper()} | {cell(test['suite'])} | {cell(test['name'])} | {test['seconds']} |"]
            if test['failure']:
                tests += ['', '```text', test['failure'].replace('```', "'''"), '```', '']
        save(name + '.md', tests)
    save('Tests.md', lines)
    save('_Sidebar.md', ['[[Home]]', '', '[[Test log|Test-log]]', '', '[[All run details|Runs]]', '', '[[Failures]]', '', '[[Images]]', '', '[[Updates]]', '', '[[Tests]]'])
    save('_Footer.md', ['Flashback Actors · 0.11.1 · [Documentation](https://pdx122.github.io/Actors/)'])
    root = Path(__file__).resolve().parents[2]
    guides = sorted((root / 'docs-site/docs').glob('*.md'))
    known = {guide.stem for guide in guides}
    gallery = {image['id']: image for image in data['gallery']}
    guide_links = []
    for guide in guides:
        text = plain(guide.read_text(encoding='utf-8'))
        if guide.stem == 'downloads':
            import json
            catalog = json.loads((root / 'docs-site/downloads/catalog.json').read_text())
            rows = ['| Actors | Minecraft | Java | Download |', '|---|---|---|---|']
            bundles = []
            for release in catalog['releases']:
                base = 'https://pdx122.github.io/Actors/assets/downloads/' + release['version'] + '/'
                for build in release['builds']:
                    rows += [f"| {release['version']} ({release['label']}) | {', '.join(build['minecraft'])} | {build['java']} | [JAR]({base + build['filename']}) |"]
                bundles += [f"- Actors {release['version']}: [All versions ZIP]({base + release['archive']['filename']}) · [Checksums]({base + release['checksums']})"]
            text = text.replace('{{download_catalog}}', '\n'.join(rows)).replace('{{download_bundles}}', '\n'.join(bundles))
        def guide_url(match):
            url = match.group(1)
            if url.startswith('assets/screenshots/'):
                name = Path(url).stem
                if name in gallery:
                    image_link(gallery[name])
                    url = 'images/' + Path(gallery[name]['thumbnail']).name
            elif url == 'test-log/':
                url = 'Test-log'
            elif url.split('#')[0].endswith('.md') and Path(url.split('#')[0]).stem in known:
                name, *fragment = url.split('#', 1)
                url = 'Guide-' + Path(name).stem + ('#' + fragment[0] if fragment else '')
            return '](' + url + ')'
        text = re.sub(r'\]\(([^)]+)\)', guide_url, text)
        save('Guide-' + guide.stem + '.md', [text])
        title = next((line[2:] for line in text.splitlines() if line.startswith('# ')), guide.stem)
        guide_links += [f'- [[{title}|Guide-{guide.stem}]]']
    save('Guides.md', ['# Documentation', '', 'Feature guides migrated from the documentation site.', '', *guide_links])
    save('Home.md', ['# Flashback Actors', '', 'Documentation and test history for 0.11.1.', '',
                     '[[Feature guides|Guides]] · [[Test log|Test-log]] · [[Failures]] · [[Images]] · [[Updates]] · [[Tests]]', '',
                     '[Documentation site](https://pdx122.github.io/Actors/)', ''])
    save('_Sidebar.md', ['[[Home]]', '', '[[Feature guides|Guides]]', '', '[[Test log|Test-log]]', '',
                         '[[All run details|Runs]]', '', '[[Failures]]', '', '[[Images]]', '', '[[Updates]]', '', '[[Tests]]'])
    return {'pages': len(list(output.glob('*.md'))), 'images': len(copied)}
