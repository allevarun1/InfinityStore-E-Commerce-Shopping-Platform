/*
 * Cloudflare Pages Function: /media/{id}/{checksum} serves an uploaded photo or video straight from the R2 bucket
 * bound as MEDIA (Pages > Settings > Bindings), so files never pass through the API server and load from Cloudflare
 * near the customer. The R2 object key is media/{checksum}. Videos get byte ranges (needed by iPhone Safari).
 * A file not in R2 yet (still in the database) is sent on to the API's /api/media/{id}/{checksum}.
 */
const ID = /^\d{1,12}$/;
const SUM = /^[a-f0-9]{10}$/;
const DEFAULT_API = 'https://rekha-shopping-mall-api.onrender.com';

export async function onRequest({ request, env, params }) {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return new Response('Method not allowed', { status: 405, headers: { Allow: 'GET, HEAD' } });
  }
  const { id, sum } = params;
  if (!ID.test(id) || !SUM.test(sum)) return new Response('Not found', { status: 404 });

  const fallback = () => {
    const api = (env.API_ORIGIN || DEFAULT_API).replace(/\/+$/, '');
    return new Response(null, {
      status: 302,
      headers: { Location: `${api}/api/media/${id}/${sum}`, 'Cache-Control': 'no-store' },
    });
  };
  if (!env.MEDIA) return fallback();

  const wantsRange = request.headers.has('range');
  let obj;
  try {
    obj = await env.MEDIA.get(`media/${sum}`, { range: request.headers, onlyIf: request.headers });
  } catch {
    // A malformed Range header: answer with the whole file.
    obj = await env.MEDIA.get(`media/${sum}`);
  }
  if (obj === null) return fallback();

  const headers = new Headers();
  obj.writeHttpMetadata(headers);
  headers.set('ETag', obj.httpEtag);
  headers.set('Cache-Control', 'public, max-age=31536000, immutable');
  headers.set('Accept-Ranges', 'bytes');
  headers.set('X-Content-Type-Options', 'nosniff');
  headers.set('Content-Disposition', 'inline');
  headers.set('Cross-Origin-Resource-Policy', 'cross-origin');
  headers.set('Access-Control-Allow-Origin', '*');

  // onlyIf matched (If-None-Match / If-Modified-Since): the browser's copy is current.
  if (!('body' in obj)) return new Response(null, { status: 304, headers });

  let status = 200;
  if (wantsRange && obj.range) {
    const r = obj.range;
    const offset = 'suffix' in r ? Math.max(obj.size - r.suffix, 0) : r.offset ?? 0;
    const length = 'suffix' in r ? obj.size - offset : r.length ?? obj.size - offset;
    headers.set('Content-Range', `bytes ${offset}-${offset + length - 1}/${obj.size}`);
    headers.set('Content-Length', String(length));
    status = 206;
  } else {
    headers.set('Content-Length', String(obj.size));
  }
  return new Response(request.method === 'HEAD' ? null : obj.body, { status, headers });
}
