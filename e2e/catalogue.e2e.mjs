// Catalogue tools browser suite: add a colour, colour swatches on the product page, bulk CSV check and import,
// export, feed and sitemap. LOCAL, THROWAWAY stack only (same setup as storefront.e2e.mjs).
//   CHROME_PATH=<chrome> node e2e/catalogue.e2e.mjs
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const API = process.env.API_URL || 'http://localhost:8080/api';
const OUT = path.resolve(process.env.SHOT_DIR || '.');
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const wait = (ms) => new Promise(r => setTimeout(r, ms));

const admin = await (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: process.env.ADMIN_PASSWORD || 'admin123' }) })).json();
const H = { Authorization: `Bearer ${admin.token}` };
const all = await (await fetch(`${API}/products`)).json();
const base = all[0];

const browser = await puppeteer.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: 'new', args: ['--no-sandbox'] });
const ctx = await browser.createBrowserContext();
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', e => errors.push(e.message));
page.on('console', m => { if (m.type() === 'error' && !/favicon/.test(m.text())) errors.push(m.text()); });
await page.setViewport({ width: 1366, height: 900 });
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.evaluate((s) => { localStorage.setItem('infinity_analytics_consent', 'no'); localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); }, admin);

// ---------- 1. Add a colour from the Products tab
await page.goto(BASE + '/admin?tab=products', { waitUntil: 'networkidle0' });
await page.waitForSelector('.admin-product', { timeout: 8000 });
const row = await page.evaluateHandle((name) => [...document.querySelectorAll('.admin-product')].find(r => r.innerText.includes(name)), base.name);
await row.asElement().evaluate(r => [...r.querySelectorAll('button')].find(b => b.textContent.trim() === 'Add colour').click());
await page.waitForSelector('.add-colour input', { timeout: 3000 });
ok('"Add colour" opens an inline form (no browser pop-up)', await page.$('.add-colour'));
await page.type('.add-colour input', 'Emerald Test');
await page.click('.add-colour .primary');
await page.waitForFunction(() => document.querySelector('#colour')?.value === 'Emerald Test', { timeout: 6000 }).catch(() => {});
ok('new colour opens in the editor, ready for photos and stock', await page.$eval('#colour', e => e.value).catch(() => '') === 'Emerald Test');
ok('it gets its own SKU suggestion field', await page.$('#sku'));

// make it sellable through the API (photo + stock + publish), as the owner would in the editor
const adminList = await (await fetch(`${API}/admin/products`, { headers: H })).json();
const green = adminList.find(p => p.colour === 'Emerald Test');
ok('colour exists as a draft with zero stock', green && green.status === 'DRAFT' && green.stockQuantity === 0);
await fetch(`${API}/products/${green.id}`, { method: 'PUT', headers: { ...H, 'Content-Type': 'application/json' },
  body: JSON.stringify({ ...green, categoryId: green.category.id, imageUrl: '/images/sarees/mysore-crepe-silk-saree.jpg', stockQuantity: 5, gallery: [] }) });
await fetch(`${API}/products/${green.id}/status?to=PUBLISHED`, { method: 'PUT', headers: H });

// ---------- 2. Swatches on the product page
await page.goto(`${BASE}/products/${base.id}`, { waitUntil: 'networkidle0' });
await page.waitForSelector('.swatches', { timeout: 6000 }).catch(() => {});
const names = await page.$$eval('.swatch span', e => e.map(x => x.textContent));
ok('product page shows both colours as swatches', names.length === 2 && names.includes('Emerald Test'), names.join(', '));
ok('current colour is marked', await page.$eval('.swatch.on span', e => e.textContent).then(t => t === base.colour).catch(() => false));
await page.screenshot({ path: path.join(OUT, 'cat-01-swatches.png') });
await page.evaluate(() => [...document.querySelectorAll('.swatch')].find(a => !a.classList.contains('on')).click());
await page.waitForFunction((id) => location.pathname === `/products/${id}`, { timeout: 5000 }, green.id).catch(() => {});
ok('clicking a swatch opens that colour', new URL(page.url()).pathname === `/products/${green.id}`);
await page.waitForFunction(() => document.querySelector('.swatches-label strong')?.textContent === 'Emerald Test', { timeout: 5000 }).catch(() => {});
ok('title and swatches follow the colour', await page.$eval('.swatches-label strong', e => e.textContent).then(t => t === 'Emerald Test').catch(() => false));

// ---------- 3. Bulk tools
await page.goto(BASE + '/admin?tab=catalogue', { waitUntil: 'networkidle0' });
ok('Bulk & feeds tab opens', await page.$('.catalogue'));
const feedText = await page.$$eval('.cat-url code', e => e.map(x => x.textContent));
ok('feed and sitemap links are shown', feedText.length === 2 && feedText[0].endsWith('/store/feed/google.xml'));

const cats = await (await fetch(`${API}/categories`)).json();
const sku = 'E2E-' + Date.now();
const csv = 'sku,name,category,colour,price,stock,description,image_url\n'
  + `${sku},E2E Imported Saree,"${cats[0].name}",Coral,3499,6,"A lovely test saree, with a long enough description.",https://example.com/coral.jpg\n`
  + `,Broken Row,Nowhere,Red,100,1,Long enough description for this row ok,https://example.com/r.jpg\n`;
const file = path.join(os.tmpdir(), 'e2e-import.csv');
fs.writeFileSync(file, csv);
const chooser = await page.$('.cat-drop input[type=file]');
await chooser.uploadFile(file);
await page.waitForFunction(() => [...document.querySelectorAll('.cat-actions button')].some(b => b.textContent.includes('Check file')), { timeout: 4000 });
ok('Import is disabled until the file is checked', await page.evaluate(() => [...document.querySelectorAll('.cat-actions button')].find(b => /import now/i.test(b.textContent)).disabled));
await page.evaluate(() => [...document.querySelectorAll('.cat-actions button')].find(b => b.textContent.includes('Check file')).click());
await page.waitForSelector('.cat-report', { timeout: 5000 });
const summary = await page.$eval('.cat-summary', e => e.innerText);
ok('check shows 1 new and 1 needing a fix, nothing saved', /1\s*new/.test(summary) && /1\s*need fixing/.test(summary) && /nothing saved/i.test(summary), summary.replace(/\s+/g, ' '));
ok('the bad row says why', (await page.$eval('.cat-report .r-error', e => e.innerText)).includes('Unknown category'));
const before = (await (await fetch(`${API}/admin/products`, { headers: H })).json()).some(p => p.sku === sku);
ok('dry run created nothing', !before);
await page.screenshot({ path: path.join(OUT, 'cat-02-import-check.png'), fullPage: true });
await page.evaluate(() => [...document.querySelectorAll('.cat-actions button')].find(b => /import now/i.test(b.textContent)).click());
await page.waitForFunction(() => /Saved/.test(document.querySelector('.cat-summary')?.innerText || ''), { timeout: 6000 }).catch(() => {});
const imported = (await (await fetch(`${API}/admin/products`, { headers: H })).json()).find(p => p.sku === sku);
ok('import creates the product as a draft', imported && imported.status === 'DRAFT' && imported.stockQuantity === 6);

// ---------- 4. Public feeds
const feed = await (await fetch(`${API}/store/feed/google.xml`)).text();
ok('Google feed lists published sarees with prices', feed.includes('<g:price>') && feed.includes('INR') && feed.includes('<g:item_group_id>'));
ok('Google feed hides the imported draft', !feed.includes('E2E Imported Saree'));
const map = await (await fetch(`${API}/store/sitemap.xml`)).text();
ok('sitemap lists product pages', map.includes(`/products/${base.id}`));
const exp = await (await fetch(`${API}/admin/catalog/export`, { headers: H })).text();
ok('export contains the new product', exp.includes(sku));

// ---------- 5. Phone layout
await page.setViewport({ width: 360, height: 780, isMobile: true, hasTouch: true });
for (const url of ['/admin?tab=catalogue', `/products/${base.id}`]) {
  await page.goto(BASE + url, { waitUntil: 'networkidle0' });
  ok(`mobile: ${url.split('?')[0]} fits the screen`, await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1));
}
await page.screenshot({ path: path.join(OUT, 'cat-03-pdp-mobile.png') });
ok('no JavaScript errors', errors.length === 0, errors.slice(0, 3).join(' | '));
await browser.close();
const failed = results.filter(x => !x.pass);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
