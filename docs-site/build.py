from pathlib import Path
from html import escape, unescape
from html.parser import HTMLParser
from urllib.parse import urlsplit
from urllib.request import urlopen
from urllib.error import HTTPError
from datetime import datetime, timezone
import argparse
import json
import re
import shutil
import markdown

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / 'docs-site'
GROUPS = json.loads((BASE / 'navigation.json').read_text(encoding='utf-8-sig'))
PAGES = [(slug, label, group['label']) for group in GROUPS for slug, label in group['pages']]
REPO = 'https://github.com/PDX122/Actors'

def clean(text):
    return text.replace('\u2014', '; ').replace('&mdash;', '; ').replace('&#8212;', '; ')

def icon(name, prefix):
    return f'<img class="icon" src="{prefix}assets/icons/{name}.svg" alt="" aria-hidden="true">'

def href(slug, prefix):
    return prefix if slug == 'index' else prefix + slug + '/'

def navigation(current, prefix):
    groups = []
    for group in GROUPS:
        links = ''.join(f'<a href="{href(slug, prefix)}"'+(' class="active" aria-current="page"' if slug == current else '')+f'>{escape(label)}</a>' for slug, label in group['pages'])
        groups.append(f'<section><h2>{escape(group["label"])}</h2>{links}</section>')
    return '<nav aria-label="Documentation">'+''.join(groups)+'</nav>'

def header(prefix, log=False):
    search_id = 'log-doc-search' if log else 'docs-search-open'
    return f'''<header class="site-header"><div class="site-header-inner"><button class="icon-button mobile-menu" id="docs-menu" aria-label="Toggle navigation" aria-expanded="false">{icon('menu',prefix)}</button><a class="site-brand" href="{href('index',prefix)}"><img src="{prefix}assets/logo.png" alt="">Flashback Actors</a><span class="version-label-top">0.11.1</span><a class="header-downloads" href="{href('downloads',prefix)}">Downloads</a><button class="search-open" id="{search_id}" type="button">{icon('search',prefix)}<span>Search documentation</span><kbd>Ctrl K</kbd></button><button class="icon-button" id="docs-theme" aria-label="Switch theme">{icon('moon',prefix)}</button><a class="icon-button github-link" href="{REPO}" aria-label="GitHub repository">{icon('github',prefix)}</a></div></header>'''

def search_dialog(prefix):
    return f'''<dialog class="docs-search" id="docs-search-dialog" aria-labelledby="docs-search-title"><div class="search-dialog-top"><h2 id="docs-search-title">Search documentation</h2><button class="icon-button" id="docs-search-close" aria-label="Close search">{icon('x',prefix)}</button></div><input id="docs-query" type="search" placeholder="Search guides, features and settings" aria-label="Search documentation"><div id="docs-search-results" aria-live="polite"></div><p class="search-hint">Esc to close. Search stays on this device.</p></dialog>'''

class Text(HTMLParser):
    def __init__(self):
        super().__init__(); self.parts=[]
    def handle_data(self, value):
        self.parts.append(value)
    def value(self):
        return ' '.join(' '.join(self.parts).split())

def plain(html):
    parser=Text();parser.feed(html);return parser.value()

def rewrite(content, prefix, known):
    def replace(match):
        attr, quote, url = match.groups()
        if url.startswith(('https:', 'http:', 'mailto:', '#', 'data:')):
            return match.group(0)
        parts = urlsplit(url)
        path = parts.path
        if path.startswith('assets/'):
            url=prefix+path
        elif path.startswith('test-log/'):
            url=prefix+path
        elif path.endswith('.md'):
            stem=Path(path).stem
            if stem in known:
                url=href(stem,prefix)
            elif (ROOT/path).is_file():
                url=REPO+'/blob/main/'+path
            else:
                raise ValueError('Unresolved guide link: '+path)
        elif path.startswith('../'):
            raise ValueError('Unresolved relative URL: '+path)
        if parts.fragment:
            url+='#'+parts.fragment
        return attr+'='+quote+escape(url,quote=True)+quote
    return re.sub(r'(href|src)=([\'"])(.*?)\2',replace,content)

def size_label(size):
    return f'{size / 1024**2:.1f} MiB'

def stamp_upload_dates():
    path=BASE/'downloads/catalog.json'
    catalog=json.loads(path.read_text(encoding='utf-8'))
    try:
        with urlopen('https://pdx122.github.io/Actors/assets/downloads/catalog.json',timeout=30) as response:
            published=json.load(response)
    except HTTPError as error:
        if error.code!=404:raise
        published={'releases':[]}
    now=datetime.now(timezone.utc).isoformat(timespec='seconds').replace('+00:00','Z')
    for release in catalog['releases']:
        fingerprint={b['filename']:b['sha256'] for b in release['builds']}
        previous=next((r for r in published['releases'] if r['version']==release['version']
            and {b['filename']:b['sha256'] for b in r['builds']}==fingerprint),{})
        release['uploaded_at']=previous.get('uploaded_at') or release.get('uploaded_at') or now
    path.write_text(json.dumps(catalog,indent=2)+'\n',encoding='utf-8')

def upload_label(release):
    value=release.get('uploaded_at')
    if not value:return 'Pending upload'
    date=datetime.fromisoformat(value.replace('Z','+00:00')).astimezone(timezone.utc)
    fallback=date.strftime('%b %d, %Y at %H:%M UTC')
    return f'Uploaded <time data-upload-time datetime="{escape(value,quote=True)}">{fallback}</time>'

def download_sections(prefix):
    catalog=json.loads((BASE/'downloads/catalog.json').read_text())
    versions=list(dict.fromkeys(v for release in catalog['releases'] for build in release['builds'] for v in build['minecraft']))
    options=''.join(f'<option value="{escape(v)}">{escape(v)}</option>' for v in versions)
    rows=f'<div class="download-filter"><label for="minecraft-download-version">Minecraft version</label><select id="minecraft-download-version"><option value="all">All Minecraft versions</option>{options}</select><span id="download-count" role="status"></span></div>'
    bundles=''
    for release in catalog['releases']:
        version=release['version'];base=prefix+'assets/downloads/'+version+'/'
        display_version=escape(release.get('display_version',version));query='?build='+display_version
        uploaded=upload_label(release)
        rows+=f'<section class="download-release"><div class="release-heading"><strong>Actors {escape(version)}</strong><span>Build {display_version} · {escape(release["label"])}</span></div><p class="download-upload">{uploaded}</p>'
        for build in release['builds']:
            versions_label=', '.join(build['minecraft'])
            supported=escape(json.dumps(build['minecraft']),quote=True)
            filename=escape(build['filename']);sha=build['sha256']
            rows+=f'<article class="download-build" data-minecraft="{supported}"><div class="build-top"><div><h3>Minecraft {escape(versions_label)}</h3><p>Build {display_version} · Java {build["java"]} · {size_label(build["bytes"])}</p></div><a class="download-button" href="{base+filename+query}" download="{filename}" aria-label="Download Actors {display_version} for Minecraft {escape(versions_label)}">Download JAR {icon("download",prefix)}</a></div><details class="download-details"><summary>Checksum and filename</summary><code class="download-filename">{filename}</code><div class="download-hash"><code>{sha}</code><button type="button" data-copy-sha="{sha}">Copy SHA-256</button></div></details></article>'
        rows+='</section>'
        archive=release['archive'];name=escape(archive['filename']);sha=archive['sha256']
        bundles+=f'<section class="download-bundle"><div class="build-top"><div><h3>Actors {escape(version)}: all versions</h3><p>{len(release["builds"])} JARs · {size_label(archive["bytes"])} · {escape(release["label"])}</p></div><a class="download-button" href="{base+name+query}" download="{name}">Download ZIP {icon("download",prefix)}</a></div><p class="download-upload">{uploaded}</p><p><a href="{base+release["checksums"]+query}" download>Download checksums</a></p><details class="download-details"><summary>ZIP checksum</summary><div class="download-hash"><code>{sha}</code><button type="button" data-copy-sha="{sha}">Copy SHA-256</button></div></details></section>'
    rows+='<p id="download-empty" hidden>No packaged build matches this Minecraft version.</p><p id="checksum-status" role="status" aria-live="polite"></p>'
    return rows,bundles

def render_page(slug, label, group, body, toc, prefix, previous, following):
    def neighbor(page, direction):
        if not page:return '<span></span>'
        key,title,_=page
        arrow=icon('arrow-left' if direction=='Previous' else 'arrow-right',prefix)
        return f'<a href="{href(key,prefix)}"><span>{direction}</span><strong>{arrow if direction=="Previous" else ""}{escape(title)}{arrow if direction=="Next" else ""}</strong></a>'
    return f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#fbfcfe"><meta name="description" content="{escape(plain(body)[:160],quote=True)}"><title>{escape(label)} | Flashback Actors</title><link rel="icon" href="{prefix}assets/logo.png"><link rel="stylesheet" href="{prefix}assets/docs.css"><script defer src="{prefix}assets/docs.js"></script></head><body data-docs-prefix="{prefix}" data-docs-search="{prefix}search.json"><a class="skip-link" href="#content">Skip to content</a>{header(prefix)}<div class="docs-shell"><aside class="site-sidebar" id="docs-sidebar">{navigation(slug,prefix)}<a class="sidebar-footer" href="{REPO}/issues">Report an issue {icon('external-link',prefix)}</a></aside><main class="doc-main" id="content"><div class="breadcrumb"><a href="{href('index',prefix)}">Docs</a>{icon('chevron-right',prefix)}<span>{escape(group)}</span></div><article class="prose">{body}</article><div class="doc-page-nav">{neighbor(previous,'Previous')}{neighbor(following,'Next')}</div><div class="page-source"><a href="{REPO}/blob/main/docs-site/docs/{slug}.md">View page source {icon('external-link',prefix)}</a></div></main><aside class="page-toc"><strong>On this page</strong>{toc}</aside></div><footer class="site-footer"><span>Flashback Actors · MIT · PDX122</span><a href="{href('changelog',prefix)}">Changelog</a><a href="{REPO}/wiki">Wiki</a></footer>{search_dialog(prefix)}</body></html>'''

def integrate_log(output):
    log=output/'test-log'
    shutil.copytree(BASE/'test-log',log,dirs_exist_ok=True)
    path=log/'index.html';html=path.read_text(encoding='utf-8')
    html=html.replace('<body>','<body class="log-document" data-docs-prefix="../" data-docs-search="../search.json">')
    html=html.replace('<link rel="stylesheet" href="styles.css">','<link rel="stylesheet" href="styles.css"><link rel="stylesheet" href="../assets/docs.css">')
    html=html.replace('<script defer src="app.js"></script>','<script defer src="app.js"></script><script defer src="../assets/docs.js"></script>')
    a=html.index('  <header class="docs-header">');b=html.index('  <div class="docs-layout">',a)
    html=html[:a]+header('../',True)+html[b:]
    a=html.index('    <aside class="docs-nav"');b=html.index('    <main>',a)
    html=html[:a]+'<aside class="site-sidebar" id="docs-sidebar">'+navigation('test-log','../')+'</aside>'+html[b:]
    html=html.replace('  <dialog id="image-viewer"',search_dialog('../')+'<div hidden><button id="menu-toggle"></button><button id="theme-toggle"></button><input id="global-search"></div>  <dialog id="image-viewer"')
    html=re.sub(r'<footer class="docs-footer">.*?</footer>','<footer class="site-footer"><span>Flashback Actors · MIT · PDX122</span><a href="../">Documentation</a></footer>',html,flags=re.S)
    path.write_text(clean(html),encoding='utf-8')

def build(output):
    output=output.resolve()
    allowed=output==BASE/'site' or ROOT/'build' in output.parents
    if not allowed:
        raise ValueError('Docs output must be docs-site/site or a child of build/')
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True,exist_ok=True)
    assets=output/'assets';assets.mkdir(exist_ok=True)
    for source in (ROOT/'tools/qa_site').glob('inter-*.ttf'):shutil.copy2(source,assets/source.name)
    for name in ('Inter-LICENSE.txt',):shutil.copy2(ROOT/'tools/qa_site'/name,assets/name)
    shutil.copytree(ROOT/'tools/qa_site/icons',assets/'icons',dirs_exist_ok=True)
    shutil.copytree(BASE/'docs/assets',assets,dirs_exist_ok=True)
    shutil.copytree(BASE/'downloads',assets/'downloads',dirs_exist_ok=True)
    for name in ('docs.css','docs.js'):shutil.copy2(BASE/'theme'/name,assets/name)
    shutil.copy2(ROOT/'src/main/resources/assets/fbactors/logo.png',assets/'logo.png')
    screenshots=assets/'screenshots';screenshots.mkdir(exist_ok=True)
    gallery=json.loads((BASE/'test-log/data.json').read_text())['gallery']
    for image in gallery:
        if (BASE/'test-log'/image['url']).is_file():shutil.copy2(BASE/'test-log'/image['url'],screenshots/(image['id']+'.png'))
    records=[];known={p[0] for p in PAGES}
    guides=[p for p in PAGES if p[0]!='test-log']
    for index,(slug,label,group) in enumerate(guides):
        source=BASE/'docs'/(slug+'.md');text=clean(source.read_text(encoding='utf-8'))
        parser=markdown.Markdown(extensions=['fenced_code','tables','toc','admonition','attr_list','md_in_html','sane_lists'],extension_configs={'toc':{'permalink':False}})
        content=parser.convert(text);prefix='./' if slug=='index' else '../'
        content=rewrite(content,prefix,known)
        if slug=='downloads':
            rows,bundles=download_sections(prefix)
            content=content.replace('<p>{{download_catalog}}</p>',rows).replace('<p>{{download_bundles}}</p>',bundles)
        toc=parser.toc if parser.toc_tokens else ''
        destination=output/'index.html' if slug=='index' else output/slug/'index.html';destination.parent.mkdir(parents=True,exist_ok=True)
        previous=guides[index-1] if index else None;following=guides[index+1] if index+1<len(guides) else None
        destination.write_text(render_page(slug,label,group,content,toc,prefix,previous,following),encoding='utf-8')
        records.append({'title':label,'group':group,'path':'' if slug=='index' else slug+'/', 'text':plain(content)})
    records.append({'title':'Test log','group':'Project','path':'test-log/','text':'Saved client attempts, build failures, fixes, screenshots, unit tests, GPU tests and coverage limits.'})
    (output/'search.json').write_text(json.dumps(records),encoding='utf-8')
    (output/'.nojekyll').write_text('')
    integrate_log(output)
    print(f'Built {len(guides)} guides, integrated test log and {len(gallery)} screenshots: {output}')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Build Flashback Actors documentation')
    parser.add_argument('--output',type=Path,default=BASE/'site')
    parser.add_argument('--publish',action='store_true')
    args=parser.parse_args()
    if args.publish:stamp_upload_dates()
    build(args.output)
