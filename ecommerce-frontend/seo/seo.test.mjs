// Run with:  node --test seo/      (Node 20+; no packages needed)
import test from 'node:test';
import assert from 'node:assert/strict';
import { handlePage, handleSitemap, classify, buildSitemap } from './handler.js';
import fs from 'node:fs';
import { jsonForScript, productTitle, productDescription, truncate } from './meta.js';
import { SECURITY_HEADERS } from './security.js';

const SHELL = `<!doctype html><html lang="en"><head><title>old</title>
<meta name="description" content="old" /><meta property="og:title" content="old" /><meta name="twitter:card" content="x" />
<link rel="canonical" href="https://old"></head><body><div id="root"></div></body></html>`;

const P = (id, name, price, extra = {}) => ({
  id, name, description: 'Line one\nLine two', price, stockQuantity: 5, deal: false, dealPrice: null, sku: null,
  imageUrl: `/api/media/${id}/abcdef1234`, gallery: [], ...extra,
});
const PRODUCTS = [
  P(23, "VINTAGE PATTU SAREE'S", 900, { sku: 'VPS', gallery: ['/api/media/99/1234567890'] }),
  P(25, "FANCY WORK SAREE'S", 450),
  P(26, "FANCY WORK SAREE'S", 450),
  P(30, 'CATLOG SAREE', 600, { stockQuantity: 0 }),
  P(31, 'Evil </script><img src=x onerror=alert(1)>', 100, { description: '</script><b>x</b>' }),
];
const INFO = { shippingFee: 80, freeShippingThreshold: null, supportEmail: 'a@b.in', supportPhone: '+91 1', whatsappNumber: '911', cashOnDelivery: false };

function fakeApi({ list = PRODUCTS, info = INFO, single = {}, fail = false } = {}) {
  return async (url) => {
    if (fail) throw new Error('down');
    const u = new URL(url);
    const ok = (data) => new Response(JSON.stringify(data), { status: 200 });
    if (u.pathname === '/api/products') return list ? ok(list) : new Response('x', { status: 500 });
    if (u.pathname === '/api/store/info') return info ? ok(info) : new Response('x', { status: 500 });
    const m = /^\/api\/products\/(\d+)$/.exec(u.pathname);
    if (m) {
      const s = single[m[1]];
      return s === undefined ? new Response('', { status: 404 }) : typeof s === 'number' ? new Response('', { status: s }) : ok(s);
    }
    return new Response('', { status: 404 });
  };
}

async function get(path, opts = {}, method = 'GET') {
  const calls = { next: 0 };
  const res = await handlePage(new Request(`https://${opts.host || 'rekhashoppingmall.com'}${path}`, { method }), opts.env || {}, {
    fetchFn: opts.fetchFn || fakeApi(),
    getShell: async () => new Response(SHELL, { headers: { 'content-type': 'text/html', 'content-security-policy': "default-src 'self'", etag: '"x"' } }),
    next: async () => { calls.next++; return opts.nextResponse || new Response(SHELL, { headers: { 'content-type': 'text/html' } }); },
  });
  return { res, text: await res.text(), calls };
}

const ld = (html) => JSON.parse(/<script type="application\/ld\+json" id="product-jsonld">([\s\S]*?)<\/script>/.exec(html)[1]);

test('classify', () => {
  assert.equal(classify('/').kind, 'home');
  assert.deepEqual(classify('/products/23'), { kind: 'product', id: 23 });
  for (const bad of ['/products/0', '/products/023', '/products/abc', '/products/1/x', '/products', '/about', '/returns', '/category/1']) {
    assert.equal(classify(bad).kind, 'notfound', bad);
  }
  assert.equal(classify('/shipping').kind, 'info');
  assert.equal(classify('/cart').kind, 'utility');
});

test('valid product: 200, real content, head, JSON-LD', async () => {
  const { res, text } = await get('/products/23');
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /text\/html/);
  assert.equal(res.headers.get('cache-control'), 'no-cache');
  assert.equal(res.headers.get('content-security-policy'), "default-src 'self'", 'security headers preserved');
  assert.equal(res.headers.get('etag'), null);
  assert.match(text, /<title>VINTAGE PATTU SAREE&#39;S \| Rekha Shopping Mall<\/title>/);
  assert.match(text, /<link rel="canonical" href="https:\/\/rekhashoppingmall\.com\/products\/23">/);
  assert.match(text, /<meta property="og:image" content="https:\/\/api\.rekhashoppingmall\.com\/api\/media\/23\/abcdef1234">/);
  assert.match(text, /<h1>VINTAGE PATTU SAREE&#39;S<\/h1>/);
  assert.match(text, /Price: ₹900/);
  assert.match(text, /Availability: In stock/);
  assert.match(text, /data-seo-product="23"/);
  assert.equal((text.match(/<title>/g) || []).length, 1);
  assert.equal((text.match(/rel="canonical"/g) || []).length, 1);
  assert.doesNotMatch(text, /content="old"/);
  const g = ld(text)['@graph'];
  const prod = g.find(n => n['@type'] === 'Product');
  assert.equal(prod.name, "VINTAGE PATTU SAREE'S");
  assert.equal(prod.sku, 'VPS');
  assert.equal(prod.offers.price, '900.00');
  assert.equal(prod.offers.priceCurrency, 'INR');
  assert.equal(prod.offers.availability, 'https://schema.org/InStock');
  assert.equal(prod.offers.shippingDetails.shippingRate.value, '80.00');
  assert.ok(!('hasMerchantReturnPolicy' in prod.offers), 'no return-policy schema until the policy is legally reviewed');
  assert.ok(!JSON.stringify(g).includes('MerchantReturn'));
  assert.equal(prod.image.length, 2);
  assert.ok(!('brand' in prod) && !('aggregateRating' in prod) && !('review' in prod), 'nothing invented');
  assert.equal(g.find(n => n['@type'] === 'BreadcrumbList').itemListElement.length, 2);
});

test('no SKU when the product has none; sold out is reflected; deal price is used', async () => {
  const sold = await get('/products/30');
  const prod = ld(sold.text)['@graph'][0];
  assert.ok(!('sku' in prod));
  assert.equal(prod.offers.availability, 'https://schema.org/OutOfStock');
  assert.match(sold.text, /Availability: Sold out/);
  const deal = P(40, 'Deal saree', 1000, { deal: true, dealPrice: 800 });
  const d = await get('/products/40', { fetchFn: fakeApi({ list: [deal] }) });
  assert.equal(ld(d.text)['@graph'][0].offers.price, '800.00');
});

test('titles and descriptions are unique even for same-named products', () => {
  const titles = PRODUCTS.map(p => productTitle(p, PRODUCTS));
  const descs = PRODUCTS.map(p => productDescription(p, PRODUCTS));
  assert.equal(new Set(titles).size, PRODUCTS.length);
  assert.equal(new Set(descs).size, PRODUCTS.length);
  assert.match(productTitle(PRODUCTS[1], PRODUCTS), /\(Item 25\)/);
  assert.doesNotMatch(productTitle(PRODUCTS[0], PRODUCTS), /Item/);
  for (const d of descs) assert.ok(d.length <= 160, d);
  assert.equal(truncate('a b c', 10), 'a b c');
});

test('markup in product data cannot break out of the page', async () => {
  const { text } = await get('/products/31');
  assert.doesNotMatch(text, /<img src=x/);
  assert.doesNotMatch(text, /<\/script><img/);
  assert.equal(ld(text)['@graph'][0].name, 'Evil </script><img src=x onerror=alert(1)>');
  assert.equal(jsonForScript({ a: '<' }), '{"a":"\\u003c"}');
});

test('missing product 404, API 410 passes through, outage 503, and none of them cache', async () => {
  const missing = await get('/products/999');
  assert.equal(missing.res.status, 404);
  assert.match(missing.text, /noindex/);
  assert.equal(missing.res.headers.get('x-robots-tag'), 'noindex');
  assert.doesNotMatch(missing.text, /seo-shell/);
  const gone = await get('/products/999', { fetchFn: fakeApi({ single: { 999: 410 } }) });
  assert.equal(gone.res.status, 410);
  const down = await get('/products/23', { fetchFn: fakeApi({ fail: true }) });
  assert.equal(down.res.status, 503);
  assert.equal(down.res.headers.get('retry-after'), '120');
  assert.equal(down.res.headers.get('cache-control'), 'no-store');
  const listDown = await get('/products/23', { fetchFn: fakeApi({ list: null, single: { 23: PRODUCTS[0] } }) });
  assert.equal(listDown.res.status, 200, 'falls back to the single-product call');
});

test('unknown routes are 404 (app still loads); utility pages are 200 noindex; info pages are indexable', async () => {
  for (const p of ['/nope', '/about', '/returns', '/category/1', '/products', '/products/abc']) {
    const r = await get(p);
    assert.equal(r.res.status, 404, p);
    assert.match(r.text, /id="root"/);
  }
  for (const p of ['/cart', '/checkout', '/login', '/track', '/order/success']) {
    const r = await get(p);
    assert.equal(r.res.status, 200, p);
    assert.match(r.text, /<meta name="robots" content="noindex, follow">/);
    assert.equal(r.res.headers.get('x-robots-tag'), 'noindex');
  }
  for (const p of ['/shipping', '/contact', '/privacy', '/terms']) {
    const r = await get(p);
    assert.equal(r.res.status, 200, p);
    assert.match(r.text, new RegExp(`rel="canonical" href="https://rekhashoppingmall.com${p}"`));
    assert.match(r.text, /content="index, follow/);
    assert.equal(r.res.headers.get('x-robots-tag'), null);
  }
  const contact = await get('/contact');
  assert.match(contact.text, /a@b\.in/);
});

test('home page: accurate description from the catalogue, links to every product, Organization + WebSite', async () => {
  const { res, text } = await get('/');
  assert.equal(res.status, 200);
  assert.match(text, /<title>Rekha Shopping Mall \| Sarees Online<\/title>/);
  assert.match(text, /Shop 5 sarees online at Rekha Shopping Mall, from ₹100\./);
  for (const p of PRODUCTS) assert.ok(text.includes(`href="/products/${p.id}"`));
  const data = JSON.parse(/id="site-jsonld">([\s\S]*?)<\/script>/.exec(text)[1])['@graph'];
  assert.deepEqual(data.map(n => n['@type']), ['Organization', 'WebSite']);
  assert.doesNotMatch(text, /Kanchipuram|Banarasi|handwoven|handloom/i);
  const degraded = await get('/', { fetchFn: fakeApi({ fail: true }) });
  assert.equal(degraded.res.status, 200);
  assert.match(degraded.text, /Shop sarees online at Rekha Shopping Mall\./);
});

test('redirects, assets, methods, kill switch', async () => {
  const slash = await get('/shipping/?a=1');
  assert.equal(slash.res.status, 301);
  assert.equal(slash.res.headers.get('location'), '/shipping?a=1');
  assert.equal((await get('/index.html')).res.status, 301);
  const css = await get('/assets/a.css', { nextResponse: new Response('body{}', { headers: { 'content-type': 'text/css' } }) });
  assert.equal(css.res.status, 200);
  const ghost = await get('/wp-login.php');
  assert.equal(ghost.res.status, 404, 'a missing file is not the app shell');
  const post = await get('/products/23', {}, 'POST');
  assert.equal(post.calls.next, 1);
  const off = await get('/products/23', { env: { SEO_DISABLED: '1' } });
  assert.equal(off.calls.next, 1);
  const head = await get('/products/23', {}, 'HEAD');
  assert.equal(head.res.status, 200);
  assert.equal(head.text, '');
  assert.equal((await get('/media/1/abcdef1234')).calls.next, 1);
});

test('sitemap: only home, info pages and published products; 503 when the API is down', async () => {
  const res = await handleSitemap(new Request('https://rekhashoppingmall.com/sitemap.xml'), {}, fakeApi());
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /application\/xml/);
  const xml = await res.text();
  const locs = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map(m => m[1]);
  assert.equal(locs.length, 1 + 4 + PRODUCTS.length);
  assert.ok(locs.includes('https://rekhashoppingmall.com/'));
  assert.ok(!locs.some(l => /cart|checkout|admin|orders|profile|wishlist|login|track|personal/.test(l)));
  assert.match(buildSitemap([{ id: 1 }]), /products\/1</);
  const down = await handleSitemap(new Request('https://rekhashoppingmall.com/sitemap.xml'), {}, fakeApi({ fail: true }));
  assert.equal(down.status, 503);
});

test('crawler copy is ordinary visible content, replaced (not duplicated) by the app', async () => {
  const { text } = await get('/products/23');
  assert.match(text, /<div id="root"><div id="seo-shell">/);
  assert.doesNotMatch(text, /clip:|position:absolute|display:none|visibility:hidden|<noscript>/i, 'nothing hides the copy');
  assert.ok(text.includes('aspect-ratio:4/5'), 'photo box is reserved to avoid layout shift');
  assert.equal((text.match(/id="seo-shell"/g) || []).length, 1);
  assert.equal((text.match(/<h1>/g) || []).length, 1);
});

test('only the production host is indexable; previews and the pages.dev alias are noindex', async () => {
  for (const path of ['/', '/products/23', '/shipping']) {
    const r = await get(path, { host: 'seo-release-1.rekha-shopping-mall.pages.dev' });
    assert.equal(r.res.headers.get('x-robots-tag'), 'noindex', path);
    assert.match(r.text, /<meta name="robots" content="noindex, nofollow">/, path);
    assert.ok(r.text.includes('rel="canonical" href="https://rekhashoppingmall.com'), 'canonical still points at production');
  }
  const prod = await get('/products/23');
  assert.match(prod.text, /content="index, follow/);
  assert.equal(prod.res.headers.get('x-robots-tag'), null);
  const alias = await get('/products/23', { host: 'rekha-shopping-mall.pages.dev' });
  assert.equal(alias.res.headers.get('x-robots-tag'), 'noindex');
  const sm = await handleSitemap(new Request('https://x.pages.dev/sitemap.xml'), {}, fakeApi());
  assert.equal(sm.headers.get('x-robots-tag'), 'noindex');
});

test('API failures never turn a valid product into a 404', async () => {
  const cases = {
    'list 500, product 500': fakeApi({ list: null, single: { 23: 500 } }),
    'list down, product 403 (origin lock)': async (url) => (new URL(url).pathname === '/api/products' ? Promise.reject(new Error('down')) : new Response('', { status: 403 })),
    'list ok but stale, product 500': fakeApi({ list: [PRODUCTS[1]], single: { 23: 500 } }),
    'list ok but stale, product 401': fakeApi({ list: [PRODUCTS[1]], single: { 23: 401 } }),
    'everything times out': fakeApi({ fail: true }),
    'list down, product says 404': fakeApi({ list: null }),
  };
  for (const [name, fetchFn] of Object.entries(cases)) {
    const r = await get('/products/23', { fetchFn });
    assert.equal(r.res.status, 503, name);
    assert.ok((r.res.headers.get('x-seo-upstream') || '').startsWith('error'), name);
  }
  // A definite 404 with a healthy list is the only way to get a 404.
  assert.equal((await get('/products/23', { fetchFn: fakeApi({ list: [PRODUCTS[1]] }) })).res.status, 404);
  // A product newer than the list is still found through its own lookup.
  assert.equal((await get('/products/23', { fetchFn: fakeApi({ list: [PRODUCTS[1]], single: { 23: PRODUCTS[0] } }) })).res.status, 200);
});

test('API cache: per-URL keys, successful answers only, off when API_CACHE_SECONDS=0', async () => {
  const seen = [];
  const spy = async (url, init) => { seen.push({ url, cf: init.cf }); return fakeApi()(url, init); };
  await get('/products/23', { fetchFn: spy });
  const urls = seen.map(c => c.url);
  assert.ok(urls.includes('https://api.rekhashoppingmall.com/api/products'));
  assert.ok(urls.includes('https://api.rekhashoppingmall.com/api/store/info'));
  for (const c of seen) {
    assert.deepEqual(c.cf.cacheTtlByStatus, { '200-299': 20, '300-599': 0 }, 'only 2xx cached, 20 s');
  }
  seen.length = 0;
  await get('/products/23', { fetchFn: spy, env: { API_CACHE_SECONDS: '0' } });
  assert.ok(seen.every(c => c.cf === undefined), 'cache disabled');
  seen.length = 0;
  await get('/products/999', { fetchFn: spy });
  assert.ok(seen.map(c => c.url).includes('https://api.rekhashoppingmall.com/api/products/999'), 'single-product lookup carries the id in its URL');
  const withSecret = [];
  await get('/products/23', { fetchFn: async (u, i) => { withSecret.push(i.headers); return fakeApi()(u, i); }, env: { ORIGIN_SECRET: 's3' } });
  assert.ok(withSecret.every(h => h['X-Origin-Secret'] === 's3'));
});

test('security headers on Function HTML: copied from the shell, with a fallback that matches public/_headers', async () => {
  const headersFile = fs.readFileSync(new URL('../public/_headers', import.meta.url), 'utf8').split(/\r?\n/).join('\n');
  const block = headersFile.split('\n/assets/*')[0];
  for (const [k, v] of Object.entries(SECURITY_HEADERS)) {
    const m = block.split('\n').map(l => l.trim()).find(l => l.toLowerCase().startsWith(k.toLowerCase() + ':'))?.match(/^[^:]+:\s*(.*)$/);
    assert.ok(m, k + ' is in _headers');
    assert.equal(m[1].trim(), v, k + ' matches _headers');
  }
  // Shell without any security headers (what a binding that skips _headers would return): the fallback fills them in.
  const res = await handlePage(new Request('https://rekhashoppingmall.com/products/23'), {}, {
    fetchFn: fakeApi(), next: async () => new Response(''), getShell: async () => new Response(SHELL, { headers: { 'content-type': 'text/html' } }),
  });
  for (const k of Object.keys(SECURITY_HEADERS)) assert.ok(res.headers.get(k), k);
  for (const path of ['/nope', '/cart', '/']) {
    const r = await get(path);
    assert.equal(r.res.headers.get('content-security-policy'), "default-src 'self'", path + ' keeps the shell CSP');
  }
});
