// Storefront browser regression suite (71 checks): guest cart -> sign-in merge -> coupon -> structured
// checkout -> confirmation page -> orders timeline; gallery, quick view, price filters, mini-cart, search,
// wishlist, accordion, auth layout, manifest, static pages, mobile tab bar / filter sheet, touch targets.
//
// LOCAL, THROWAWAY stack only: it edits product 1 through the admin API and places an order.
//   1. Backend, dev profile with demo data (see run.ps1), with STORE_WHATSAPP=919876543210 GA_MEASUREMENT_ID=G-TEST12345
//   2. Frontend: VITE_API_URL=http://localhost:8080/api npx vite --port 4200
//   3. npm i --no-save puppeteer-core ; CHROME_PATH=<chrome> node e2e/storefront.e2e.mjs
// The first-order coupon is single use per customer: restart the backend between runs.
import puppeteer from 'puppeteer-core';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const OUT = path.resolve(process.env.SHOT_DIR || '.');
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };

const browser = await puppeteer.launch({
  executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new',
  args: ['--no-sandbox'],
});

// ---------- 0. Setup (throwaway local DB): give product 1 two extra photos through the admin API
{
  const API = process.env.API_URL || 'http://localhost:8080/api';
  const auth = await (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: process.env.ADMIN_PASSWORD || 'admin123' }) })).json();
  const H = { 'Content-Type': 'application/json', Authorization: `Bearer ${auth.token}` };
  const list = await (await fetch(`${API}/admin/products`, { headers: H })).json();
  const p = list.find(x => x.id === 1);
  const body = { ...p, categoryId: p.category.id, gallery: ['/images/sarees/banarasi-silk-saree.jpg', '/images/sarees/mysore-crepe-silk-saree.jpg'] };
  const r = await fetch(`${API}/products/1`, { method: 'PUT', headers: H, body: JSON.stringify(body) });
  ok('setup: admin API saves a product gallery', r.ok, `(${r.status})`);
}

let page = await browser.newPage();
const consoleErrors = [];
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', e => consoleErrors.push('PAGEERROR ' + e.message));
await page.setViewport({ width: 1280, height: 900 });

const clickText = async (selector, text) => {
  const handle = await page.evaluateHandle((sel, t) =>
    [...document.querySelectorAll(sel)].find(el => el.textContent.trim().toLowerCase().includes(t.toLowerCase())), selector, text);
  const el = handle.asElement();
  if (!el) throw new Error(`no ${selector} with text "${text}"`);
  await el.click();
};
const wait = (ms) => new Promise(r => setTimeout(r, ms));

// ---------- 1. Home
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
const cards = await page.$$eval('.pcard', els => els.length);
ok('home shows product cards', cards >= 6, `(${cards})`);
ok('WhatsApp button present', await page.$('.wa-fab'));
ok('consent banner shown (GA configured)', await page.$('.consent'));
ok('newsletter in footer', await page.$('.newsletter'));
await page.screenshot({ path: path.join(OUT, '01-home.png') });

// decline cookies
await clickText('.consent button', 'Decline');
ok('consent banner dismissed', !(await page.$('.consent')));

// ---------- 2. Guest add to cart
// ---------- 1b. Interactive UX
ok('category nav bar lists categories + deals', (await page.$$eval('.catnav a', e => e.length)) >= 4);
ok('hero spotlight has a visible slide', await page.$('.spot-slide.on'));
const dots = await page.$$eval('.spot-dots button', e => e.length);
ok('hero spotlight has navigation dots', dots >= 2, `(${dots})`);
await page.click('.spot-dots button:nth-child(2)'); await wait(1100);
ok('spotlight dot switches slide', await page.$eval('.spot-slide:nth-child(2)', e => e.classList.contains('on')));
ok('category tiles render with images', (await page.$$eval('.tile img', e => e.length)) >= 2);
await page.type('.header-search input', 'silk'); await wait(500);
const sugg = await page.$$eval('.suggest li', e => e.length);
ok('search suggestions appear while typing', sugg >= 1, `(${sugg})`);
await page.keyboard.press('ArrowDown');
ok('arrow key highlights a suggestion', await page.$('.suggest li.on'));
await page.keyboard.press('Escape');
ok('Escape closes suggestions', !(await page.$('.suggest')));
await page.$eval('.header-search input', e => { e.value = ''; });
await page.click('.tile'); await wait(900);
ok('clicking a category tile filters the catalogue', new URL(page.url()).searchParams.get('category'), page.url());
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });

// ---------- 2. Guest add to cart (hover quick-add, then the slide-in panel)
await page.hover('.pcard');
await page.click('.pcard .pcard-quick');
await page.waitForSelector('.drawer', { timeout: 4000 }).catch(() => {});
ok('add to cart opens the slide-in mini-cart', await page.$('.drawer'));
await page.waitForSelector('.drawer-sugg li', { timeout: 3000 }).catch(() => {});
ok('mini-cart offers matching sarees', (await page.$$eval('.drawer-sugg li', e => e.length)) >= 1);
await page.keyboard.press('Escape'); await wait(300);
ok('Escape closes the mini-cart', !(await page.$('.drawer')));
await wait(500);
ok('stays on home after guest add (no login redirect)', new URL(page.url()).pathname === '/');
const badge = await page.$eval('.badge-count', e => e.textContent.trim()).catch(() => null);
ok('header cart badge shows 1 for guest', badge === '1', `(${badge})`);

// ---------- 3. Guest cart page
await page.goto(BASE + '/cart', { waitUntil: 'networkidle0' });
const guestLines = await page.$$eval('.cart-line', els => els.length);
ok('guest cart page lists the item', guestLines === 1, `(${guestLines})`);
await page.screenshot({ path: path.join(OUT, '02-guest-cart.png') });

// ---------- 4. Sign in -> returns to checkout with merged cart
await clickText('button', 'Sign in to check out');
await page.waitForFunction(() => location.pathname === '/login', { timeout: 8000 }).catch(() => {});
ok('guest "Sign in to check out" goes to /login', new URL(page.url()).pathname === '/login', page.url());
await page.waitForSelector('#username', { timeout: 10000 });
await page.type('#username', 'customer');
await page.type('#password', 'customer123');
await clickText('form button[type=submit]', 'Login');
await page.waitForFunction(() => location.pathname === '/checkout', { timeout: 10000 }).catch(() => {});
ok('after sign-in the shopper lands on /checkout', new URL(page.url()).pathname === '/checkout', page.url());
await page.waitForSelector('.ck-summary', { timeout: 8000 }).catch(() => {});
const summaryRows = await page.$$eval('.ck-summary .ck-totals > div', els => els.length);
ok('merged cart shows in checkout summary', summaryRows >= 4, `(${summaryRows} rows)`);
const guestLeft = await page.evaluate(() => localStorage.getItem('infinity_guest_cart'));
ok('guest cart cleared after merge', !guestLeft || guestLeft === '[]', guestLeft);

// Headless Chrome's password manager swallows keystrokes after a login-form submit (a harness quirk,
// verified: typing works when the session is restored without the form). Continue in a fresh context.
const session = await page.evaluate(() => ({ t: localStorage.getItem('infinity_token'), u: localStorage.getItem('infinity_user') }));
const ctx = await browser.createBrowserContext();
page = await ctx.newPage();
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
await page.setViewport({ width: 1280, height: 900 });
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.evaluate((x) => { localStorage.setItem('infinity_token', x.t); localStorage.setItem('infinity_user', x.u); localStorage.setItem('infinity_analytics_consent', 'no'); }, session);
await page.goto(BASE + '/checkout', { waitUntil: 'networkidle0' });
await page.waitForSelector('.ck-summary', { timeout: 8000 }).catch(() => {});

// ---------- 5. Coupon
const totalBefore = await page.$eval('.ck-summary', e => e.innerText);
await page.type('#coupon', 'nope');
await clickText('.coupon-row button', 'Apply');
await wait(800);
ok('invalid coupon shows an error toast', (await page.evaluate(() => document.body.innerText)).includes('not valid'));
await page.$eval('#coupon', e => (e.value = ''));
await page.click('#coupon', { clickCount: 3 });
await page.type('#coupon', 'welcome10');
await clickText('.coupon-row button', 'Apply');
await page.waitForSelector('.coupon-applied', { timeout: 5000 }).catch(() => {});
ok('WELCOME10 applies', await page.$('.coupon-applied'));
const totalAfter = await page.$eval('.ck-summary', e => e.innerText);
ok('coupon row appears and total changes', /Coupon WELCOME10/.test(totalAfter) && totalAfter !== totalBefore);
await page.screenshot({ path: path.join(OUT, '03-checkout-coupon.png') });

// ---------- 6. Place order (structured address)
const fill = async (sel, v) => {
  await page.click(sel);
  await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control');
  await page.keyboard.press('Backspace');
  await page.type(sel, v);
};
await fill('#ck-fullName', 'Priya Sharma');
await fill('#ck-phone', '9876543210');
await fill('#ck-line1', '12 MG Road, Indiranagar');
await fill('#ck-pincode', '560038');
await fill('#ck-city', 'Bengaluru');
await page.select('#ck-state', 'Karnataka');
await page.screenshot({ path: path.join(OUT, '04a-checkout.png'), fullPage: true });
await clickText('button[type=submit]', 'Place order');
await page.waitForFunction(() => location.pathname === '/order/success', { timeout: 10000 }).catch(() => {});
ok('order placed -> confirmation page', new URL(page.url()).pathname === '/order/success', page.url());
await page.waitForSelector('.success-no', { timeout: 8000 }).catch(() => {});
const okNo = await page.$eval('.success-no', e => e.innerText).catch(() => '');
ok('confirmation shows order number', /INF-\d{8}-/.test(okNo), okNo);
ok('confirmation shows the delivery address', (await page.$eval('.success-ship', e => e.innerText).catch(() => '')).includes('Priya Sharma'));
ok('confirmation shows a status timeline', await page.$('.otl li.current'));
await page.screenshot({ path: path.join(OUT, '04-order-confirmation.png'), fullPage: true });
await page.goto(BASE + '/orders', { waitUntil: 'networkidle0' });
ok('orders page lists the order with a timeline', (await page.$$eval('.order .otl', e => e.length)) >= 1);
await clickText('.order-actions button', 'Cancel order');
ok('cancel asks for inline confirmation (no browser dialog)', await page.$('.confirm-inline'));
await clickText('.confirm-inline button', 'Keep it');
await page.screenshot({ path: path.join(OUT, '05-orders.png'), fullPage: true });

// ---------- 6b. Wishlist heart (logged in)
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
const pressed = (v) => page.waitForFunction((x) => document.querySelector('.pcard-wish')?.getAttribute('aria-pressed') === x, { timeout: 8000 }, v).then(() => true, () => false);
await page.click('.pcard-wish');
ok('wishlist heart shows saved state', await pressed('true'));
await page.click('.pcard-wish');
ok('wishlist heart toggles off', await pressed('false'));

// ---------- 7. Product page growth features
await page.goto(BASE + '/products/2', { waitUntil: 'networkidle0' });
await wait(500);
ok('PDP: WhatsApp ask button', await page.$('.wa-ask'));
ok('PDP: JSON-LD Product injected', await page.evaluate(() => {
  const t = document.getElementById('product-jsonld'); if (!t) return false;
  const j = JSON.parse(t.textContent); return j['@type'] === 'Product' && j.offers.price;
}));
ok('PDP: related shelf rendered', await page.$('#related-title'));
await page.goto(BASE + '/products/1', { waitUntil: 'networkidle0' });
ok('PDP: gallery shows 3 photos with thumbnails', (await page.$$eval('.gallery-thumbs button', e => e.length)) === 3);
await page.click('.gallery-thumbs button:nth-child(2)'); await wait(700);
ok('PDP: thumbnail switches photo', await page.$eval('.gallery-thumbs button:nth-child(2)', e => e.classList.contains('on')));
await page.click('.gallery-slide'); await wait(300);
ok('PDP: photo opens full-screen viewer', await page.$('.viewer'));
await page.keyboard.press('ArrowRight'); await wait(150);
ok('PDP: viewer counter moves with arrow keys', /\/ 3/.test(await page.$eval('.viewer-count', e => e.innerText).catch(() => '')));
await page.keyboard.press('Escape'); await wait(200);
ok('PDP: Escape closes the viewer', !(await page.$('.viewer')));
await page.screenshot({ path: path.join(OUT, '05b-pdp-gallery.png') });
await page.goto(BASE + '/#products', { waitUntil: 'networkidle0' });
await page.hover('.pcard'); await page.click('.pcard .pcard-qv'); await wait(400);
ok('quick view opens from the card', await page.$('.qv'));
await page.screenshot({ path: path.join(OUT, '05c-quickview.png') });
await page.keyboard.press('Escape'); await wait(200);
ok('quick view closes with Escape', !(await page.$('.qv')));
const before = await page.$$eval('.pgrid .pcard', e => e.length);
await clickText('.price-chips .chip', 'Under'); await wait(400);
const after = await page.$$eval('.pgrid .pcard', e => e.length);
ok('price filter narrows the collection', after > 0 && after < before, `(${before} -> ${after})`);
ok('active filter chip appears', await page.$('.active-filters'));
await clickText('.active-filters .text-link', 'Clear all');
ok('clear all restores the collection', await page.waitForFunction((n) => document.querySelectorAll('.pgrid .pcard').length === n, { timeout: 6000 }, before).then(() => true, () => false));
await page.goto(BASE + '/products/2', { waitUntil: 'networkidle0' });
ok('PDP: accordion sections', (await page.$$eval('.acc details', e => e.length)) === 3);
await page.click('.acc details:nth-child(2) summary'); await wait(200);
ok('PDP: accordion opens on click', await page.$eval('.acc details:nth-child(2)', e => e.open));
ok('PDP: welcome offer chip', await page.$('.offer-chip'));
await page.screenshot({ path: path.join(OUT, '05-pdp.png'), fullPage: false });

// ---------- 7b. Auth layout and installability
const ctxAnon = await browser.createBrowserContext();
const anon = await ctxAnon.newPage(); await anon.setViewport({ width: 1280, height: 900 });
await anon.goto(BASE + '/login', { waitUntil: 'networkidle0' });
ok('sign-in uses the split brand layout', await anon.$('.auth-shell .auth-art'));
await anon.screenshot({ path: path.join(OUT, '09-login.png') });
const mf = await anon.evaluate(async () => { const l = document.querySelector('link[rel=manifest]'); if (!l) return null; const r = await fetch(l.href); return r.ok ? (await r.json()).short_name : null; });
ok('web app manifest is served (installable)', mf === 'Rekha Shopping Mall', mf);
await ctxAnon.close();

// ---------- 8. Static pages
for (const p of ['/shipping', '/contact', '/privacy', '/terms']) {
  await page.goto(BASE + p, { waitUntil: 'networkidle0' });
  const h1 = await page.$eval('h1', e => e.textContent).catch(() => '');
  ok(`page ${p} renders`, h1.length > 0, `(${h1})`);
}

// ---------- 9. Newsletter
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await wait(5600); // let earlier toasts expire
await page.type('#nl-email', 'shopper@example.com');
await clickText('.nl-form button', 'Subscribe');
await page.waitForSelector('.nl-done', { timeout: 5000 }).catch(() => {});
ok('newsletter sign-up confirms', await page.$('.nl-done'));

// ---------- 10. Mobile overflow + tap targets
await page.setViewport({ width: 375, height: 812, isMobile: true, hasTouch: true });
for (const p of ['/', '/products/2', '/cart', '/shipping', '/login']) {
  await page.goto(BASE + p, { waitUntil: 'networkidle0' });
  await wait(300);
  const over = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  ok(`mobile ${p}: no horizontal overflow`, over <= 1, `(overflow ${over}px)`);
}
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
ok('mobile: bottom tab bar visible on home', await page.$eval('.bottomnav', e => getComputedStyle(e).display !== 'none'));
ok('mobile: category bar hidden (tab bar replaces it)', await page.$eval('.catnav', e => getComputedStyle(e).display === 'none'));
await page.goto(BASE + '/products/2', { waitUntil: 'networkidle0' });
ok('mobile: tab bar yields to the sticky buy bar on product pages', !(await page.$('.bottomnav')));
await page.goto(BASE + '/#products', { waitUntil: 'networkidle0' });
await clickText('.mobile-tools button', 'Filters'); await wait(400);
ok('mobile: Filters opens a bottom sheet', await page.$('.catalog-controls.open'));
await page.screenshot({ path: path.join(OUT, '08-mobile-filters.png') });
await clickText('.sheet-foot .btn', 'Show'); await wait(300);
ok('mobile: sheet closes with its Show button', !(await page.$('.catalog-controls.open')));
await page.goto(BASE + '/products/2', { waitUntil: 'networkidle0' });
await page.screenshot({ path: path.join(OUT, '06-mobile-pdp.png') });
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.screenshot({ path: path.join(OUT, '07-mobile-home.png') });
const small = await page.evaluate(() => [...document.querySelectorAll('button, a.btn, .header-actions a')]
  .filter(el => el.offsetParent !== null)
  .map(el => { const r = el.getBoundingClientRect(); return { t: (el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 30), w: Math.round(r.width), h: Math.round(r.height) }; })
  .filter(x => x.w < 24 || x.h < 24));
ok('mobile: no interactive element below 24x24px (WCAG 2.2 min)', small.length === 0, JSON.stringify(small.slice(0, 5)));

// ---------- console
const real = consoleErrors.filter(e => !/favicon|ERR_NETWORK_CHANGED|Failed to load resource.*(400|401|404)/i.test(e));
ok('no unexpected console errors', real.length === 0, JSON.stringify(real.slice(0, 4)));

await browser.close();
const failed = results.filter(r => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
