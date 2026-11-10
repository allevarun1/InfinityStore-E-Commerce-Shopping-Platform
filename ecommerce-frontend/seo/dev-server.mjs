// Local stand-in for Cloudflare Pages, for checking the SEO layer with curl before deploying. No dependencies.
//   npm run build            (needs VITE_API_URL set, as in production)
//   node seo/dev-server.mjs  (serves ./dist and runs functions/_middleware.js logic on http://127.0.0.1:8788)
// Reads the real API (API_ORIGIN, default https://api.rekhashoppingmall.com); it only issues GET requests.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { handlePage, handleSitemap } from './handler.js';

const DIST = path.resolve(import.meta.dirname, '../dist');
const PORT = Number(process.env.PORT || 8788);
const env = {
  API_ORIGIN: process.env.API_ORIGIN, ORIGIN_SECRET: process.env.ORIGIN_SECRET,
  SEO_DISABLED: process.env.SEO_DISABLED, API_CACHE_SECONDS: process.env.API_CACHE_SECONDS,
};
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.txt': 'text/plain; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.webp': 'image/webp', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json', '.mp4': 'video/mp4',
};

// Pages applies the "/*" block of _headers to static files only (not to Function responses).
function globalHeaders() {
  const h = {};
  try {
    let on = false;
    for (const line of fs.readFileSync(path.join(DIST, '_headers'), 'utf8').split(/\r?\n/)) {
      if (!line.startsWith(' ') && line.trim()) { on = line.trim() === '/*'; continue; }
      const m = on && /^\s+([\w-]+):\s*(.+)$/.exec(line);
      if (m) h[m[1]] = m[2];
    }
  } catch { /* no _headers */ }
  return h;
}
const GLOBAL = globalHeaders();
const routes = JSON.parse(fs.readFileSync(path.join(DIST, '_routes.json'), 'utf8'));
const excluded = (p) => routes.exclude.some(r => (r.endsWith('/*') ? p.startsWith(r.slice(0, -1)) : p === r));

/** What env.ASSETS.fetch gives: the file, or (Pages' SPA fallback) index.html with 200 when there is none. */
function asset(pathname) {
  const file = path.join(DIST, decodeURIComponent(pathname));
  const real = file.startsWith(DIST) && fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.join(DIST, 'index.html');
  return new Response(fs.readFileSync(real), { status: 200, headers: { ...GLOBAL, 'Content-Type': TYPES[path.extname(real)] || 'application/octet-stream' } });
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
  const request = new Request(url, { method: req.method, headers: req.headers });
  const next = () => (url.pathname === '/sitemap.xml' ? handleSitemap(request, env) : asset(url.pathname));
  const out = excluded(url.pathname) ? asset(url.pathname)
    : await handlePage(request, env, { next, getShell: () => asset('/') });
  res.writeHead(out.status, Object.fromEntries(out.headers));
  res.end(Buffer.from(await out.arrayBuffer()));
}).listen(PORT, '127.0.0.1', () => console.log(`SEO dev server on http://127.0.0.1:${PORT} (API ${env.API_ORIGIN || 'default'})`));
