// Shopper tools browser suite: multi-buy offers (admin to cart), delivery estimate, price breakup, personal
// shopping leads, public order tracking, cart cross-sell, card hover photo.
// LOCAL, THROWAWAY stack only (same setup as storefront.e2e.mjs); restart the backend between runs.
//   CHROME_PATH=<chrome> node e2e/shoppers.e2e.mjs
import puppeteer from 'puppeteer-core';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const API = process.env.API_URL || 'http://localhost:8080/api';
const OUT = path.resolve(process.env.SHOT_DIR || '.');
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const wait = (ms) => new Promise(r => setTimeout(r, ms));
const money = (t) => Number(String(t).replace(/[^\d.]/g, ''));
const signin = async (u, p) => (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) })).json();
const admin = await signin('admin', process.env.ADMIN_PASSWORD || 'admin123');
const cust = await signin('customer', 'customer123');
const AH = { Authorization: `Bearer ${admin.token}`, 'Content-Type': 'application/json' };
const CH = { Authorization: `Bearer ${cust.token}`, 'Content-Type': 'application/json' };
const products = await (await fetch(`${API}/products`)).json();
const inStock = products.filter(p => p.stockQuantity > 3);

const browser = await puppeteer.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: 'new', args: ['--no-sandbox'] });
const errors = [];
const open = async (session, width = 1366, height = 900) => {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error' && !/favicon|postalpincode|status of 4/.test(m.text())) errors.push(m.text()); });
  await page.setViewport({ width, height, isMobile: width < 800, hasTouch: width < 800 });
  await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
  await page.evaluate((s) => { localStorage.setItem('infinity_analytics_consent', 'no'); if (s) { localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); } }, session);
  return { ctx, page };
};

// give product 1 a second photo for the hover swap
const p1 = (await (await fetch(`${API}/admin/products`, { headers: AH })).json()).find(p => p.id === inStock[0].id);
await fetch(`${API}/products/${p1.id}`, { method: 'PUT', headers: AH, body: JSON.stringify({ ...p1, categoryId: p1.category.id, gallery: ['/images/sarees/mysore-crepe-silk-saree.jpg'] }) });

// ---------- 1. Admin sets up multi-buy tiers through the screen
let { ctx, page } = await open(admin);
await page.goto(BASE + '/admin?tab=coupons', { waitUntil: 'networkidle0' });
await page.waitForSelector('.offers-form', { timeout: 8000 });
for (const [n, pct] of [[2, 5], [3, 10]]) {
  await page.type('#of-min', String(n));
  await page.type('#of-pct', String(pct));
  await page.click('.offers-form .btn-primary');
  await page.waitForFunction((label) => [...document.querySelectorAll('.offers-list li strong')].some(s => s.textContent === label), { timeout: 5000 }, `Buy ${n}+ and save ${pct}%`).catch(() => {});
}
ok('owner adds two multi-buy tiers', (await page.$$eval('.offers-list li', e => e.length)) === 2);
await page.evaluate(() => [...document.querySelectorAll('.offers-list li')][1].querySelectorAll('button')[0].click());
await wait(500);
ok('a tier can be paused', (await (await fetch(`${API}/store/offers`)).json()).length === 1);
await page.evaluate(() => [...document.querySelectorAll('.offers-list li')][1].querySelectorAll('button')[0].click());
await wait(500);
ok('and resumed', (await (await fetch(`${API}/store/offers`)).json()).length === 2);
await page.screenshot({ path: path.join(OUT, 'sh-01-admin-offers.png') });
await ctx.close();

// ---------- 2. Visitor: announce bar, product page tools, card hover photo
({ ctx, page } = await open(null));
await page.reload({ waitUntil: 'networkidle0' });
ok('announcement bar advertises the best tier', (await page.$eval('.announce', e => e.innerText)).includes('Buy 3+ sarees, save 10%'));
ok('product card carries a second photo for hover', await page.$('.pcard .pcard-alt'));
await page.goto(`${BASE}/products/${inStock[0].id}`, { waitUntil: 'networkidle0' });
ok('product page shows the multi-buy strip', (await page.$eval('.offer-strip', e => e.innerText).catch(() => '')).includes('buy 2+ save 5%'));
await page.type('#pin-p', '560001');
await page.click('.delivery .btn');
await page.waitForSelector('.delivery-msg.ok', { timeout: 5000 }).catch(() => {});
const eta = await page.$eval('.delivery-msg', e => e.innerText).catch(() => '');
ok('PIN check gives an estimated delivery range', /Estimated delivery .* to /.test(eta), eta.replace(/\s+/g, ' '));
await page.type('#pin-p', '', {});
await page.$eval('#pin-p', e => { e.value = ''; });
await page.click('#pin-p'); await page.keyboard.type('12345'); await page.click('.delivery .btn');
ok('bad PIN is refused politely', (await page.$eval('.delivery-msg.bad', e => e.innerText).catch(() => '')).includes('6-digit'));
await page.reload({ waitUntil: 'networkidle0' });
await page.waitForSelector('.delivery-msg.ok', { timeout: 5000 }).catch(() => {});
ok('last good PIN is remembered', await page.$eval('#pin-p', e => e.value) === '560001' || !!(await page.$('.delivery-msg.ok')));
await page.click('.breakup summary');
const breakup = await page.$eval('.breakup', e => e.innerText);
ok('price breakup shows GST and shipping', /GST \(\d+/.test(breakup) && /Shipping/.test(breakup), breakup.replace(/\s+/g, ' ').slice(0, 90));
await page.screenshot({ path: path.join(OUT, 'sh-02-pdp-tools.png') });

// ---------- 3. Personal shopping -> lead
await page.click('.vc-link');
await page.waitForSelector('.ps-form', { timeout: 5000 });
ok('video call page opens for this saree', (await page.$eval('.ps-product', e => e.innerText).catch(() => '')).includes(inStock[0].name));
await page.type('#vc-name', 'Kavya');
await page.type('#vc-reach', '9811122233');
ok('request needs consent first', await page.$eval('.ps-form .btn-primary', b => b.disabled));
await page.click('.ps-form .ai-consent input');
await page.click('.ps-form .btn-primary');
await page.waitForSelector('.ps-done', { timeout: 5000 }).catch(() => {});
ok('video call request is accepted', await page.$('.ps-done'));
await page.screenshot({ path: path.join(OUT, 'sh-03-personal-shopping.png') });
const lead = (await (await fetch(`${API}/admin/leads`, { headers: AH })).json()).find(l => l.phone === '9811122233');
ok('it arrives as a hot video-call lead with a ready message', lead && lead.temperature === 'hot' && lead.source === 'video_call' && /video call/i.test(lead.followUp), lead ? `${lead.score} ${lead.temperature}` : 'no lead');
await page.click('.ps-tabs button:nth-child(2)').catch(() => {});
await ctx.close();

// ---------- 4. Customer: cart with the offer, cross-sell, delivery, then checkout totals
await fetch(`${API}/cart`, { method: 'DELETE', headers: CH }).catch(() => {});
const items = await (await fetch(`${API}/cart`, { headers: CH })).json();
for (const i of items) await fetch(`${API}/cart/${i.id}`, { method: 'DELETE', headers: CH });
await fetch(`${API}/cart`, { method: 'POST', headers: CH, body: JSON.stringify({ productId: inStock[0].id, quantity: 1 }) });
({ ctx, page } = await open(cust));
await page.goto(BASE + '/cart', { waitUntil: 'networkidle0' });
await page.waitForSelector('.offer-progress', { timeout: 6000 }).catch(() => {});
ok('one saree: nudge to add one more', /Add 1 more saree to save 5%/.test((await page.$eval('.offer-progress', e => e.innerText).catch(() => '')).replace(/\s+/g, ' ')));
ok('cart suggests more sarees to complete the order', (await page.$$eval('.cross li', e => e.length)) >= 1);
await page.screenshot({ path: path.join(OUT, 'sh-04-cart-one.png'), fullPage: true });
await page.click('.cross li .btn');
await page.waitForFunction(() => /Buy 2\+ and save 5%/.test(document.querySelector('.summary')?.innerText || ''), { timeout: 6000 }).catch(() => {});
const summary = await page.$eval('.summary', e => e.innerText);
ok('two sarees: the 5% tier applies and shows as a saving row', /Buy 2\+ and save 5%\s*−/.test(summary) && /applied/.test(summary), summary.replace(/\s+/g, ' ').slice(0, 140));
const q = await (await fetch(`${API}/cart/quote`, { headers: CH })).json();
ok('cart total equals the server quote', Math.abs(money(await page.$eval('.summary-row.total', e => e.innerText)) - Number(q.total)) < 0.01, `(${q.total})`);
ok('quote has the offer discount', Number(q.offerDiscount) > 0 && q.offerLabel === 'Buy 2+ and save 5%');
await page.screenshot({ path: path.join(OUT, 'sh-05-cart-offer.png'), fullPage: true });
await page.goto(BASE + '/checkout', { waitUntil: 'networkidle0' });
await page.waitForSelector('.ck-totals', { timeout: 6000 }).catch(() => {});
ok('checkout totals list the multi-buy saving', /Buy 2\+ and save 5%/.test(await page.$eval('.ck-totals', e => e.innerText).catch(() => '')));
await ctx.close();

// ---------- 5. Order, then track it without signing in
const place = await fetch(`${API}/orders/checkout`, { method: 'POST', headers: { ...CH, 'Idempotency-Key': 'e2e-' + Date.now() },
  body: JSON.stringify({ shippingAddress: '12 Secret Lane, Bengaluru, Karnataka 560001', phoneNumber: '9876543210', paymentMethod: 'COD', expectedTotal: q.total }) });
const order = await place.json();
ok('order with the offer is placed at the quoted total', place.ok && Number(order.totalPrice) === Number(q.total) && Number(order.offerDiscountAmount) > 0, `(${place.status})`);
({ ctx, page } = await open(null));
await page.goto(BASE + '/track', { waitUntil: 'networkidle0' });
await page.type('#tk-order', order.trackingNumber);
await page.type('#tk-phone', '12345 67890');
await page.click('.track-form .btn-primary');
await page.waitForSelector('.track-error', { timeout: 5000 }).catch(() => {});
ok('wrong mobile number is refused without saying which detail was wrong', /couldn't find an order/i.test(await page.$eval('.track-error', e => e.innerText).catch(() => '')));
await page.$eval('#tk-phone', e => { e.value = ''; });
await page.click('#tk-phone'); await page.keyboard.type('+91 98765 43210');
await page.click('.track-form .btn-primary');
await page.waitForSelector('.track-result', { timeout: 5000 }).catch(() => {});
const result = await page.$eval('.track-result', e => e.innerText).catch(() => '');
ok('right details show the order and its timeline', result.includes(order.trackingNumber) && !!(await page.$('.track-result .otl')));
ok('the delivery address is never shown', !result.includes('Secret Lane') && !(await page.content()).includes('Secret Lane'));
await page.screenshot({ path: path.join(OUT, 'sh-06-track.png'), fullPage: true });
await ctx.close();

// ---------- 6. Phones
({ ctx, page } = await open(cust, 360, 780));
for (const url of ['/track', '/personal-shopping', '/cart', `/products/${inStock[0].id}`, '/admin?tab=coupons']) {
  const asAdmin = url.startsWith('/admin');
  if (asAdmin) await page.evaluate((s) => { localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); }, admin);
  await page.goto(BASE + url, { waitUntil: 'networkidle0' });
  await wait(300);
  ok(`mobile: ${url.split('?')[0]} fits the screen`, await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1));
}
await page.screenshot({ path: path.join(OUT, 'sh-07-admin-offers-mobile.png') });
await ctx.close();

ok('no JavaScript errors', errors.length === 0, errors.slice(0, 3).join(' | '));
await browser.close();
const failed = results.filter(x => !x.pass);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
