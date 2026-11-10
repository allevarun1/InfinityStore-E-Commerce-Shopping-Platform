// Checks the initial HTML of every published product, the sitemap, robots.txt and the error statuses.
//   node seo/check.mjs http://127.0.0.1:8788                      (local dev server)
//   node seo/check.mjs https://rekhashoppingmall.com              (after deployment)
// Read-only: it only issues GET requests. Exit code 1 if anything fails.
const base = (process.argv[2] || 'http://127.0.0.1:8788').replace(/\/+$/, '');
const api = (process.argv[3] || 'https://api.rekhashoppingmall.com').replace(/\/+$/, '');
let failures = 0;
const PRODUCTION = ['rekhashoppingmall.com', 'www.rekhashoppingmall.com'].includes(new URL(base).hostname);
const SECURITY = ['content-security-policy', 'strict-transport-security', 'x-frame-options', 'x-content-type-options', 'referrer-policy', 'permissions-policy'];
const ok = (cond, msg) => { if (!cond) { failures++; console.log(`  FAIL  ${msg}`); } return cond; };
const text = async (path) => { const r = await fetch(base + path, { redirect: 'manual' }); return { r, t: await r.text() }; };
const pick = (html, re) => (re.exec(html) || [])[1];
const decode = (s) => (s || '').replace(/&#39;/g, "'").replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

const products = await (await fetch(`${api}/api/products`)).json();
const info = await (await fetch(`${api}/api/store/info`)).json();
console.log(`${products.length} published products on ${base}`);
const titles = new Map();
const descs = new Map();

for (const p of products) {
  const { r, t } = await text(`/products/${p.id}`);
  const price = Number(p.deal && p.dealPrice != null && p.dealPrice < p.price ? p.dealPrice : p.price);
  const title = decode(pick(t, /<title>([\s\S]*?)<\/title>/));
  const desc = decode(pick(t, /<meta name="description" content="([^"]*)"/));
  const ldRaw = pick(t, /<script type="application\/ld\+json" id="product-jsonld">([\s\S]*?)<\/script>/);
  let node = null;
  try { node = JSON.parse(ldRaw)['@graph'].find(n => n['@type'] === 'Product'); } catch { /* reported below */ }
  const label = `product ${p.id}`;
  ok(r.status === 200, `${label}: status ${r.status}`);
  ok(PRODUCTION ? !/noindex/.test(r.headers.get('x-robots-tag') || '') && /content="index, follow/.test(t) : r.headers.get('x-robots-tag') === 'noindex' && /content="noindex, nofollow"/.test(t),
    `${label}: robots (${PRODUCTION ? 'production must be indexable' : 'non-production must be noindex'})`);
  ok(!/MerchantReturn/.test(t), `${label}: no return-policy schema`);
  ok(r.headers.get('x-seo-upstream') === 'ok', `${label}: upstream header ${r.headers.get('x-seo-upstream')}`);
  for (const h of SECURITY) ok(r.headers.get(h), `${label}: header ${h}`);
  ok(!/clip:rect|position:absolute|display:none/.test(t.split('</head>')[0]), `${label}: copy not hidden`);
  ok(t.includes(decode(p.name).replace(/&/g, '&amp;').replace(/'/g, '&#39;')), `${label}: name in HTML`);
  ok(title.includes(p.name), `${label}: name in title`);
  ok(pick(t, /<link rel="canonical" href="([^"]*)"/) === `https://rekhashoppingmall.com/products/${p.id}`, `${label}: canonical`);
  ok(/<meta property="og:image" content="https?:\/\//.test(t), `${label}: og:image`);
  ok(node && node.offers.price === price.toFixed(2), `${label}: JSON-LD price ${node?.offers.price} vs ${price.toFixed(2)}`);
  ok(node && node.offers.availability.endsWith(p.stockQuantity > 0 ? 'InStock' : 'OutOfStock'), `${label}: JSON-LD availability`);
  ok(t.includes(`Price: ₹${price.toLocaleString('en-IN')}`), `${label}: visible price`);
  ok(t.includes(p.stockQuantity > 0 ? 'Availability: In stock' : 'Availability: Sold out'), `${label}: visible availability`);
  ok(!('brand' in (node || {})) && !('aggregateRating' in (node || {})), `${label}: no invented brand/rating`);
  ok(!!node && (!!p.sku === ('sku' in node)), `${label}: SKU only when present`);
  ok((t.match(/<title>/g) || []).length === 1 && (t.match(/rel="canonical"/g) || []).length === 1, `${label}: no duplicate head tags`);
  titles.set(title, [...(titles.get(title) || []), p.id]);
  descs.set(desc, [...(descs.get(desc) || []), p.id]);
  console.log(`  ${r.status}  /products/${p.id}  ${title}`);
}
for (const [k, ids] of titles) ok(ids.length === 1, `duplicate title on products ${ids}: ${k}`);
for (const [k, ids] of descs) ok(ids.length === 1, `duplicate description on products ${ids}`);

console.log('status codes');
const expect = { '/products/99999': 404, '/products/abc': 404, '/this-does-not-exist': 404, '/about': 404, '/category/1': 404,
  '/': 200, '/shipping': 200, '/contact': 200, '/privacy': 200, '/terms': 200, '/cart': 200, '/checkout': 200, '/login': 200 };
for (const [path, status] of Object.entries(expect)) {
  const r = await fetch(base + path, { redirect: 'manual' });
  ok(r.status === status, `${path}: ${r.status}, expected ${status}`);
  console.log(`  ${r.status}  ${path}`);
}

console.log('security headers on every kind of Function response');
for (const path of ['/', '/nope', '/cart', '/shipping', '/products/99999']) {
  const r = await fetch(base + path, { redirect: 'manual' });
  for (const h of SECURITY) ok(r.headers.get(h), `${path}: header ${h}`);
}
console.log('sitemap and robots');
const sm = await text('/sitemap.xml');
ok(sm.r.status === 200 && /xml/.test(sm.r.headers.get('content-type')), `sitemap.xml ${sm.r.status} ${sm.r.headers.get('content-type')}`);
const locs = [...sm.t.matchAll(/<loc>(.*?)<\/loc>/g)].map(m => m[1]);
ok(locs.length === 1 + 4 + products.length, `sitemap has ${locs.length} URLs, expected ${1 + 4 + products.length}`);
for (const p of products) ok(locs.includes(`https://rekhashoppingmall.com/products/${p.id}`), `sitemap lists product ${p.id}`);
ok(new Set(locs).size === locs.length && locs.every(l => l.startsWith('https://rekhashoppingmall.com/') && !/[?#]/.test(l)), 'sitemap URLs are unique, https, canonical, with no query');
ok(sm.t.startsWith('<?xml') && sm.t.trim().endsWith('</urlset>'), 'sitemap is an XML urlset');
ok(!locs.some(l => /cart|checkout|admin|orders|profile|wishlist|login|track|personal|reset/.test(l)), 'sitemap has no private routes');
const rb = await text('/robots.txt');
ok(rb.r.status === 200 && /text\/plain/.test(rb.r.headers.get('content-type')), `robots.txt ${rb.r.headers.get('content-type')}`);
ok(rb.t.includes('Sitemap: https://rekhashoppingmall.com/sitemap.xml'), 'robots.txt references the sitemap');

const home = await text('/');
ok(!/Kanchipuram|Banarasi|Mysore|Chanderi|kasavu|handwoven|handloom/i.test(home.t), 'home HTML makes no unsupported fabric claims');
ok(home.t.includes(`href="/products/${products[0].id}"`), 'home HTML links to products');
console.log(`shipping fee in store info: ${info.shippingFee}`);
console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
