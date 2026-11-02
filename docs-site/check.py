from pathlib import Path
from html.parser import HTMLParser
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlsplit, unquote
from functools import partial
import argparse
import json
import threading
import hashlib

BASE=Path(__file__).resolve().parent
OUTPUT=BASE/'site'

class Links(HTMLParser):
    def __init__(self,path):
        super().__init__();self.path=path;self.urls=[]
    def handle_starttag(self,tag,attrs):
        for key,value in attrs:
            if key in ('href','src') and value:
                self.urls.append(value)

def static_check():
    pages=list(OUTPUT.rglob('*.html'));errors=[]
    for path in pages:
        source=path.read_text(encoding='utf-8')
        assert '\u2014' not in source,path
        parser=Links(path);parser.feed(source)
        for url in parser.urls:
            parts=urlsplit(url)
            if parts.scheme or parts.netloc or not parts.path:
                continue
            destination=(path.parent/unquote(parts.path)).resolve()
            if destination.is_dir():destination=destination/'index.html'
            if not destination.is_file():errors.append((str(path.relative_to(OUTPUT)),url))
    assert not errors,errors
    index=json.loads((OUTPUT/'search.json').read_text())
    expected=sum(len(group['pages']) for group in json.loads((BASE/'navigation.json').read_text(encoding='utf-8-sig')))
    assert len(index)==expected,len(index)
    for record in index:
        assert (OUTPUT/record['path']/'index.html').is_file(),record['path']
    catalog=json.loads((OUTPUT/'assets/downloads/catalog.json').read_text())
    builds=0
    for release in catalog['releases']:
        folder=OUTPUT/'assets/downloads'/release['version']
        for item in [*release['builds'],release['archive']]:
            path=folder/item['filename']
            path.resolve().relative_to(folder.resolve())
            with path.open('rb') as stream:
                assert hashlib.file_digest(stream,'sha256').hexdigest()==item['sha256'],path
            assert path.stat().st_size==item['bytes'],path
        assert release['display_version']==f"{release['version']}.{release['revision']}"
        assert release['display_version'] in (OUTPUT/'downloads/index.html').read_text()
        builds+=len(release['builds'])
    return {'pages':len(pages),'search_records':len(index),'broken_links':len(errors),'verified_downloads':builds}

class Quiet(SimpleHTTPRequestHandler):
    def log_message(self,*args):
        pass

def browser_check():
    from playwright.sync_api import sync_playwright,expect
    server=ThreadingHTTPServer(('127.0.0.1',0),partial(Quiet,directory=str(BASE)))
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    url=f'http://127.0.0.1:{server.server_port}/site/'
    evidence=BASE.parent/'build/docs-checks';evidence.mkdir(parents=True,exist_ok=True)
    errors=[];failed_requests=[]
    try:
        with sync_playwright() as playwright:
            browser=playwright.chromium.launch()
            context=browser.new_context(viewport={'width':1440,'height':1000},permissions=['clipboard-read','clipboard-write'])
            page=context.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
            page.on('response',lambda response:failed_requests.append((response.status,response.url)) if response.status>=400 else None)
            records=json.loads((OUTPUT/'search.json').read_text())
            for record in records:
                page.goto(url+record['path'],wait_until='networkidle')
                assert page.locator('h1').count()==1,record['path']
                assert page.locator('.site-sidebar [aria-current=page]').count()==1,record['path']
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),record['path']
                page.locator('img[src]').evaluate_all('(images)=>Promise.all(images.filter(i=>i.getAttribute("src")&&i.loading!=="lazy").map(i=>i.decode()))')
            page.goto(url,wait_until='networkidle');page.screenshot(path=str(evidence/'overview-desktop.png'),full_page=True)
            page.keyboard.press('Control+k');page.locator('#docs-query').fill('sitting')
            expect(page.locator('#docs-search-results')).to_contain_text('Morphs and poses')
            page.locator('#docs-search-results').get_by_role('link',name='Appearance Morphs and poses',exact=False).click()
            expect(page.locator('h1')).to_have_text('Morphs and poses')
            page.screenshot(path=str(evidence/'morphs-desktop.png'),full_page=True)
            page.locator('.page-toc').get_by_role('link',name='Choose an animation or pose').click()
            assert page.url.endswith('#choose-an-animation-or-pose')
            page.locator('#docs-theme').click();assert page.locator('html').get_attribute('class')=='dark'
            page.reload(wait_until='networkidle');assert page.locator('html').get_attribute('class')=='dark'
            page.screenshot(path=str(evidence/'morphs-dark.png'),full_page=True)
            page.locator('#docs-theme').click()
            page.goto(url+'custom-shaders/',wait_until='networkidle')
            page.locator('.copy-code').first.click();expect(page.locator('.copy-code').first).to_have_text('Copied')
            page.goto(url+'downloads/',wait_until='networkidle')
            expect(page.locator('.download-build')).to_have_count(9)
            page.screenshot(path=str(evidence/'downloads-desktop.png'),full_page=True)
            page.locator('#minecraft-download-version').select_option('1.21.7')
            expect(page.locator('.download-build:visible')).to_have_count(1)
            expect(page.locator('.download-build:visible')).to_contain_text('1.21.6, 1.21.7, 1.21.8')
            page.locator('#minecraft-download-version').select_option('26.3')
            uploaded=page.locator('.download-release [data-upload-time]')
            assert uploaded.get_attribute('datetime').endswith('Z')
            assert uploaded.inner_text() and 'Invalid' not in uploaded.inner_text()
            row=page.locator('.download-build:visible')
            row.locator('summary').click();row.locator('[data-copy-sha]').click()
            expect(page.locator('#checksum-status')).to_have_text('SHA-256 copied to clipboard.')
            with page.expect_download() as download:
                row.locator('.download-button').click()
            assert download.value.suggested_filename=='flashback-actors-26.3-0.11.1.jar'
            received=evidence/download.value.suggested_filename
            download.value.save_as(received)
            with received.open('rb') as stream:
                assert hashlib.file_digest(stream,'sha256').hexdigest()==row.locator('[data-copy-sha]').get_attribute('data-copy-sha')
            page.goto(url+'test-log/',wait_until='networkidle')
            expect(page.locator('#current-state')).to_contain_text('9/9 clients passed')
            page.locator('[data-view=failures]').click()
            expected_failures=json.loads((OUTPUT/'test-log/data.json').read_text())['stats']['historicalFailures']
            expect(page.locator('.entry')).to_have_count(expected_failures)
            page.locator('#log-doc-search').click();page.locator('#docs-query').fill('captions')
            page.locator('#docs-search-results').get_by_role('link',name='Project Captions',exact=False).click()
            expect(page.locator('h1')).to_have_text('Captions')
            page.goto(url+'test-log/#images',wait_until='networkidle')
            page.locator('#search').fill('cat sitting');page.locator('#activity .gallery-image').click()
            page.locator('#full-image').evaluate('image=>image.decode()');assert page.locator('#image-viewer').is_visible()
            page.keyboard.press('Escape')
            mobile=browser.new_page(viewport={'width':390,'height':844},is_mobile=True,has_touch=True)
            mobile.on('pageerror',lambda error:errors.append(str(error)))
            for slug in ['','morphs/','install/','looks/','test-log/','downloads/']:
                mobile.goto(url+slug,wait_until='networkidle')
                assert mobile.evaluate('document.documentElement.scrollWidth<=innerWidth'),slug
            mobile.goto(url+'morphs/',wait_until='networkidle');mobile.screenshot(path=str(evidence/'morphs-mobile.png'),full_page=True)
            mobile.locator('#docs-menu').click();assert mobile.locator('#docs-sidebar').is_visible()
            mobile.locator('#docs-sidebar').get_by_role('link',name='Camera modes',exact=True).click()
            expect(mobile.locator('h1')).to_have_text('Camera modes')
            mobile.locator('#docs-search-open').click();mobile.locator('#docs-query').fill('no matching feature 92383')
            expect(mobile.locator('#docs-search-results')).to_contain_text('No guides match')
            mobile.keyboard.press('Escape')
            browser.close()
    finally:
        server.shutdown();server.server_close();thread.join(timeout=5)
    assert not errors,errors
    assert not failed_requests,failed_requests
    return {'browser_errors':errors,'failed_requests':failed_requests,'checks':['All pages and local assets','Project subpath navigation','Full-text search and empty states','Anchor navigation','Persistent light/dark theme','Code copying','Minecraft build filter and SHA-256 copying','Real JAR download and checksum verification','Integrated failure history and image viewer','390px mobile layout and menu']}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Check documentation links and browser behavior')
    parser.add_argument('--static-only',action='store_true')
    args=parser.parse_args()
    result={'status':'PASS','static':static_check()}
    if not args.static_only:result['browser']=browser_check()
    output=BASE.parent/'build/docs-checks';output.mkdir(parents=True,exist_ok=True)
    (output/'checks.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
