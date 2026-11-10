/*
 * Cloudflare Pages middleware: gives search engines and social crawlers real HTML for every public page and real
 * HTTP status codes (see seo/handler.js). The React app is untouched: its index.html is fetched from the static
 * assets and the SEO head and crawler copy are injected into it.
 *
 * Kill switch: set SEO_DISABLED=1 in Pages > Settings > Environment variables and redeploy to go back to the old
 * behaviour (plain app shell for every URL). See seo/README.md for the full rollback procedure.
 */
import { handlePage } from '../seo/handler.js';

export async function onRequest(context) {
  const { request, env, next } = context;
  return handlePage(request, env, {
    next,
    // "/" is the app shell. (Asking for /index.html would be redirected to "/".)
    getShell: () => env.ASSETS.fetch(new Request(new URL('/', request.url), { headers: { Accept: 'text/html' }, redirect: 'manual' })),
  });
}
