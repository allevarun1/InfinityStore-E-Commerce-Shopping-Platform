// Builds the SEO head and the crawler-readable content for each public route, and injects them into the React
// app's index.html. Pure functions: no network, no Cloudflare APIs, so they are unit-tested directly.
import { NO_RETURNS_POINTS } from '../src/lib/policy.js';
import {
  SITE_URL, SITE_NAME, OG_IMAGE_PATH, HOME_TITLE, HOME_FALLBACK_DESCRIPTION, INFO_PAGES, UTILITY_PAGES,
  esc, clean, descriptionLines, inrText, jsonForScript, absoluteImage, activePrice, isOnDeal, inStock,
  productTitle, productDescription, homeDescription, productPath, siteJsonLd, productJsonLd, DEFAULT_API_ORIGIN,
} from './meta.js';

const STRIP = [
  /<title>[\s\S]*?<\/title>/gi,
  /<meta\s+name="description"[^>]*>/gi,
  /<meta\s+name="robots"[^>]*>/gi,
  /<meta\s+property="(?:og|product):[^>]*>/gi,
  /<meta\s+name="twitter:[^>]*>/gi,
  /<link\s+rel="canonical"[^>]*>/gi,
];

// The page copy served in the first HTML is ordinary visible content: customers and crawlers read the same words
// while the app loads. React replaces #root's children when it mounts, so nothing is duplicated. The picture has a
// reserved 4:5 box (the shop's photo ratio) so the app's own photo does not move the page when it arrives.
const SEO_STYLE = '<style>#seo-shell{max-width:760px;margin:0 auto;padding:24px 16px 48px;line-height:1.55}'
  + '#seo-shell h1{margin:.4em 0}#seo-shell ul{padding-left:1.2em}'
  // The app styles every <nav> as its header menu; inside this copy links should simply flow as text.
  + '#seo-shell nav{display:block;position:static;width:auto;height:auto;padding:0;margin:10px 0;text-align:left;font-size:.92rem;background:none;box-shadow:none}'
  + '#seo-shell nav a{display:inline;padding:0}#seo-shell nav a::before,#seo-shell nav a::after{content:none}'
  + '#seo-shell img{display:block;width:min(320px,100%);aspect-ratio:4/5;height:auto;object-fit:cover;border-radius:12px;margin:12px 0}</style>';

const meta = (attr, key, value) => `<meta ${attr}="${key}" content="${esc(value)}">`;

export function headHtml(h) {
  const t = [`<title>${esc(h.title)}</title>`];
  if (h.description) t.push(meta('name', 'description', h.description));
  t.push(meta('name', 'robots', h.robots));
  if (h.canonical) t.push(`<link rel="canonical" href="${esc(h.canonical)}">`);
  if (h.og) {
    const o = h.og;
    t.push(meta('property', 'og:site_name', SITE_NAME), meta('property', 'og:type', o.type || 'website'),
      meta('property', 'og:title', o.title), meta('property', 'og:description', o.description), meta('property', 'og:url', o.url));
    if (o.image) {
      t.push(meta('property', 'og:image', o.image));
      if (o.imageAlt) t.push(meta('property', 'og:image:alt', o.imageAlt));
    }
    for (const [k, v] of Object.entries(o.extra || {})) t.push(meta('property', k, v));
    t.push(meta('name', 'twitter:card', 'summary_large_image'), meta('name', 'twitter:title', o.title),
      meta('name', 'twitter:description', o.description));
    if (o.image) t.push(meta('name', 'twitter:image', o.image));
  }
  for (const { id, data } of h.jsonld || []) {
    t.push(`<script type="application/ld+json" id="${id}">${jsonForScript(data)}</script>`);
  }
  return t.join('\n    ');
}

/** Replaces the app shell's static head tags with this page's, and puts the crawler copy inside #root. */
export function injectIntoShell(shell, { head, bodyHtml, htmlAttrs }) {
  let html = shell;
  for (const re of STRIP) html = html.replace(re, '');
  html = html.replace('</head>', `    ${headHtml(head)}\n    ${bodyHtml ? SEO_STYLE : ''}\n  </head>`);
  if (htmlAttrs) html = html.replace(/<html\b/i, `<html ${htmlAttrs}`);
  if (bodyHtml) html = html.replace('<div id="root"></div>', `<div id="root"><div id="seo-shell">${bodyHtml}</div></div>`);
  return html;
}

// ------------------------------------------------------------------ page builders

const NOINDEX = 'noindex, follow';
const INDEX = 'index, follow, max-image-preview:large';

const links = (extra = '') => `<nav aria-label="Site"><a href="/">Home</a> · <a href="/shipping">Shipping &amp; No-Returns Policy</a> · <a href="/contact">Contact us</a> · <a href="/privacy">Privacy Policy</a> · <a href="/terms">Terms and Conditions</a>${extra}</nav>`;

export function notFoundPage(reason = 'Page not found') {
  return { status: 404, cache: 'no-cache', head: { title: `${reason} | ${SITE_NAME}`, robots: 'noindex, nofollow' }, bodyHtml: '' };
}

export function utilityPage(path) {
  return { status: 200, cache: 'no-cache', head: { title: `${UTILITY_PAGES[path]} | ${SITE_NAME}`, robots: NOINDEX }, bodyHtml: '', noindexHeader: true };
}

export function homePage(products, info) {
  const list = Array.isArray(products) ? products : null;
  const description = list ? homeDescription(list) : HOME_FALLBACK_DESCRIPTION;
  const url = `${SITE_URL}/`;
  const items = (list || []).map(p => `<li><a href="${productPath(p)}">${esc(clean(p.name))}</a> – ${esc(inrText(activePrice(p)))}${inStock(p) ? '' : ' (sold out)'}</li>`).join('');
  const bodyHtml = `<main><h1>${esc(SITE_NAME)}: sarees online</h1><p>${esc(description)}</p>${items ? `<ul>${items}</ul>` : ''}${links()}</main>`;
  return {
    status: 200, cache: 'no-cache', bodyHtml,
    head: {
      title: HOME_TITLE, description, robots: INDEX, canonical: url,
      og: { title: HOME_TITLE, description, url, image: `${SITE_URL}${OG_IMAGE_PATH}`, imageAlt: `${SITE_NAME} logo` },
      jsonld: [{ id: 'site-jsonld', data: siteJsonLd(info) }],
    },
  };
}

export function infoPage(path, info) {
  const page = INFO_PAGES[path];
  const title = `${page.heading} | ${SITE_NAME}`;
  const url = `${SITE_URL}${path}`;
  let body = '';
  if (path === '/contact') {
    const wa = clean(info?.whatsappNumber).replace(/\D/g, '');
    const phone = clean(info?.supportPhone);
    const email = clean(info?.supportEmail);
    const rows = [
      wa && `<li><a href="https://wa.me/${wa}">Chat on WhatsApp</a></li>`,
      phone && `<li>Phone: ${esc(phone)}</li>`,
      email && `<li>Email: <a href="mailto:${esc(email)}">${esc(email)}</a></li>`,
    ].filter(Boolean);
    body = rows.length ? `<ul>${rows.join('')}</ul>` : '';
  } else if (path === '/shipping') {
    const fee = Number(info?.shippingFee);
    const flat = Number.isFinite(fee) && fee > 0 && !(Number(info?.freeShippingThreshold) > 0);
    body = `<ul>${flat ? `<li>Delivery charge: ${esc(inrText(fee))} per order.</li>` : ''}`
      + `<li>${info?.cashOnDelivery ? 'Cash on delivery is available.' : 'Orders are paid securely online at checkout. We do not offer cash on delivery.'}</li>`
      + '<li>Delivery times vary by location and courier.</li></ul>'
      + `<h2>No returns, no refunds</h2><ul>${NO_RETURNS_POINTS.map(t => `<li>${esc(t)}</li>`).join('')}</ul>`;
  }
  const bodyHtml = `<main><h1>${esc(page.heading)}</h1><p>${esc(page.description)}</p>${body}${links()}</main>`;
  return {
    status: 200, cache: 'no-cache', bodyHtml,
    head: {
      title, description: page.description, robots: INDEX, canonical: url,
      og: { title, description: page.description, url, image: `${SITE_URL}${OG_IMAGE_PATH}`, imageAlt: `${SITE_NAME} logo` },
    },
  };
}

export function productPage(p, all, info, apiOrigin = DEFAULT_API_ORIGIN) {
  const url = `${SITE_URL}${productPath(p)}`;
  const title = productTitle(p, all);
  const description = productDescription(p, all);
  const images = [p.imageUrl, ...(Array.isArray(p.gallery) ? p.gallery : [])].map(u => absoluteImage(u, apiOrigin)).filter(Boolean);
  const cover = images[0] || `${SITE_URL}${OG_IMAGE_PATH}`;
  const price = activePrice(p);
  const name = clean(p.name);
  const facts = [['Fabric', p.fabric], ['Weave', p.weave], ['Colour', p.colour], ['Occasion', p.occasion], ['Region', p.region], ['Care', p.careInstructions]]
    .filter(([, v]) => clean(v)).map(([k, v]) => `<li>${k}: ${esc(clean(v))}</li>`).join('');
  const lines = descriptionLines(p.description).map(l => `<p>${esc(l)}</p>`).join('');
  const bodyHtml = `<main><nav aria-label="Breadcrumb"><a href="/">Home</a> › <span>${esc(name)}</span></nav>`
    + `<article><h1>${esc(name)}</h1>`
    + `${images[0] ? `<img src="${esc(images[0])}" alt="${esc(name)}">` : ''}`
    + `<p>Price: ${esc(inrText(price))}${isOnDeal(p) ? ` (was ${esc(inrText(p.price))})` : ''}</p>`
    + `<p>Availability: ${inStock(p) ? 'In stock' : 'Sold out'}</p>`
    + `${lines ? `<h2>Description</h2>${lines}` : ''}${facts ? `<h2>Details</h2><ul>${facts}</ul>` : ''}</article>${links()}</main>`;
  return {
    status: 200, cache: 'no-cache', bodyHtml, htmlAttrs: `data-seo-product="${esc(p.id)}"`,
    head: {
      title, description, robots: INDEX, canonical: url,
      og: {
        type: 'product', title, description, url, image: cover, imageAlt: name,
        extra: { 'product:price:amount': price.toFixed(2), 'product:price:currency': 'INR', 'product:availability': inStock(p) ? 'in stock' : 'out of stock' },
      },
      jsonld: [{ id: 'product-jsonld', data: productJsonLd(p, { url, images, info }) }],
    },
  };
}
