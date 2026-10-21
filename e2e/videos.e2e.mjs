// Video and storytelling suite: animated loom fallback, owner adds/pauses/removes videos from the admin,
// the "Behind the weave" story, reels strip + full-screen viewer, YouTube click-to-load, product clip,
// and the reduced-motion behaviour. Uses fake https links, so it checks structure and behaviour, not codecs.
//
// LOCAL, THROWAWAY stack only (see storefront.e2e.mjs). Run: node e2e/videos.e2e.mjs
import puppeteer from 'puppeteer-core';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4200';
const API = process.env.API_URL || 'http://localhost:8080/api';
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };
const wait = (ms) => new Promise(r => setTimeout(r, ms));

const admin = await (await fetch(`${API}/auth/signin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: process.env.ADMIN_PASSWORD || 'admin123' }) })).json();
const products = await (await fetch(`${API}/products`)).json();
const saree = products[0];

const browser = await puppeteer.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: 'new', args: ['--no-sandbox'] });
const errors = [];
async function open(reduce = false, asAdmin = false) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error' && !/favicon|Failed to load resource|ERR_/.test(m.text())) errors.push(m.text()); });
  await page.setViewport({ width: 1366, height: 900 });
  if (reduce) await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
  await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
  await page.evaluate((s, a) => {
    localStorage.setItem('infinity_analytics_consent', 'no');
    if (a) { localStorage.setItem('infinity_token', s.token); localStorage.setItem('infinity_user', JSON.stringify(s)); }
  }, admin, asAdmin);
  return page;
}

// ---------- 0. The seeded demo: real motion-design clips hosted on the site, and labelled sample quotes
let page = await open();
await page.reload({ waitUntil: 'networkidle0' });
await page.evaluate(() => document.querySelector('.craft').scrollIntoView({ block: 'center' }));
await wait(2500);
const story = await page.$eval('.craft video', v => ({ src: v.getAttribute('src'), w: v.videoWidth, h: v.videoHeight, d: v.duration, ready: v.readyState, paused: v.paused, t: v.currentTime }));
ok('demo story clip loads from the site', story.src === '/videos/story-collection.mp4' && story.w === 720 && story.h === 900 && story.d > 10, JSON.stringify(story));
ok('demo story clip is actually playing in view', !story.paused && story.t > 0.3, `t=${story.t}`);
const reelsOk = await page.$$eval('.reel video', vs => vs.map(v => v.getAttribute('src')));
ok('nineteen cinematic demo reels are present', reelsOk.length === 19 && reelsOk.every(u => u.startsWith('/videos/cine-')), JSON.stringify(reelsOk));
await page.evaluate(() => document.querySelector('.reel').scrollIntoView({ block: 'center' }));
await wait(2500);
const reelV = await page.$eval('.reel video', v => ({ w: v.videoWidth, h: v.videoHeight, d: v.duration }));
ok('a cinematic reel decodes as a portrait clip', reelV.w === 540 && reelV.h === 960 && reelV.d > 6, JSON.stringify(reelV));
const poster = await page.$eval('.reel video', v => v.getAttribute('poster'));
ok('reel cover image loads', await page.evaluate(async (u) => (await fetch(u)).ok, poster));
await page.evaluate(() => document.querySelector('.voices')?.scrollIntoView({ block: 'center' }));
await wait(1200);
ok('testimonials section shows', await page.$('.voices-card blockquote'));
ok('demo quotes are visibly labelled as samples', (await page.$eval('.voices-sample', e => e.textContent)) === 'Sample');
const first = await page.$eval('.voices blockquote', e => e.textContent);
await page.click('.voices-dots button[aria-current="false"]');
await wait(500);
ok('dots switch the testimonial', (await page.$eval('.voices blockquote', e => e.textContent)) !== first);
ok('five stars render as icons with a text label', await page.$eval('.voices-stars', e => e.getAttribute('aria-label').endsWith('out of 5 stars') && e.querySelectorAll('i').length === 5));
await page.close();

// start the rest from a clean slate
for (const v of await (await fetch(`${API}/admin/videos`, { headers: { Authorization: `Bearer ${admin.token}` } })).json()) {
  await fetch(`${API}/admin/videos/${v.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${admin.token}` } });
}

// ---------- 1. No videos yet: the animated loom and the four steps
page = await open();
await page.reload({ waitUntil: 'networkidle0' });
ok('no video yet: an animated loom is shown instead', await page.$('.craft svg.loom'));
ok('the four process steps are listed', (await page.$$eval('.craft-steps li', l => l.length)) === 4);
ok('no reels strip without reels', !(await page.$('.reels')));
ok('thread divider is decorative', await page.$eval('.thread', e => e.getAttribute('aria-hidden') === 'true'));
await page.close();

// ---------- 2. Owner adds videos in the admin
page = await open(false, true);
await page.goto(BASE + '/admin?tab=videos', { waitUntil: 'networkidle0' });
await page.waitForSelector('.videos-form', { timeout: 8000 });
ok('Videos tab explains where to host clips', (await page.$eval('.videos-admin .admin-hint', e => e.innerText)).includes('.mp4'));

async function addVideo({ title, kind, url, productId }) {
  await page.$eval('#v-title', e => (e.value = ''));
  await page.type('#v-title', title);
  await page.select('#v-kind', kind);
  await page.$eval('#v-url', e => (e.value = ''));
  await page.type('#v-url', url);
  if (productId) await page.select('#v-prod', String(productId));
  await page.click('.videos-form button[type="submit"]');
  await wait(900);
}
await addVideo({ title: 'Insecure clip', kind: 'REEL', url: 'http://cdn.example.com/a.mp4' });
ok('an http link is refused with a clear message', (await page.evaluate(() => document.body.innerText)).includes('https'));
ok('nothing was added by the refused link', (await page.$$('.videos-admin .videos-list li')).length === 0);
await addVideo({ title: 'From loom to you', kind: 'STORY', url: 'https://cdn.example.com/loom.mp4' });
await addVideo({ title: 'Silk in motion', kind: 'REEL', url: 'https://cdn.example.com/silk.mp4', productId: saree.id });
await addVideo({ title: 'Festive drape', kind: 'REEL', url: 'https://youtu.be/dQw4w9WgXcQ' });
await addVideo({ title: 'To pause', kind: 'REEL', url: 'https://cdn.example.com/pause.mp4' });
ok('four videos listed', (await page.$$('.videos-admin .videos-list li')).length === 4);
await page.evaluate(() => [...document.querySelectorAll('.videos-admin .videos-list li')].find(l => l.innerText.includes('To pause')).querySelectorAll('button')[0].click());
await wait(700);
const pubAfterPause = await (await fetch(`${API}/store/videos`)).json();
ok('a paused video disappears from the public list', pubAfterPause.length === 3 && !pubAfterPause.some(v => v.title === 'To pause'));
await page.evaluate(() => [...document.querySelectorAll('.videos-admin .videos-list li')].find(l => l.innerText.includes('To pause')).querySelector('.danger-btn').click());
await wait(700);
ok('a removed video is gone', (await page.$$('.videos-admin .videos-list li')).length === 3);
await page.close();

// ---------- 3. Storefront: story, reels, viewer, YouTube, product clip
page = await open();
await page.reload({ waitUntil: 'networkidle0' });
ok('story video replaces the loom', (await page.$eval('.craft video', v => v.getAttribute('src'))) === 'https://cdn.example.com/loom.mp4' && !(await page.$('.craft svg.loom')));
ok('story title and steps show', (await page.$eval('#craft-title', e => e.textContent)) === 'From loom to you' && (await page.$$('.craft-steps li')).length === 4);
ok('story video starts muted and loops (autoplay-safe)', await page.$eval('.craft video', v => v.muted && v.loop && v.playsInline));
ok('two reels in the strip', (await page.$$('.reel')).length === 2);
ok('reels have readable names for screen readers', (await page.$$eval('.reel', r => r.map(x => x.getAttribute('aria-label')))).every(l => l.startsWith('Watch: ')));

await page.evaluate(() => document.querySelector('.reel').scrollIntoView({ block: 'center' }));
await wait(800);
await page.click('.reel');
await page.waitForSelector('.rv[role="dialog"]', { timeout: 3000 }).catch(() => {});
ok('tapping a reel opens the full-screen viewer', await page.$('.rv[role="dialog"]'));
ok('viewer offers the linked saree', (await page.$eval('.rv-info a', e => e.textContent).catch(() => '')).includes(saree.name));
ok('page behind the viewer does not scroll', await page.evaluate(() => document.body.classList.contains('no-scroll')));
await page.click('.rv-nav.next');
await wait(400);
ok('next shows the YouTube clip as a cover, not an iframe', (await page.$('.rv .vp-cover')) && !(await page.$('.rv iframe')));
await page.click('.rv .vp-cover');
await wait(400);
const src = await page.$eval('.rv iframe', f => f.src).catch(() => '');
ok('YouTube loads only after the tap, from the privacy domain', src.startsWith('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ'), src);
await page.keyboard.press('Escape');
await wait(300);
ok('Escape closes the viewer and frees the page', !(await page.$('.rv')) && !(await page.evaluate(() => document.body.classList.contains('no-scroll'))));

// product page clip
await page.goto(`${BASE}/products/${saree.id}`, { waitUntil: 'networkidle0' });
ok('product page shows "Watch it drape" for a linked clip', await page.$('.watch-btn'));
await page.click('.watch-btn');
await page.waitForSelector('.rv', { timeout: 3000 }).catch(() => {});
ok('it opens that saree\'s clip', (await page.$eval('.rv-info strong', e => e.textContent).catch(() => '')) === 'Silk in motion');
await page.keyboard.press('Escape');
const other = products.find(p => p.id !== saree.id);
await page.goto(`${BASE}/products/${other.id}`, { waitUntil: 'networkidle0' });
ok('a saree without a clip has no watch button', !(await page.$('.watch-btn')));

// card badge
await page.goto(BASE + '/', { waitUntil: 'networkidle0' });
ok('the saree with a clip shows a video badge on its card', (await page.$$eval('.pcard', cs => cs.filter(c => c.querySelector('.pcard-play')).length)) === 1);
await page.close();

// ---------- 4. Reduced motion: nothing starts by itself
page = await open(true);
await page.reload({ waitUntil: 'networkidle0' });
await page.evaluate(() => document.querySelector('.craft video').scrollIntoView({ block: 'center' }));
await wait(1200);
ok('reduced motion: story video does not autoplay and offers a play button', await page.$eval('.craft video', v => v.paused) && await page.$('.craft .vp-bigplay'));
ok('reduced motion: thread is already drawn', await page.$eval('.thread path', p => getComputedStyle(p).strokeDashoffset === '0px'));
await page.close();

// clean up what the suite created
const all = await (await fetch(`${API}/admin/videos`, { headers: { Authorization: `Bearer ${admin.token}` } })).json();
for (const v of all) await fetch(`${API}/admin/videos/${v.id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${admin.token}` } });
ok('suite cleaned up its videos', (await (await fetch(`${API}/store/videos`)).json()).length === 0);

ok('no JavaScript errors', errors.length === 0, JSON.stringify(errors.slice(0, 3)));
await browser.close();
const failed = results.filter(r => !r.pass);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
