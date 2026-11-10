/*
 * https://rekhashoppingmall.com/sitemap.xml, built from the live catalogue: the home page, the public information
 * pages and every published product. The API's own /api/store/sitemap.xml is left in place.
 * SEO_DISABLED=1 (the rollback switch) hands the request back to the static site, exactly as before this release.
 */
import { handleSitemap } from '../seo/handler.js';

const serve = ({ request, env, next }) => (env.SEO_DISABLED === '1' ? next() : handleSitemap(request, env));

export const onRequestGet = serve;
export const onRequestHead = serve;
