// Growth engine browser suite: shopping stylist chat, back-in-stock alerts, Autopilot and Leads.
//
// LOCAL, THROWAWAY stack only (same setup as storefront.e2e.mjs): it marks a product sold out and back
// in stock through the admin API and leaves leads behind. Restart the backend between runs.
//   CHROME_PATH=<chrome> node e2e/growth.e2e.mjs
import puppeteer from 'puppeteer-core';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const API = process.env.API_URL || 'http://localhost:8080/api';
const OUT = path.resolve(process.env.SHOT_DIR || '.');
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const wait = (ms) => new Promise(r => setTimeout(r, ms));
const num = (t) => Number(String(t).replace(/[^\d.]/g, ''));

const signin = async (u, p) => (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) })).json();
const admin = await signin('admin', process.env.ADMIN_PASSWORD || 'admin123');
const H = { Authorization: `Bearer ${admin.token}` };

// ---------- 0. Setup: one saree sold out
const all = await (await fetch(`${API}/products`)).json();
const soldOut = all[all.length - 1];
await fetch(`${API}/products/${soldOut.id}/stock?inStock=false`, { method: 'PUT', headers: H });
ok('setup: a saree is marked sold out', (await (await fetch(`${API}/products/${soldOut.id}`)).json()).stockQuantity === 0);

const browser = await puppeteer.launch({
  executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new',
  args: ['--no-sandbox'],
});
const errors = [];
const watch = (p) => { p.on('pageerror', e => errors.push(e.message)); p.on('console', m => { if (m.type() === 'error' && !/favicon|postalpincode/.test(m.text())) errors.push(m.text()); }); };

const ctx = await browser.createBrowserContext();
let page = await ctx.newPage();
watch(page);
await page.setViewport({ width: 1280, height: 900 });
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.evaluate(() => localStorage.setItem('infinity_analytics_consent', 'no'));
await page.reload({ waitUntil: 'networkidle0' });

const lastBot = () => page.$$eval('.ai-msg.bot', els => {
  const m = els[els.length - 1];
  return { text: m.querySelector('.ai-bubble')?.innerText || '', cards: [...m.querySelectorAll('.ai-card')].map(c => c.querySelector('.ai-card-price strong')?.innerText || ''), contact: !!m.querySelector('.ai-contact') };
});
const say = async (text) => {
  const before = await page.$$eval('.ai-msg.bot', e => e.length);
  await page.type('.ai-input input', text);
  await page.keyboard.press('Enter');
  await page.waitForFunction((n) => document.querySelectorAll('.ai-msg.bot').length > n && !document.querySelector('.ai-typing'), { timeout: 8000 }, before);
  await wait(150);
  return lastBot();
};

// ---------- 1. Stylist chat
ok('stylist launcher is on the home page', await page.$('.ai-fab'));
const fab = await page.$eval('.ai-fab', e => e.getBoundingClientRect().toJSON());
const wa = await page.$eval('.wa-fab', e => e.getBoundingClientRect().toJSON());
ok('launcher does not cover the WhatsApp button', fab.bottom <= wa.top || fab.right <= wa.left, `(fab bottom ${Math.round(fab.bottom)}, wa top ${Math.round(wa.top)})`);
await page.click('.ai-fab');
await page.waitForSelector('.ai-panel');
ok('stylist opens with a welcome and quick ideas', (await page.$$eval('.ai-chip', e => e.length)) >= 3);

await page.evaluate(() => [...document.querySelectorAll('.ai-chip')].find(b => b.textContent.includes('Wedding')).click());
await page.waitForFunction(() => document.querySelectorAll('.ai-msg.bot').length >= 2 && !document.querySelector('.ai-typing'), { timeout: 8000 });
await wait(150);
let r = await lastBot();
ok('"Wedding sarees" returns real saree cards', r.cards.length >= 1, `(${r.cards.length}: ${r.text.slice(0, 60)})`);

r = await say('silk under 10000');
ok('budget is respected (silk under ₹10,000)', r.cards.length > 0 && r.cards.every(p => num(p) <= 10000), r.cards.join(' '));
const wide = r.cards.length;
r = await say('make it under 3000');
ok('follow-up narrows the same search', r.cards.every(p => num(p) <= 3000) && (r.cards.length < wide || r.cards.length === 0 || /closest|couldn/i.test(r.text)), `(${r.cards.join(' ')}) ${r.text.slice(0, 50)}`);

r = await say('start over');
r = await say('show me festive sarees');
const addBtn = await page.$('.ai-msg.bot:last-child .ai-add:not(.ghost)');
ok('festive search shows cards with Add to bag', !!addBtn);
if (addBtn) {
  await addBtn.click();
  await page.waitForFunction(() => [...document.querySelectorAll('.ai-add')].some(b => b.disabled && /bag/i.test(b.textContent)), { timeout: 5000 }).catch(() => {});
  ok('Add to bag from the chat works', await page.evaluate(() => [...document.querySelectorAll('.ai-add')].some(b => b.disabled && /in your bag/i.test(b.textContent))));
  // Close the "added" mini-cart drawer if it opened.
  await page.keyboard.press('Escape'); await wait(200);
  if (!(await page.$('.ai-panel'))) { await page.click('.ai-fab'); await page.waitForSelector('.ai-panel'); }
}

r = await say('is cash on delivery available?');
ok('answers the COD question', /cash on delivery/i.test(r.text), r.text.slice(0, 60));
r = await say('what are the delivery charges');
ok('answers shipping from real settings', /₹999/.test(r.text), r.text.slice(0, 70));
await page.screenshot({ path: path.join(OUT, 'ai-01-stylist-desktop.png') });

r = await say('can I talk to a person');
ok('"talk to a person" offers a call-back form', r.contact);
const phone = '9876501234';
await page.type('.ai-msg.bot:last-child .ai-contact input[aria-label="Your name"]', 'Meera');
await page.type('.ai-msg.bot:last-child .ai-contact input[aria-label="WhatsApp number or email"]', phone);
await page.click('.ai-msg.bot:last-child .ai-consent input');
await page.click('.ai-msg.bot:last-child .ai-contact .btn');
await page.waitForSelector('.ai-contact.done', { timeout: 5000 }).catch(() => {});
ok('call-back request is accepted', await page.$('.ai-contact.done'));

await page.reload({ waitUntil: 'networkidle0' });
await page.click('.ai-fab');
await page.waitForSelector('.ai-panel');
ok('conversation survives a page reload', (await page.$$eval('.ai-msg', e => e.length)) >= 10);
await page.keyboard.press('Escape'); await wait(200);
ok('Escape closes the stylist', !(await page.$('.ai-panel')));

for (const p of ['/login', '/checkout']) {
  await page.goto(BASE + p, { waitUntil: 'networkidle0' });
  ok(`stylist stays out of the way on ${p}`, !(await page.$('.ai-fab')));
}

// ---------- 2. Back-in-stock on a sold-out saree
await page.goto(`${BASE}/products/${soldOut.id}`, { waitUntil: 'networkidle0' });
ok('sold-out saree shows "Notify me"', await page.$('.notify'));
const waitEmail = `waiting${Date.now()}@example.com`;
await page.type('.notify input[aria-label="Email or WhatsApp number"]', waitEmail);
ok('Notify me needs consent first', await page.$eval('.notify .btn', b => b.disabled));
await page.click('.notify-consent input');
await page.click('.notify .btn');
await page.waitForSelector('.notify.done', { timeout: 5000 }).catch(() => {});
ok('back-in-stock request is saved', await page.$('.notify.done'));
await page.screenshot({ path: path.join(OUT, 'ai-02-notify-me.png') });

// ---------- 3. Phones
await page.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true });
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
const mf = await page.$eval('.ai-fab', e => e.getBoundingClientRect().toJSON());
const mw = await page.$eval('.wa-fab', e => e.getBoundingClientRect().toJSON());
const nav = await page.$eval('.bottomnav', e => e.getBoundingClientRect().toJSON()).catch(() => null);
ok('mobile: launcher sits above WhatsApp and the tab bar', mf.bottom <= mw.top && (!nav || mf.bottom <= nav.top), `(fab ${Math.round(mf.top)}-${Math.round(mf.bottom)}, wa ${Math.round(mw.top)}, nav ${nav && Math.round(nav.top)})`);
ok('mobile: launcher is a 48px touch target', mf.width >= 44 && mf.height >= 44);
await page.click('.ai-fab');
await page.waitForSelector('.ai-panel');
await wait(450); // let the opening animation finish before measuring
const pw = await page.$eval('.ai-panel', e => e.getBoundingClientRect().width);
ok('mobile: stylist opens full screen', Math.abs(pw - 390) < 2, `(${pw}px)`);
ok('mobile: no sideways scrolling with the chat open', await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1));
await page.screenshot({ path: path.join(OUT, 'ai-03-stylist-mobile.png') });
await ctx.close();

// ---------- 4. Autopilot (owner)
const actx = await browser.createBrowserContext();
page = await actx.newPage();
watch(page);
await page.setViewport({ width: 1366, height: 900 });
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
await page.evaluate((s) => { localStorage.setItem('infinity_analytics_consent', 'no'); localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); }, admin);
await page.goto(BASE + '/admin', { waitUntil: 'networkidle0' });
await page.waitForSelector('.ap-hero', { timeout: 8000 }).catch(() => {});
ok('admin lands on Autopilot', await page.$('.ap-hero'));
const tasks = await page.$$eval('.ap-task', els => els.map(e => e.innerText));
ok('to-do list asks to restock the saree people are waiting for', tasks.some(t => t.includes(soldOut.name) && /waiting/i.test(t)), `(${tasks.length} tasks)`);
ok('to-do list includes following up the new lead', tasks.some(t => /follow up/i.test(t)));
ok('four automations with Off / Ask me first / Automatic', (await page.$$eval('.ap-rule .ap-seg', e => e.length)) === 4);
await page.screenshot({ path: path.join(OUT, 'ai-04-autopilot.png'), fullPage: true });

await page.evaluate(() => {
  const rule = [...document.querySelectorAll('.ap-rule')].find(r => r.innerText.includes('Review requests'));
  [...rule.querySelectorAll('.ap-seg button')].find(b => b.textContent === 'Off').click();
});
await page.waitForFunction(() => {
  const rule = [...document.querySelectorAll('.ap-rule')].find(r => r.innerText.includes('Review requests'));
  return rule && rule.querySelector('.ap-seg button.on')?.textContent === 'Off';
}, { timeout: 5000 }).catch(() => {});
const mode = await (await fetch(`${API}/admin/autopilot`, { headers: H })).json();
ok('switching a rule off is saved on the server', mode.rules.find(x => x.key === 'REVIEW_REQUEST').mode === 'OFF');
await fetch(`${API}/admin/autopilot/rules/REVIEW_REQUEST?mode=PREVIEW`, { method: 'PUT', headers: H });

// The saree comes back: Run now must prepare the alert for the waiting shopper.
await fetch(`${API}/products/${soldOut.id}/stock?inStock=true`, { method: 'PUT', headers: H });
await page.evaluate(() => [...document.querySelectorAll('.ap-hero .btn')].find(b => /run now/i.test(b.textContent)).click());
await page.waitForFunction(() => [...document.querySelectorAll('.ap-msg-title')].some(t => /back in stock/i.test(t.textContent)), { timeout: 8000 }).catch(() => {});
const alert = await page.evaluateHandle(() => [...document.querySelectorAll('.ap-msg')].find(m => /back in stock/i.test(m.innerText)));
ok('Run now prepares the back-in-stock message', alert.asElement());
if (alert.asElement()) {
  await (await alert.asElement().$('.ap-msg-line')).click();
  await wait(200);
  const body = await page.$eval('.ap-preview pre', e => e.innerText).catch(() => '');
  ok('message preview has the saree and its link', body.includes(soldOut.name) && body.includes(`/products/${soldOut.id}`));
  ok('without email set up it says so instead of pretending to send', /waiting for email setup|ready for you/i.test(await alert.asElement().evaluate(m => m.innerText)));
}
await page.screenshot({ path: path.join(OUT, 'ai-05-autopilot-message.png'), fullPage: true });
const again = await (await fetch(`${API}/admin/autopilot/run`, { method: 'POST', headers: H })).json();
ok('running again does not repeat the alert', again.byRule.BACK_IN_STOCK === 0, JSON.stringify(again.byRule));

// ---------- 5. Leads
await page.evaluate(() => [...document.querySelectorAll('.admin-tabs button')].find(b => /leads/i.test(b.textContent)).click());
await page.waitForSelector('.lead', { timeout: 8000 }).catch(() => {});
const meera = await page.evaluateHandle(() => [...document.querySelectorAll('.lead')].find(l => l.innerText.includes('Meera')));
ok('the stylist call-back request is in Leads', meera.asElement());
if (meera.asElement()) {
  const info = await meera.asElement().evaluate(l => ({ temp: l.querySelector('.lead-score span').textContent, wa: l.querySelector('a.wa')?.href || '' }));
  ok('it is scored hot with reasons', info.temp === 'hot', info.temp);
  ok('WhatsApp follow-up is ready with their number', info.wa.startsWith(`https://wa.me/91${phone}?text=`));
  await meera.asElement().$eval('select', s => { s.value = 'CONTACTED'; s.dispatchEvent(new Event('change', { bubbles: true })); });
  await wait(600);
  const leadsNow = await (await fetch(`${API}/admin/leads`, { headers: H })).json();
  ok('status change is saved', leadsNow.find(l => l.phone === phone)?.status === 'CONTACTED');
}
ok('the back-in-stock shopper is a lead too', await page.evaluate((e) => document.body.innerText.includes(e), waitEmail));
await page.screenshot({ path: path.join(OUT, 'ai-06-leads.png'), fullPage: true });

await page.setViewport({ width: 375, height: 812, isMobile: true, hasTouch: true });
await page.goto(BASE + '/admin?tab=leads', { waitUntil: 'networkidle0' });
await page.waitForSelector('.lead', { timeout: 8000 }).catch(() => {});
ok('?tab=leads opens Leads directly', await page.$('.leads-head'));
ok('mobile: Leads fits the screen', await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1));
await page.screenshot({ path: path.join(OUT, 'ai-07-leads-mobile.png'), fullPage: true });
await page.goto(BASE + '/admin', { waitUntil: 'networkidle0' });
await page.waitForSelector('.ap-hero', { timeout: 8000 }).catch(() => {});
ok('mobile: Autopilot fits the screen', await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1));
await page.screenshot({ path: path.join(OUT, 'ai-08-autopilot-mobile.png'), fullPage: true });
await actx.close();

ok('no JavaScript errors', errors.length === 0, errors.slice(0, 3).join(' | '));
await browser.close();
const failed = results.filter(x => !x.pass);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
