






from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import quote, urlsplit, unquote
import argparse
import json
import mimetypes
import os
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'tools/qa_site'
BUILD = ROOT / 'build/qa-site'


def load(path, default=None):
    return json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else default


def title(identifier):
    settings = ['Recording', 'Playback', 'Actors', 'World', 'Cameras and boats', 'Audio', 'Captions', 'Interface', 'Shortcuts']
    if re.fullmatch(r'settings-\d', identifier):
        return 'Settings / ' + settings[int(identifier[-1])]
    special = {'editor': 'The editor', 'actor-setup': 'Actor setup', 'ui-themes': 'UI themes', 'looks': 'Looks gallery',
               'effects': 'Effects', 'actor-morph-picker': 'Actor morph picker', 'skin-library': 'Skin library',
               'shortcut-help': 'Shortcut help', 'camera-timeline': 'Camera timeline', 'boat-handling': 'Boat handling'}
    if identifier in special:
        return special[identifier]
    if re.fullmatch(r'camera-\d+', identifier):
        names = ['Follow', 'Side follow', 'Orbit', 'First person', 'Front follow', 'Overhead', 'Drift follow',
                 'Two subjects', 'Shoulder', 'Low angle', 'Crane reveal', 'Push in', 'Pull back', 'Dolly zoom']
        index = int(identifier.split('-')[-1])
        return 'Camera / ' + (names[index] if index < len(names) else str(index))
    if identifier.startswith('morph-catalog-'):
        return 'Morph catalog / Page ' + str(int(identifier.split('-')[-1]) + 1)
    if identifier.startswith('morph-'):
        return ' / '.join(part.title() for part in identifier[6:].rsplit('-', 1))
    return identifier.replace('-', ' ').capitalize()


def category(identifier):
    if identifier.startswith('settings-'):
        return 'Settings'
    if identifier.startswith('camera-') or identifier == 'boat-handling':
        return 'Cameras'
    if identifier.startswith('morph-') or identifier == 'actor-morph-picker':
        return 'Morphs'
    if identifier in ('looks', 'effects', 'ui-themes', 'studio-looks'):
        return 'Looks'
    if identifier.startswith('error-'):
        return 'Recovery'
    return 'Editor'


def size_label(size):
    return f'{size / 1024**2:.1f} MB' if size >= 1024**2 else f'{size / 1024:.0f} KB'


def prepare(qa, package):
    from qa_site.history import build_log
    return build_log(ROOT, qa, BUILD)


def handler_for(routes):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_HEAD(self):
            self.respond(False)

        def do_GET(self):
            self.respond(True)

        def respond(self, body):
            path = urlsplit(self.path).path
            if self.headers.get('Host', '').split(':')[0] not in ('127.0.0.1', 'localhost'):
                self.send_error(403)
                return
            source = routes.get(path)
            if source is None:
                self.send_error(404)
                return
            length = source.stat().st_size
            start, end = 0, length - 1
            partial = self.headers.get('Range')
            if partial:
                match = re.fullmatch(r'bytes=(\d*)-(\d*)', partial)
                try:
                    if not match or not any(match.groups()):
                        raise ValueError()
                    first, last = match.groups()
                    if not first:
                        start = max(0, length - int(last))
                    else:
                        start = int(first)
                        if last:
                            end = min(end, int(last))
                    if start > end or start >= length:
                        raise ValueError()
                except ValueError:
                    self.send_response(416)
                    self.send_header('Content-Range', f'bytes */{length}')
                    self.end_headers()
                    return
            self.send_response(206 if partial else 200)
            self.send_header('Content-Type', mimetypes.guess_type(str(source))[0] or 'application/octet-stream')
            self.send_header('Content-Length', str(end - start + 1))
            self.send_header('Accept-Ranges', 'bytes')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Referrer-Policy', 'same-origin')
            self.send_header('Content-Security-Policy', "default-src 'self'; img-src 'self'; media-src 'self'; style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'")
            self.send_header('Cache-Control', 'no-cache')
            if partial:
                self.send_header('Content-Range', f'bytes {start}-{end}/{length}')
            if path.startswith('/downloads/'):
                self.send_header('Content-Disposition', f'attachment; filename="{source.name}"')
            self.end_headers()
            if body:
                try:
                    with source.open('rb') as stream:
                        stream.seek(start)
                        remaining = end - start + 1
                        while remaining:
                            chunk = stream.read(min(1024 * 1024, remaining))
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                            remaining -= len(chunk)
                except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                    pass                                                           
    return Handler


def main():
    parser = argparse.ArgumentParser(description='Saved test log tools')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--qa-root', type=Path, default=ROOT / 'build/release-batches/production-final')
    parser.add_argument('--package', type=Path, default=ROOT / 'build/dist/release-candidate-2026-11-01-final')
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    routes, data = prepare(args.qa_root.resolve(), args.package.resolve())
    print(f"Indexed {len(data['entries'])} log entries, including {data['stats']['historicalFailures']} failed attempts.", flush=True)
    if args.prepare_only:
        return
    server = ThreadingHTTPServer(('127.0.0.1', args.port), handler_for(routes))
    address = f'http://127.0.0.1:{server.server_port}'
    (BUILD / 'server.json').write_text(json.dumps({'pid': os.getpid(), 'url': address}), encoding='utf-8')
    print(f'Review site: {address}', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
