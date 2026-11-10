// Keeps the document head right while people move around the app without a page load. The first page load already
// has its head written by the server (functions/_middleware.js); this applies when React Router changes page.
import { SITE_URL, OG_IMAGE_PATH, SITE_NAME } from '../../seo/meta.js';

function setTag(selector, create, attr, value) {
  let el = document.head.querySelector(selector);
  if (!el) {
    el = create();
    document.head.appendChild(el);
  }
  el.setAttribute(attr, value);
}

const metaTag = (attr, key) => () => {
  const el = document.createElement('meta');
  el.setAttribute(attr, key);
  return el;
};

export function setHead({ title, description, path, image, imageAlt, type = 'website', robots = 'index, follow, max-image-preview:large' }) {
  document.title = title;
  const url = `${SITE_URL}${path === '/' ? '/' : path}`;
  const img = image || `${SITE_URL}${OG_IMAGE_PATH}`;
  setTag('meta[name="robots"]', metaTag('name', 'robots'), 'content', robots);
  if (description) setTag('meta[name="description"]', metaTag('name', 'description'), 'content', description);
  setTag('link[rel="canonical"]', () => Object.assign(document.createElement('link'), { rel: 'canonical' }), 'href', url);
  const og = { 'og:type': type, 'og:site_name': SITE_NAME, 'og:title': title, 'og:url': url, 'og:image': img, 'og:image:alt': imageAlt || `${SITE_NAME} logo` };
  if (description) og['og:description'] = description;
  for (const [k, v] of Object.entries(og)) setTag(`meta[property="${k}"]`, metaTag('property', k), 'content', v);
  setTag('meta[name="twitter:card"]', metaTag('name', 'twitter:card'), 'content', 'summary_large_image');
  setTag('meta[name="twitter:title"]', metaTag('name', 'twitter:title'), 'content', title);
  setTag('meta[name="twitter:image"]', metaTag('name', 'twitter:image'), 'content', img);
  if (description) setTag('meta[name="twitter:description"]', metaTag('name', 'twitter:description'), 'content', description);
}
