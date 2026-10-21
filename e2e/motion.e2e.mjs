// Motion regression suite: hero choreography, scroll reveals, fly-to-cart, ripples, ticker, progress bar,
// phone announcement rotation, and the prefers-reduced-motion fallbacks.
//
// LOCAL, THROWAWAY stack only (same setup as storefront.e2e.mjs). Run: node e2e/motion.e2e.mjs
import puppeteer from 'puppeteer-core';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const wait = (ms) => new Promise(r => setTimeout(r, ms));

const browser = await puppeteer.launch({
  executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new', args: ['--no-sandbox'],
});

async function open(reduce = false, mobile = false) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport(mobile ? { width: 390, height: 800, isMobile: true, hasTouch: true } : { width: 1366, height: 900 });
  if (reduce) await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  await page.evaluateOnNewDocument(() => localStorage.setItem('cookie-consent', 'declined'));
  await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
  return { page, errors };
}

// ---------- Desktop with motion
{
  const { page, errors } = await open();
  await wait(2600);
  ok('headline words arrive one by one and end visible', await page.$$eval('.hero-x h1 .word', w => w.length >= 6 && w.every(x => getComputedStyle(x).opacity === '1')));
  ok('headline keeps one readable sentence for screen readers', (await page.$eval('.hero-x h1 .sr-only', e => e.textContent)).startsWith('Drapes woven to be treasured'));
  const stats = await page.$eval('.hero-stats', e => e.innerText.replace(/\s+/g, ' '));
  const real = await page.evaluate(async () => (await fetch('http://localhost:8080/api/products').then(r => r.json())).length);
  ok('hero counters land on the real number of sarees', stats.startsWith(`${real} SAREES`), stats);
  ok('gold dust and glows are decorative only', await page.$eval('.hero-dust', e => e.getAttribute('aria-hidden') === 'true' && getComputedStyle(e).pointerEvents === 'none'));

  const m1 = await page.$eval('.marquee-track', e => getComputedStyle(e).transform);
  await wait(900);
  const m2 = await page.$eval('.marquee-track', e => getComputedStyle(e).transform);
  ok('ticker is moving', m1 !== m2, `${m1} -> ${m2}`);
  ok('ticker text is not repeated for screen readers', await page.$$eval('.marquee ul[aria-hidden="true"]', u => u.length === 1));

  const before = await page.$eval('.scroll-progress', e => e.style.getPropertyValue('--p'));
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight / 2));
  await wait(500);
  const after = await page.$eval('.scroll-progress', e => e.style.getPropertyValue('--p'));
  ok('scroll progress bar follows the page', Number(after) > Number(before) && Number(after) < 1, `${before} -> ${after}`);

  await page.evaluate(() => document.getElementById('products').scrollIntoView());
  await wait(1500);
  ok('product cards reveal as they scroll in', await page.$$eval('.pcard.rise.in', c => c.length >= 4));
  ok('every revealed card is fully opaque afterwards', await page.$$eval('.pcard.rise.in', c => c.every(x => getComputedStyle(x).opacity === '1')));

  // ripple
  await page.hover('.pcard');
  await page.click('.pcard .pcard-quick');
  const ghost = await page.waitForFunction(() => [...document.body.children].some(e => e.tagName === 'IMG' && e.style.position === 'fixed'), { timeout: 1500 }).then(() => true).catch(() => false);
  ok('product photo flies towards the cart', ghost);
  await page.waitForSelector('.drawer', { timeout: 3000 }).catch(() => {});
  ok('mini-cart opens after the photo lands', await page.$('.drawer'));
  await wait(300);
  ok('flying photo is removed once it lands', await page.evaluate(() => ![...document.body.children].some(e => e.tagName === 'IMG' && e.style.position === 'fixed')));
  ok('cart count badge shows 1', (await page.$eval('.badge-count', e => e.textContent)) === '1');
  await page.keyboard.press('Escape'); await wait(300);

  await page.evaluate(() => scrollTo(0, 0));
  await wait(300);
  const btn = await page.$('.hero-ctas .btn-ghost-light');
  const r = await btn.boundingBox();
  await page.mouse.click(r.x + 10, r.y + 10);
  ok('buttons ripple on press', await page.evaluate(() => document.querySelector('.has-ripple') !== null));

  ok('no console errors', errors.length === 0, JSON.stringify(errors.slice(0, 2)));
  await page.close();
}

// ---------- Phone: announcement shows one message at a time and rotates
{
  const { page } = await open(false, true);
  const shown = () => page.$$eval('.announce span', s => s.filter(x => getComputedStyle(x).display !== 'none').map(x => x.textContent));
  const a = await shown();
  await wait(4200);
  const b = await shown();
  ok('phone announcement shows exactly one line', a.length === 1 && b.length === 1, JSON.stringify([a, b]));
  ok('phone announcement rotates', a[0] !== b[0]);
  ok('phone has no horizontal scroll with the new motion', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  await page.close();
}

// ---------- Reduced motion: everything is simply there
{
  const { page, errors } = await open(true);
  await wait(500);
  ok('reduced motion: headline words are visible straight away', await page.$$eval('.hero-x h1 .word', w => w.every(x => getComputedStyle(x).opacity === '1')));
  ok('reduced motion: ticker does not scroll and shows one copy', await page.evaluate(() => getComputedStyle(document.querySelector('.marquee-track')).animationName === 'none'
    && getComputedStyle(document.querySelector('.marquee ul[aria-hidden]')).display === 'none'));
  await page.evaluate(() => document.getElementById('products').scrollIntoView());
  await wait(300);
  ok('reduced motion: cards are visible without waiting', await page.$$eval('.pcard', c => c.length > 0 && c.every(x => getComputedStyle(x).opacity === '1')));
  const stats = await page.$eval('.hero-stats', e => e.innerText.replace(/\s+/g, ' '));
  ok('reduced motion: counters show real numbers at once', !stats.startsWith('0 '), stats);
  await page.hover('.pcard');
  await page.click('.pcard .pcard-quick');
  await page.waitForSelector('.drawer', { timeout: 1200 }).catch(() => {});
  ok('reduced motion: mini-cart opens with no flight delay', await page.$('.drawer'));
  ok('reduced motion: no scroll progress bar', await page.$eval('.scroll-progress', e => getComputedStyle(e).display === 'none'));
  ok('reduced motion: no console errors', errors.length === 0, JSON.stringify(errors.slice(0, 2)));
  await page.close();
}

await browser.close();
const failed = results.filter(r => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
