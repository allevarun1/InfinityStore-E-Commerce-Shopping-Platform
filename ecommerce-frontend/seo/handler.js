// Request handling for the SEO layer. Cloudflare wires it in functions/_middleware.js and functions/sitemap.xml.js;
// tests and seo/dev-server.mjs call the same code.
//
// Rules: valid pages 200; unknown routes and missing products 404 (the React app still loads so people see its
// "not found" screen); API trouble 503 (never a 404, so a hiccup cannot drop pages from search); HTML is never cached,
// so price and stock are read fresh. Only the upstream API call is cached, for a few seconds (API_CACHE_SECONDS).
import {
  SITE_URL, DEFAULT_API_ORIGIN, PRODUCTION_HOSTS, INFO_PAGES, UTILITY_PAGES, esc, productPath,
} from './meta.js';
import { SECURITY_HEADERS } from './security.js';
import { injectIntoShell, notFoundPage, utilityPage, homePage, infoPage, productPage } from './render.js';

const PRODUCT_PATH = /^\/products\/([1-9]\d{0,9})$/;
const HAS_EXTENSION = /\.[a-z0-9]{1,8}$/i;

export const isProductionHost = (hostname) => PRODUCTION_HOSTS.includes(String(hostname).toLowerCase());

export function classify(path) {
  if (path === '/') return { kind: 'home' };
  const m = PRODUCT_PATH.exec(path);
  if (m) return { kind: 'product', id: Number(m[1]) };
  if (Object.hasOwn(INFO_PAGES, path)) return { kind: 'info', path };
  if (Object.hasOwn(UTILITY_PAGES, path)) return { kind: 'utility', path };
  return { kind: 'notfound' };
}

// ------------------------------------------------------------------ upstream API

export function apiOrigin(env) {
  return String(env?.API_ORIGIN || DEFAULT_API_ORIGIN).replace(/\/+$/, '');
}

/** GET {api}/api{path}. Resolves { status, data } (data null on non-2xx); rejects on timeout or network failure. */
export async function apiGet(env, path, fetchFn = fetch) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 5000);
  const ttl = Number(env?.API_CACHE_SECONDS ?? 20);
  const headers = { Accept: 'application/json' };
  if (env?.ORIGIN_SECRET) headers['X-Origin-Secret'] = env.ORIGIN_SECRET;
  try {
    const res = await fetchFn(`${apiOrigin(env)}/api${path}`, {
      // Cloudflare caches by full URL, so /products/23 and /products/24 are separate entries. Only 2xx answers are kept;
      // a 404 or a 5xx is never cached, so a hiccup cannot linger.
      headers, signal: ctl.signal,
      ...(ttl > 0 && { cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': ttl, '300-599': 0 } } }),
    });
    let data = null;
    if (res.ok) data = await res.json();
    return { status: res.status, data };
  } finally {
    clearTimeout(timer);
  }
}

const safe = (p) => p.catch(() => ({ status: 0, data: null }));

/** Published products, or null when the API cannot be reached. */
export async function loadCatalogue(env, fetchFn) {
  const r = await safe(apiGet(env, '/products', fetchFn));
  return r.status === 200 && Array.isArray(r.data) ? r.data : null;
}

export async function loadInfo(env, fetchFn) {
  const r = await safe(apiGet(env, '/store/info', fetchFn));
  return r.status === 200 ? r.data : null;
}

/** { state: 'found'|'missing'|'gone'|'error', product, all } */
export async function loadProduct(env, id, fetchFn) {
  const list = await safe(apiGet(env, '/products', fetchFn));
  const all = list.status === 200 && Array.isArray(list.data) ? list.data : null;
  const hit = all?.find(p => p.id === id);
  if (hit) return { state: 'found', product: hit, all, upstream: 'ok' };
  // Not in the list (or the list failed): ask for this one product, the authority on whether it exists.
  // Only a definite 404 from that call ever produces "not found"; any other failure is an outage (503), never a 404.
  const r = await safe(apiGet(env, `/products/${id}`, fetchFn));
  if (r.status === 200 && r.data) return { state: 'found', product: r.data, all: all || [r.data], upstream: 'ok' };
  if (r.status === 404 && all) return { state: 'missing', upstream: 'ok' };
  if (r.status === 410) return { state: 'gone', upstream: 'ok' };
  // Status 0 means timeout or network failure. (A 404 with no list to cross-check is also treated as an outage.)
  return { state: 'error', upstream: `error (list ${list.status}, product ${r.status})` };
}

// ------------------------------------------------------------------ responses

const STRIPPED = ['content-length', 'content-encoding', 'etag', 'last-modified', 'content-type', 'cache-control', 'accept-ranges'];

function htmlResponse(shellRes, shellText, page, request, extra = {}) {
  // Functions do not get the _headers rules, so keep the security headers the static shell came with.
  const headers = new Headers(shellRes.headers);
  for (const h of STRIPPED) headers.delete(h);
  headers.set('Content-Type', 'text/html; charset=utf-8');
  headers.set('Cache-Control', extra.cacheControl || page.cache || 'no-cache');
  for (const [k, v] of Object.entries(SECURITY_HEADERS)) if (!headers.has(k)) headers.set(k, v);
  if (page.noindexHeader || page.head?.robots?.startsWith('noindex')) headers.set('X-Robots-Tag', 'noindex');
  for (const [k, v] of Object.entries(extra.headers || {})) headers.set(k, v);
  const html = injectIntoShell(shellText, page);
  return new Response(request.method === 'HEAD' ? null : html, { status: extra.status || page.status, headers });
}

/**
 * @param request   incoming Request
 * @param env       Pages environment (ASSETS not needed here)
 * @param deps      { next(): Promise<Response>, getShell(): Promise<Response>, fetchFn? }
 */
export async function handlePage(request, env, deps) {
  const url = new URL(request.url);
  const { next, getShell, fetchFn = fetch } = deps;
  if ((request.method !== 'GET' && request.method !== 'HEAD') || env?.SEO_DISABLED === '1') return next();

  const path = url.pathname;
  if (path.startsWith('/media/')) return next(); // photo/video function
  // Redirects stay on the host that was asked (a preview deployment must not bounce to production).
  const redirect = (to) => new Response(null, { status: 301, headers: { Location: to, 'Cache-Control': 'public, max-age=3600' } });
  if (path === '/index.html') return redirect(`/${url.search}`);
  if (path.length > 1 && path.endsWith('/')) return redirect(`${path.replace(/\/+$/, '')}${url.search}`);

  if (HAS_EXTENSION.test(path)) {
    // A real file, or a function such as /sitemap.xml. Pages answers a missing file with the app shell and 200: make that a 404.
    const res = await next();
    if ((res.headers.get('content-type') || '').includes('text/html')) {
      return new Response('Not found', { status: 404, headers: { 'Content-Type': 'text/plain; charset=utf-8', 'X-Robots-Tag': 'noindex', 'Cache-Control': 'no-cache' } });
    }
    return res;
  }

  let shellRes;
  try { shellRes = await getShell(); } catch { return next(); }
  if (!shellRes?.ok) return next();
  const shell = await shellRes.text();
  const route = classify(path);
  const production = isProductionHost(url.hostname);
  // Previews and the *.pages.dev alias must never compete with the real site in search results.
  const send = (page, extra) => htmlResponse(shellRes, shell,
    production ? page : { ...page, noindexHeader: true, head: { ...page.head, robots: 'noindex, nofollow' } }, request, extra);

  switch (route.kind) {
    case 'notfound':
      return send(notFoundPage());
    case 'utility':
      return send(utilityPage(path));
    case 'info': {
      const needsInfo = path === '/contact' || path === '/shipping';
      return send(infoPage(path, needsInfo ? await loadInfo(env, fetchFn) : null));
    }
    case 'home': {
      const [all, info] = await Promise.all([loadCatalogue(env, fetchFn), loadInfo(env, fetchFn)]);
      return send(homePage(all, info)); // API down: still a valid page, with the generic description
    }
    case 'product': {
      const [found, info] = await Promise.all([loadProduct(env, route.id, fetchFn), loadInfo(env, fetchFn)]);
      if (found.state === 'found') return send(productPage(found.product, found.all, info, apiOrigin(env)), { headers: { 'X-SEO-Upstream': 'ok' } });
      if (found.state === 'gone') return send(notFoundPage('Product no longer available'), { status: 410 });
      if (found.state === 'missing') return send(notFoundPage('Product not found'));
      // The API is unreachable: tell crawlers to come back later. People still get the app, which calls the API itself.
      return send({ status: 503, head: { title: 'Rekha Shopping Mall', robots: 'noindex, nofollow' }, bodyHtml: '' },
        { cacheControl: 'no-store', headers: { 'Retry-After': '120', 'X-SEO-Upstream': found.upstream } });
    }
    default:
      return next();
  }
}

// ------------------------------------------------------------------ sitemap

export function buildSitemap(products) {
  const urls = ['/', ...Object.keys(INFO_PAGES), ...products.map(productPath)];
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n`
    + urls.map(u => `<url><loc>${esc(`${SITE_URL}${u === '/' ? '/' : u}`)}</loc></url>`).join('\n') + '\n</urlset>\n';
}

export async function handleSitemap(request, env, fetchFn = fetch) {
  const security = { 'X-Content-Type-Options': 'nosniff' };
  if (!isProductionHost(new URL(request.url).hostname)) security['X-Robots-Tag'] = 'noindex';
  const all = await loadCatalogue(env, fetchFn);
  if (!all) {
    return new Response('Sitemap temporarily unavailable', { status: 503, headers: { ...security, 'Retry-After': '120', 'Cache-Control': 'no-store', 'Content-Type': 'text/plain; charset=utf-8' } });
  }
  return new Response(request.method === 'HEAD' ? null : buildSitemap(all), {
    status: 200,
    headers: { ...security, 'Content-Type': 'application/xml; charset=utf-8', 'Cache-Control': 'public, max-age=300' },
  });
}
