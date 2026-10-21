// Admin console suite: navigation, dashboard, the product editor with drag-in photos that are enhanced and stored in the
// library, live preview and checklist, publishing, the media library, the photo and video studio (enhance, before/after,
// a reel recorded in the browser and published), offer pop-ups designed in the admin and shown to shoppers, and orders.
//
// LOCAL, THROWAWAY stack only (see storefront.e2e.mjs). Run: node e2e/admin.e2e.mjs
import puppeteer from 'puppeteer-core';
import fs from 'fs';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const API = process.env.API_URL || 'http://localhost:8080/api';
const FIXTURES = process.env.FIXTURE_DIR || new URL('../ecommerce-frontend/public/images/sarees/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const photo = (n) => `${FIXTURES.replace(/\/$/, '')}/${n}`;
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const wait = (ms) => new Promise(r => setTimeout(r, ms));

const admin = await (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: process.env.ADMIN_PASSWORD || 'admin123' }) })).json();
const H = { Authorization: `Bearer ${admin.token}` };
for (const f of ['bridal-red-zari-saree.jpg', 'kerala-kasavu-saree.jpg', 'organza-zari-saree.jpg']) if (!fs.existsSync(photo(f))) throw new Error('missing fixture ' + photo(f));

const browser = await puppeteer.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: 'new', args: ['--no-sandbox', '--autoplay-policy=no-user-gesture-required'] });
const errors = [];
async function open(width = 1366, asAdmin = true) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error' && !/favicon|Failed to load resource|ERR_/.test(m.text())) errors.push(m.text()); });
  await page.setViewport({ width, height: 900 });
  await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
  await page.evaluate((s, a) => {
    localStorage.setItem('infinity_analytics_consent', 'no');
    if (a) { localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); }
  }, admin, asAdmin);
  return page;
}
const tab = async (page, t) => { await page.goto(`${BASE}/admin?tab=${t}`, { waitUntil: 'networkidle0' }); await page.waitForSelector('.adm-top h1', { timeout: 10000 }); };
const setVal = (page, sel, value) => page.$eval(sel, (el, v) => { const proto = el.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v); el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); }, value);
const clickText = (page, sel, text) => page.evaluate((s, t) => { const el = [...document.querySelectorAll(s)].find(e => e.textContent.trim().includes(t)); if (!el) throw new Error('no ' + s + ' with ' + t); el.click(); }, sel, text);

// ---------- 1. Shell, navigation and the dashboard
let page = await open();
await tab(page, 'autopilot');
ok('admin opens on Today with the business at a glance', await page.$('.kpis') && (await page.$$('.kpi')).length === 4);
ok('sidebar groups the sections', (await page.$$eval('.adm-group-label', e => e.map(x => x.textContent))).join() === 'Home,Catalogue,Sales,Grow,People');
ok('the title bar names the page', (await page.$eval('.adm-top h1', e => e.textContent)) === 'Today');
ok('the autopilot to-do list is still on Today', await page.evaluate(() => document.body.innerText.includes("Your shop's assistant") || document.body.innerText.includes('Autopilot')));
await page.evaluate(() => [...document.querySelectorAll('.admin-tabs button')].find(b => /Sarees/.test(b.textContent)).click());
await page.waitForSelector('.admin-product', { timeout: 6000 });
ok('Sarees lists every product as a card with a photo', (await page.$$('.admin-product')).length >= 9 && (await page.$$('.admin-product .ap-thumb img')).length >= 9);
ok('active section is marked for assistive tech', await page.$eval('.admin-tabs button.active', e => e.getAttribute('aria-current') === 'page'));
await page.type('.toolbar input[type="search"]', 'kasavu');
await wait(300);
ok('search narrows the sarees', (await page.$$('.admin-product')).length === 1);
await clickText(page, '.chips button', 'Needs stock');
await wait(200);
ok('quick filters work together with search', (await page.$$('.admin-product')).length === 0 && await page.$('.ap-empty'));
await page.close();

// ---------- 2. Add a saree: drag in photos, enhanced and stored in the library
page = await open();
await tab(page, 'management');
await page.waitForSelector('#pname');
ok('editor shows the live preview and a checklist', await page.$('.pv-card') && (await page.$$('.check li')).length >= 8);
ok('nothing is ready yet, and the checklist says so', (await page.$eval('.savebar > span', e => e.textContent)).includes('still needed'));
await page.type('#pname', 'Studio Test Kanjivaram');
await page.type('#pdesc', 'Pure silk with a woven gold zari border and a rich pallu, made for weddings and festive evenings.');
await page.select('#cat', String((await (await fetch(`${API}/categories`)).json())[0].id));
await page.type('#price', '12000');
await page.evaluate(() => document.querySelector('input[name="deal"]').click());
await page.type('#dealPrice', '9600');
await page.type('#fabric', 'Silk'); await page.type('#colour', 'Ruby red'); await page.type('#occasion', 'Wedding');
ok('live preview follows what is typed', (await page.$eval('.pv-card strong', e => e.textContent)) === 'Studio Test Kanjivaram' && (await page.$eval('.pv-price', e => e.textContent)).includes('9,600'));
ok('preview shows the deal badge', (await page.$eval('.pv-badge', e => e.textContent)).includes('20% off'));
const input = await page.$('.dropzone input[type="file"]');
await input.uploadFile(photo('bridal-red-zari-saree.jpg'), photo('kerala-kasavu-saree.jpg'), photo('organza-zari-saree.jpg'));
await page.waitForFunction(() => document.querySelectorAll('.photo-grid li').length === 3, { timeout: 60000 }).catch(() => {});
ok('three photos were enhanced, uploaded and appear', (await page.$$('.photo-grid li')).length === 3);
ok('the first is the cover', await page.$eval('.photo-grid li:first-child', e => e.classList.contains('is-cover') && !!e.querySelector('.cover-tag')));
const srcs = await page.$$eval('.photo-grid img', i => i.map(x => x.getAttribute('src')));
ok('photos are library files with a checksum link', srcs.every(s => /\/api\/media\/\d+\/[a-f0-9]{10}$/.test(s)), srcs[0]);
const dims = await page.$$eval('.photo-grid img', i => i.map(x => [x.naturalWidth, x.naturalHeight]));
ok('each stored photo is 1200 x 1500 (4:5)', dims.every(([w, h]) => w === 1200 && h === 1500), JSON.stringify(dims[0]));
ok('preview now shows the cover', await page.$eval('.pv-media img', e => e.naturalWidth > 0));
ok('checklist is complete and says ready', (await page.$eval('.savebar > span', e => e.textContent)) === 'Ready to publish');
// reorder: make the third photo the cover
await page.hover('.photo-grid li:nth-child(3)');
const third = srcs[2];
await page.click('.photo-grid li:nth-child(3) button[aria-label$="the cover"]');
await page.waitForFunction((u) => document.querySelector('.photo-grid li:first-child img')?.getAttribute('src') === u, { timeout: 8000 }, third).catch(() => {});
ok('"make cover" moves a photo to the front', (await page.$eval('.photo-grid li:first-child img', e => e.getAttribute('src'))) === third);
await page.click('.photo-grid li:last-child button[aria-label^="Remove"]');
await page.waitForFunction(() => document.querySelectorAll('.photo-grid li').length === 2, { timeout: 8000 }).catch(() => {});
ok('a photo can be removed', (await page.$$('.photo-grid li')).length === 2);
await input.uploadFile(photo('organza-zari-saree.jpg'));
await page.waitForFunction(() => document.querySelectorAll('.photo-grid li').length === 3, { timeout: 60000 }).catch(() => {});
await clickText(page, '.savebar button', 'Save and publish');
await page.waitForSelector('.admin-product', { timeout: 10000 });
const made = (await (await fetch(`${API}/admin/products`, { headers: H })).json()).find(p => p.name === 'Studio Test Kanjivaram');
ok('saree is saved and published', made && made.status === 'PUBLISHED');
ok('its cover and gallery are library links', made && /^\/api\/media\/\d+\//.test(made.imageUrl) && made.gallery.length === 2 && made.gallery.every(g => g.startsWith('/api/media/')));
await page.close();

// shoppers see it, with a photo that really loads
page = await open(1366, false);
await page.goto(`${BASE}/products/${made.id}`, { waitUntil: 'networkidle0' });
await page.waitForSelector('.gallery-slide img, .gallery img, .pdp img', { timeout: 8000 });
ok('the product page shows the uploaded photo', await page.$$eval('img', is => is.some(i => i.src.includes('/api/media/') && i.naturalWidth === 1200)));
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
ok('the home page card shows it too', await page.$$eval('.pcard img', is => is.some(i => i.src.includes('/api/media/') && i.complete && i.naturalWidth > 0)));
await page.close();

// ---------- 3. Edit that saree: older photos, the studio hand-off
page = await open();
await tab(page, 'products');
await page.waitForSelector('.admin-product');
await page.evaluate(() => { const row = [...document.querySelectorAll('.admin-product')].find(r => r.innerText.includes('Studio Test Kanjivaram')); [...row.querySelectorAll('button')].find(b => b.textContent.trim() === 'Edit').click(); });
await page.waitForSelector('#pname');
ok('edit loads the saree with its photos', (await page.$eval('#pname', e => e.value)) === 'Studio Test Kanjivaram' && (await page.$$('.photo-grid li')).length === 3);
ok('editing shows its name in the editor', (await page.$eval('.editor-head h2', e => e.textContent)).includes('Studio Test Kanjivaram'));
await page.close();

// ---------- 4. Media library
page = await open();
await tab(page, 'media');
await page.waitForSelector('.dropzone');
const before = (await (await fetch(`${API}/admin/media`, { headers: H })).json()).length;
await (await page.$('.dropzone input[type="file"]')).uploadFile(photo('hero-sarees.jpg'));
await page.waitForFunction((n) => document.querySelectorAll('.media-card').length > n, { timeout: 30000 }, before).catch(() => {});
ok('library lists the uploaded photo with its size', (await page.$$('.media-card')).length === before + 1 && (await page.$eval('.media-card .media-meta small', e => e.textContent)).includes('KB'));
const lib = await (await fetch(`${API}/admin/media`, { headers: H })).json();
const used = lib.find(m => m.url === made.imageUrl);
ok('a photo used by a saree cannot be deleted', await (async () => { const r = await fetch(`${API}/admin/media/${used.id}`, { method: 'DELETE', headers: H }); return r.status === 400; })());
await clickText(page, '.chips button', 'Videos');
await wait(200);
ok('filters separate photos from videos', (await page.$$('.media-card')).length === 0);
await clickText(page, '.chips button', 'Photos');
await page.click('.media-card .media-actions button[aria-label^="Delete"]');
ok('delete asks first, inline', await page.$('.media-card .media-actions .danger-btn') && (await page.$eval('.media-card .media-actions', e => e.textContent)).includes('Yes, delete'));
await page.close();

// ---------- 5. Studio: enhance, compare, hand the photo to the editor
page = await open();
await tab(page, 'studio');
await page.select('#st-prod', String(made.id));
await page.waitForSelector('.studio-step:nth-of-type(2)', { timeout: 8000 });
ok('choosing a saree loads its cover into the studio', await page.$('.studio-src'));
await clickText(page, 'button', 'Enhance photo');
await page.waitForSelector('.compare', { timeout: 30000 });
ok('before and after comparison appears', await page.$('.compare .before') && await page.$('.compare input, .studio-view input[type="range"]'));
ok('it reports the new size', (await page.$eval('.stats', e => e.textContent)).includes('1200×1500'));
await setVal(page, '.studio-view > input[type="range"]', '20');
ok('the slider moves the comparison', (await page.$eval('.compare', e => e.getAttribute('style'))).includes('20%'));
ok('AI briefs are written from the saree and can be copied', (await page.$$('.prompt')).length === 3 && (await page.$eval('.prompt p', e => e.textContent)).includes('Ruby red'));
await clickText(page, 'button', 'Use as cover of this saree');
await page.waitForSelector('#pname', { timeout: 15000 });
ok('"use as cover" opens the editor with the new photo, unsaved', (await page.$eval('.photo-grid li:first-child img', e => e.getAttribute('src'))) !== made.imageUrl && (await page.$eval('#pname', e => e.value)) === 'Studio Test Kanjivaram');
await page.close();

// ---------- 6. Studio: a cinematic reel recorded in the browser, then published
page = await open();
await tab(page, 'studio');
await page.select('#st-prod', String(made.id));
await page.waitForSelector('#rl-title', { timeout: 8000 });
ok('reel title is filled from the saree', (await page.$eval('#rl-title', e => e.value)) === 'Studio Test Kanjivaram');
await page.click('.focus-pick');
await clickText(page, 'button', 'Create reel');
await page.waitForSelector('.reel-stage canvas', { timeout: 5000 });
ok('the reel can be watched while it is recorded', true);
await page.waitForSelector('.reel-stage video', { timeout: 40000 });
const reelInfo = await page.$eval('.reel-stage video', v => new Promise(res => { const done = () => res({ w: v.videoWidth, h: v.videoHeight, src: v.src.slice(0, 5) }); if (v.readyState >= 1) done(); else v.addEventListener('loadedmetadata', done, { once: true }); }));
ok('a real 540 x 960 video was recorded', reelInfo.w === 540 && reelInfo.h === 960, JSON.stringify(reelInfo));
const reelsBefore = (await (await fetch(`${API}/store/videos`)).json()).length;
await clickText(page, 'button', 'Publish to');
await page.waitForFunction(async (n, api) => (await (await fetch(`${api}/store/videos`)).json()).length > n, { timeout: 30000 }, reelsBefore, API).catch(() => {});
const vids = await (await fetch(`${API}/store/videos`)).json();
const reel = vids.find(v => v.productId === made.id);
ok('the reel is published to the store, linked to the saree', reel && reel.kind === 'REEL' && reel.videoUrl.startsWith('/api/media/') && reel.posterUrl.startsWith('/api/media/'));
const served = await fetch(API.replace(/\/api$/, '') + reel.videoUrl);
ok('the recorded video is served with a video type and long cache', /^video\//.test(served.headers.get('content-type')) && /immutable/.test(served.headers.get('cache-control')));
await page.close();
page = await open(1366, false);
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
ok('shoppers see the new reel in "Sarees in motion"', await page.$$eval('.reel', rs => rs.some(r => (r.getAttribute('data-video-src') || '').includes('/api/media/'))));
await page.close();

// ---------- 7. Offer pop-ups
page = await open();
await tab(page, 'popups');
await page.waitForSelector('.presets');
await clickText(page, '.presets button', 'Festival sale');
ok('presets fill the form and the live preview', (await page.$eval('#pp-head', e => e.value)).includes('Festive sale') && (await page.$eval('.popup-preview .promo h2', e => e.textContent)).includes('Festive sale'));
ok('the sale preset switches the countdown on', await page.evaluate(() => [...document.querySelectorAll('.popup-builder input[type="checkbox"]')].some(c => c.checked)));
await page.select('#pp-theme', 'EMERALD');
ok('theme changes the preview colours', await page.$eval('.popup-preview .promo', e => e.classList.contains('promo-emerald')));
await page.select('#pp-code', 'WELCOME10');
await setVal(page, '#pp-delay', '1');
await page.select('#pp-freq', 'SESSION');
await setVal(page, '#pp-end', '2099-01-01T10:00');
await page.click('.popup-builder form button[type="submit"]');
await page.waitForSelector('.popup-list li', { timeout: 8000 });
ok('the pop-up is created and live', (await page.$eval('.popup-list li .chip-status', e => e.textContent)) === 'Live');
const live = await (await fetch(`${API}/store/popup`)).json();
ok('shoppers\' API serves it without internal fields', live.headline.includes('Festive sale') && live.couponCode === 'WELCOME10' && !('name' in live));
await page.close();

page = await open(1366, false);
await page.evaluate(() => localStorage.setItem('rsm_promo_force', '1'));
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.waitForSelector('.promo-dialog', { timeout: 10000 });
ok('shoppers get the pop-up after the delay', await page.$('.promo-dialog[role="dialog"]'));
ok('it shows the code, a countdown and a button', await page.$('.promo-code code') && await page.$('.promo-clock') && await page.$('.promo-cta'));
ok('code is shown in capitals', (await page.$eval('.promo-code code', e => e.textContent)) === 'WELCOME10');
ok('focus moves into the dialog', await page.evaluate(() => document.querySelector('.promo-dialog').contains(document.activeElement)));
await page.keyboard.press('Escape');
await wait(300);
ok('Escape closes it and frees the page', !(await page.$('.promo-dialog')) && !(await page.evaluate(() => document.body.classList.contains('no-scroll'))));
await page.goto(BASE + '/track', { waitUntil: 'networkidle0' });
await wait(2500);
ok('it does not nag again while moving around the site', !(await page.$('.promo-dialog')));
await page.goto(BASE + '/checkout', { waitUntil: 'networkidle0' });
await wait(2500);
ok('it never appears on checkout or sign-in pages', !(await page.$('.promo-dialog')));
await page.close();

// pause it: shoppers stop seeing it
const popups = await (await fetch(`${API}/admin/popups`, { headers: H })).json();
for (const p of popups.filter(x => x.headline.includes('Festive'))) await fetch(`${API}/admin/popups/${p.id}/active?value=false`, { method: 'PUT', headers: H });
const afterPause = await fetch(`${API}/store/popup`);
ok('pausing the newest pop-up brings back the previous live one', afterPause.status === 200 && (await afterPause.json()).headline.includes('Welcome'));

// ---------- 8. Orders
page = await open();
await tab(page, 'orders');
ok('orders have filters with counts and a search', (await page.$$('.chips button')).length === 6 && await page.$('.toolbar input[type="search"]'));
await page.close();

// ---------- 9. Phone layout
page = await open(390, true);
await tab(page, 'products');
const m = await page.evaluate(() => ({ overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth, navScrolls: document.querySelector('.admin-tabs').scrollWidth > document.querySelector('.admin-tabs').clientWidth, sideHidden: getComputedStyle(document.querySelector('.adm-brand')).display === 'none' }));
ok('on a phone the sidebar becomes a swipeable bar and nothing overflows', m.overflow <= 1 && m.navScrolls && m.sideHidden, JSON.stringify(m));
for (const t of ['management', 'studio', 'media', 'popups', 'orders']) {
  await tab(page, t);
  const o = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  ok(`phone layout has no sideways scroll: ${t}`, o <= 1, `(${o}px)`);
}
await page.close();

// clean up what the suite created
for (const v of (await (await fetch(`${API}/admin/videos`, { headers: H })).json()).filter(v => v.productId === made.id)) await fetch(`${API}/admin/videos/${v.id}`, { method: 'DELETE', headers: H });
await fetch(`${API}/products/${made.id}`, { method: 'DELETE', headers: H });
for (const p of popups.filter(x => x.headline.includes('Festive'))) await fetch(`${API}/admin/popups/${p.id}`, { method: 'DELETE', headers: H });

ok('no JavaScript errors', errors.length === 0, JSON.stringify(errors.slice(0, 3)));
await browser.close();
const failed = results.filter(r => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
