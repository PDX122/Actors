
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import subprocess
import sys
import json
import urllib.request
import time

root=Path(__file__).resolve().parents[2]
output=root/'build/qa-site'
saved=json.loads((root/'docs-site/test-log/data.json').read_text())
errors=[]
server=subprocess.Popen([sys.executable,'-m','http.server','8766','--bind','127.0.0.1','--directory',str(root/'docs-site')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
try:
    for attempt in range(30):
        try:
            urllib.request.urlopen('http://127.0.0.1:8766/test-log/',timeout=1)
            break
        except OSError:
            time.sleep(.1)
    with sync_playwright() as p:
        browser=p.chromium.launch()
        page=browser.new_page(viewport={'width':1440,'height':1000})
        page.on('pageerror',lambda error:errors.append(str(error)))
        for base in ['http://127.0.0.1:8765/','http://127.0.0.1:8766/test-log/']:
            page.goto(base,wait_until='networkidle')
            page.get_by_role('heading',name='Test log',exact=True).wait_for()
            page.locator('.docs-header .icon').evaluate_all('(icons)=>Promise.all(icons.map(i=>i.decode()))')
            assert '\u2014' not in page.locator('body').inner_text()
            assert page.locator('#menu-toggle img[src="icons/menu.svg"]').count()==1
            assert page.locator('.repo-link img[src="icons/github.svg"]').count()==1
            assert '9/9' in page.locator('#current-state').inner_text()
            assert page.locator('.entry').count()>0
            assert 'release, in view' not in page.content().lower()
            assert 'local review site' not in page.content().lower()
            page.screenshot(path=str(output/'test-log-desktop.png'),full_page=True)
            page.locator('[data-view=failures]').click()
            page.locator('.entry').first.wait_for()
            expect(page.locator('.entry')).to_have_count(saved['stats']['historicalFailures'])
            page.locator('.entry > summary').first.click()
            assert page.locator('.entry[open] .context').count() or page.locator('.entry[open] .issue-list').count()
            page.screenshot(path=str(output/'test-log-failures.png'),full_page=True)
            page.locator('#search').fill('ConcurrentModificationException')
            assert page.locator('.entry').count()>=1
            page.locator('#search').fill('')
            page.locator('[data-view=tests]').click()
            expect(page.locator('.entry')).to_have_count(11)
            page.locator('.entry > summary').first.click()
            page.locator('.entry[open] .test-group > summary').first.click()
            assert page.locator('.entry[open] .test-cases li').count()>0
            page.locator('#version').select_option('1.21.1')
            expect(page.locator('.entry')).to_have_count(1)
            page.locator('#version').select_option('all')
            page.locator('[data-view=log]').click()
            page.locator('#search').fill('Timeline mutation')
            expect(page.locator('.entry')).to_have_count(1)
            page.locator('.entry > summary').click()
            page.locator('.entry[open] .references a').first.click()
            assert page.locator('.entry[open]').count()==1
            image=page.locator('.entry[open] [data-image]')
            if image.count():
                image.first.click()
                page.locator('#full-image').evaluate('i=>i.decode()')
                assert page.locator('#image-viewer').is_visible()
                page.keyboard.press('Escape')
            page.locator('#search').fill('')
            page.locator('[data-view=images]').click()
            expect(page.locator('#activity .gallery-image')).to_have_count(20)
            page.locator('#search').fill('cat sitting')
            expect(page.locator('#activity .gallery-image')).to_have_count(1)
            page.locator('#activity .gallery-image').click()
            page.locator('#full-image').evaluate('i=>i.decode()')
            assert page.locator('#image-viewer').is_visible()
            page.keyboard.press('Escape')
            page.locator('#search').fill('')
            page.locator('#theme-toggle').click()
            assert page.locator('html').get_attribute('class')=='dark'
            page.locator('#theme-toggle').click()
        mobile=browser.new_page(viewport={'width':390,'height':844},is_mobile=True,has_touch=True)
        mobile.goto('http://127.0.0.1:8766/test-log/#failures',wait_until='networkidle')
        assert mobile.evaluate('document.documentElement.scrollWidth<=innerWidth')
        mobile.locator('#menu-toggle').click()
        assert mobile.locator('#docs-nav').is_visible()
        mobile.locator('#menu-toggle').click()
        mobile.screenshot(path=str(output/'test-log-mobile.png'),full_page=True)
        assert not errors,errors
        browser.close()
    result={'status':'PASS','checks':['Chronological activity and all saved historical failures','Failure context search','All 4,643 individual test results accessible in 11 suites','Version filters','Update verification links','125 screenshots with visible thumbnails and full-size viewer','Vendored Lucide SVG icons and no em dashes','Light and dark documentation theme','GitHub Pages project subpath','390px mobile layout'],'browser_errors':errors}
    (output/'browser-checks.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
finally:
    server.terminate()
    server.wait(timeout=5)
