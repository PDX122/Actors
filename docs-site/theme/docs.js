(()=>{
'use strict';
const select=selector=>document.querySelector(selector);
const safe=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const prefix=document.body.dataset.docsPrefix||'./';
const icon=name=>`<img class="icon" src="${prefix}assets/icons/${name}.svg" alt="" aria-hidden="true">`;
const theme=select('#docs-theme');
function setTheme(dark){document.documentElement.classList.toggle('dark',dark);localStorage.setItem('actors-docs-theme',dark?'dark':'light');theme.innerHTML=icon(dark?'sun':'moon');theme.setAttribute('aria-label',dark?'Switch to light mode':'Switch to dark mode');}
setTheme(localStorage.getItem('actors-docs-theme')==='dark');
theme.addEventListener('click',()=>setTheme(!document.documentElement.classList.contains('dark')));
select('#docs-menu').addEventListener('click',()=>{const opened=select('#docs-sidebar').classList.toggle('open');select('#docs-menu').setAttribute('aria-expanded',String(opened));});
const dialog=select('#docs-search-dialog'),input=select('#docs-query'),results=select('#docs-search-results');
let index,loadError=false;
async function openSearch(){if(!dialog.open)dialog.showModal();input.focus();if(!index){try{const response=await fetch(document.body.dataset.docsSearch);if(!response.ok)throw Error('Search index unavailable');index=await response.json();}catch{loadError=true;}}search();}
function search(){if(loadError){results.innerHTML='<p class="search-empty">Search could not load. Use the guide navigation to browse the docs.</p>';return;}if(!index)return;const terms=input.value.toLowerCase().trim().split(/\s+/).filter(Boolean);let matches=index.map(page=>{const title=page.title.toLowerCase(),text=page.text.toLowerCase();return{...page,score:terms.reduce((s,t)=>s+(title.includes(t)?10:0)+(text.includes(t)?1:0),0)}}).filter(page=>terms.every(t=>(page.title+' '+page.text).toLowerCase().includes(t)));if(terms.length)matches.sort((a,b)=>b.score-a.score);matches=matches.slice(0,9);results.innerHTML=matches.length?matches.map(page=>{const first=terms.length?page.text.toLowerCase().indexOf(terms[0]):0;const start=Math.max(0,first-35);const excerpt=page.text.slice(start,start+150);return`<a class="search-result" href="${prefix+page.path}"><small>${safe(page.group)}</small><strong>${safe(page.title)}</strong><p>${start?'…':''}${safe(excerpt)}…</p></a>`}).join(''):'<p class="search-empty">No guides match. Try a feature name such as morphs, captions or camera.</p>';}
for(const id of ['docs-search-open','log-doc-search'])select('#'+id)?.addEventListener('click',openSearch);
select('#docs-search-close').addEventListener('click',()=>dialog.close());input.addEventListener('input',search);
window.addEventListener('keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k'){event.preventDefault();event.stopImmediatePropagation();openSearch();}},{capture:true});
for(const table of document.querySelectorAll('.prose table')){const wrapper=document.createElement('div');wrapper.className='table-scroll';table.replaceWith(wrapper);wrapper.append(table);}
for(const pre of document.querySelectorAll('.prose pre')){const code=pre.querySelector('code');if(!code)continue;const button=document.createElement('button');button.className='copy-code';button.textContent='Copy';button.setAttribute('aria-label','Copy code block');button.addEventListener('click',async()=>{try{await navigator.clipboard.writeText(code.textContent);button.textContent='Copied';}catch{button.textContent='Select text to copy';}});pre.prepend(button);}
for(const image of document.querySelectorAll('.prose img[src*="screenshots/"]')){const link=document.createElement('a');link.href=image.src;link.target='_blank';link.rel='noopener';link.setAttribute('aria-label','Open screenshot: '+image.alt);image.replaceWith(link);link.append(image);}


const downloadFilter=select('#minecraft-download-version');
if(downloadFilter){
 const filterDownloads=()=>{let count=0;for(const row of document.querySelectorAll('.download-build')){const matches=downloadFilter.value==='all'||JSON.parse(row.dataset.minecraft).includes(downloadFilter.value);row.hidden=!matches;if(matches)count++;}for(const release of document.querySelectorAll('.download-release'))release.hidden=![...release.querySelectorAll('.download-build')].some(row=>!row.hidden);select('#download-count').textContent=count+' build'+(count===1?'':'s');select('#download-empty').hidden=count>0;};
 downloadFilter.addEventListener('change',filterDownloads);filterDownloads();
 for(const button of document.querySelectorAll('[data-copy-sha]'))button.addEventListener('click',async()=>{try{await navigator.clipboard.writeText(button.dataset.copySha);button.textContent='Copied';select('#checksum-status').textContent='SHA-256 copied to clipboard.';}catch{select('#checksum-status').textContent='SHA-256: '+button.dataset.copySha;}});
}
})();
