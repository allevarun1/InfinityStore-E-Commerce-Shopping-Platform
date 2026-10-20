// Device matrix: every page at 10 widths (320px phones to 1920px desktops) as visitor, customer and admin.
// Fails loudly on horizontal overflow (naming the culprit elements) or a page that renders no heading.
// Same local stack as storefront.e2e.mjs. Run: node e2e/devices.e2e.mjs
import puppeteer from 'puppeteer-core';
const BASE = 'http://127.0.0.1:4200';
const API = 'http://localhost:8080/api';
const WIDTHS = [320, 360, 390, 414, 768, 820, 1024, 1280, 1440, 1920];
const signin = async (u, p) => (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) })).json();
const cust = await signin('customer', 'customer123');
const admin = await signin('admin', 'admin123');
// give the customer something in the cart so checkout renders its full layout
await fetch(`${API}/cart`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${cust.token}` }, body: JSON.stringify({ productId: 3, quantity: 1 }) });

const ROLES = {
  visitor: { session: null, pages: ['/', '/products/1', '/cart', '/login', '/register', '/shipping', '/contact', '/privacy', '/?view=deals#products', '/nope-404'] },
  customer: { session: cust, pages: ['/checkout', '/orders', '/wishlist', '/profile', '/cart'] },
  admin: { session: admin, pages: ['/admin'] },
};

const b = await puppeteer.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: 'new', args: ['--no-sandbox'] });
const problems = [];
let checks = 0;
for (const [role, { session, pages }] of Object.entries(ROLES)) {
  const ctx = await b.createBrowserContext();
  const p = await ctx.newPage();
  await p.goto(BASE + '/', { waitUntil: 'networkidle0' });
  await p.evaluate((s) => {
    localStorage.setItem('infinity_analytics_consent', 'no');
    if (s) { localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); }
  }, session);
  for (const w of WIDTHS) {
    await p.setViewport({ width: w, height: w < 800 ? 844 : 900, isMobile: w < 800, hasTouch: w < 800 });
    for (const path of pages) {
      await p.goto(BASE + path, { waitUntil: 'networkidle0' });
      await new Promise(r => setTimeout(r, 250));
      checks++;
      const res = await p.evaluate(() => {
        const vw = document.documentElement.clientWidth;
        const over = document.documentElement.scrollWidth - vw;
        const culprits = over > 1 ? [...document.querySelectorAll('body *')]
          .filter(el => { const r = el.getBoundingClientRect(); return r.width > 0 && r.right > vw + 1 && getComputedStyle(el).position !== 'fixed'; })
          .filter(el => !el.closest('.chips, .catnav, .gallery-track, .qv-thumbs, .tiles-grid'))
          .slice(0, 4).map(el => `${el.tagName.toLowerCase()}.${[...el.classList].join('.')}`) : [];
        return { over, culprits, h1: !!document.querySelector('h1, h2') };
      });
      if (res.over > 1 || !res.h1) problems.push(`${role} ${w}px ${path}: overflow ${res.over}px ${res.culprits.join(', ')}${res.h1 ? '' : ' (no heading rendered)'}`);
    }
  }
  if (role === 'visitor') {
    for (const [w, name] of [[320, 'dev-320-home'], [768, 'dev-768-pdp'], [1920, 'dev-1920-home']]) {
      await p.setViewport({ width: w, height: 900, isMobile: w < 800, hasTouch: w < 800 });
      await p.goto(BASE + (name.includes('pdp') ? '/products/1' : '/'), { waitUntil: 'networkidle0' });
      await p.screenshot({ path: `${name}.png` });
    }
  }
  if (role === 'customer') {
    await p.setViewport({ width: 360, height: 800, isMobile: true, hasTouch: true });
    await p.goto(BASE + '/checkout', { waitUntil: 'networkidle0' });
    await p.screenshot({ path: 'dev-360-checkout.png', fullPage: true });
  }
  await ctx.close();
}
await b.close();
console.log(`${checks} page/width checks, ${problems.length} problems`);
problems.forEach(x => console.log('  ' + x));
