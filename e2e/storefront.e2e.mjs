// Storefront browser regression suite: guest cart -> sign-in merge -> coupon -> COD order, mini-cart,
// search suggestions, category nav, hero spotlight, wishlist hearts, accordion, growth features, static
// pages, mobile tab bar, mobile overflow and touch targets.
//
// Run against a LOCAL, THROWAWAY stack only: it signs in as the dev demo customer and places an order.
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
await page.type('#username', 'customer');
await page.type('#password', 'customer123');
await clickText('form button[type=submit]', 'Login');
await page.waitForFunction(() => location.pathname === '/checkout', { timeout: 10000 }).catch(() => {});
ok('after sign-in the shopper lands on /checkout', new URL(page.url()).pathname === '/checkout', page.url());
await page.waitForSelector('.order-summary-box', { timeout: 8000 }).catch(() => {});
const summaryRows = await page.$$eval('.order-summary-box .order-summary-row', els => els.length);
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
await page.waitForSelector('.order-summary-box', { timeout: 8000 }).catch(() => {});

// ---------- 5. Coupon
const totalBefore = await page.$eval('.order-summary-box', e => e.innerText);
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
const totalAfter = await page.$eval('.order-summary-box', e => e.innerText);
ok('coupon row appears and total changes', /Coupon WELCOME10/.test(totalAfter) && totalAfter !== totalBefore);
await page.screenshot({ path: path.join(OUT, '03-checkout-coupon.png') });

// ---------- 6. Place order
await page.type('#shippingAddress', '12 MG Road, Indiranagar, Bengaluru 560038');
await page.type('#phoneNumber', '9876543210');
await clickText('button[type=submit]', 'Place Order');
await page.waitForFunction(() => location.pathname === '/orders', { timeout: 10000 }).catch(() => {});
ok('order placed -> redirected to /orders', new URL(page.url()).pathname === '/orders', page.url());
await page.waitForSelector('.order-card', { timeout: 8000 }).catch(() => {});
const banner = await page.$eval('.notice', e => e.innerText).catch(() => '');
ok('confirmation banner shows order number', /INF-\d{8}-/.test(banner), banner.slice(0, 60));
const orderText = await page.$eval('.order-card', e => e.innerText).catch(() => '');
ok('order card present', orderText.includes('Order ID'));
await page.screenshot({ path: path.join(OUT, '04-order-confirmation.png') });

// ---------- 6b. Wishlist heart (logged in)
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.click('.pcard-wish'); await wait(900);
ok('wishlist heart shows saved state', await page.$eval('.pcard-wish', e => e.getAttribute('aria-pressed') === 'true'));
await page.click('.pcard-wish'); await wait(900);
ok('wishlist heart toggles off', await page.$eval('.pcard-wish', e => e.getAttribute('aria-pressed') === 'false'));

// ---------- 7. Product page growth features
await page.goto(BASE + '/products/2', { waitUntil: 'networkidle0' });
await wait(500);
ok('PDP: WhatsApp ask button', await page.$('.wa-ask'));
ok('PDP: JSON-LD Product injected', await page.evaluate(() => {
  const t = document.getElementById('product-jsonld'); if (!t) return false;
  const j = JSON.parse(t.textContent); return j['@type'] === 'Product' && j.offers.price;
}));
ok('PDP: related shelf rendered', await page.$('#related-title'));
ok('PDP: accordion sections', (await page.$$eval('.acc details', e => e.length)) === 3);
await page.click('.acc details:nth-child(2) summary'); await wait(200);
ok('PDP: accordion opens on click', await page.$eval('.acc details:nth-child(2)', e => e.open));
ok('PDP: welcome offer chip', await page.$('.offer-chip'));
await page.screenshot({ path: path.join(OUT, '05-pdp.png'), fullPage: false });

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
