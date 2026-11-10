// Shared, dependency-free SEO helpers. Used by the Cloudflare Pages Functions (server HTML) and by the React app
// (client-side navigation), so a page gets the same title, description and structured data either way.
// Everything here is derived from real catalogue data: nothing about fabric, brand, rating or reviews is invented.

export const SITE_URL = 'https://rekhashoppingmall.com';
export const SITE_NAME = 'Rekha Shopping Mall';
export const DEFAULT_API_ORIGIN = 'https://api.rekhashoppingmall.com';
export const LOGO_PATH = '/brand/rekha-logo.png';
export const OG_IMAGE_PATH = '/brand/og-card.jpg';

export const HOME_TITLE = `${SITE_NAME} | Sarees Online`;
export const HOME_FALLBACK_DESCRIPTION = `Shop sarees online at ${SITE_NAME}.`;

/** Hosts that may be indexed. Every other host (previews, the *.pages.dev alias) is served with noindex. */
export const PRODUCTION_HOSTS = ['rekhashoppingmall.com', 'www.rekhashoppingmall.com'];

/** Public informational pages that are indexed and listed in the sitemap. */
export const INFO_PAGES = {
  '/shipping': {
    heading: 'Shipping & No-Returns Policy',
    description: 'Delivery charges, payment options and the sales policy for orders at Rekha Shopping Mall.',
  },
  '/contact': {
    heading: 'Contact us',
    description: 'Contact Rekha Shopping Mall by phone, WhatsApp or email.',
  },
  '/privacy': {
    heading: 'Privacy Policy',
    description: 'How Rekha Shopping Mall collects, uses and protects your personal information.',
  },
  '/terms': {
    heading: 'Terms and Conditions',
    description: 'Terms and conditions for using Rekha Shopping Mall and placing orders.',
  },
};

/** Real pages that must load (HTTP 200) but must not be indexed: accounts, checkout, tools. */
export const UTILITY_PAGES = {
  '/login': 'Sign in',
  '/register': 'Create account',
  '/reset-password': 'Reset password',
  '/cart': 'Your cart',
  '/wishlist': 'Wishlist',
  '/checkout': 'Checkout',
  '/orders': 'Your orders',
  '/order/success': 'Order placed',
  '/refer': 'Refer a friend',
  '/profile': 'Your profile',
  '/admin': 'Admin',
  '/track': 'Track your order',
  '/personal-shopping': 'Personal shopping',
};

// ------------------------------------------------------------------ text helpers

export const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

export const clean = (v) => String(v ?? '').replace(/\s+/g, ' ').trim();

/** Description lines as the owner typed them (one detail per line). */
export const descriptionLines = (v) => String(v ?? '').split(/\r?\n/).map(clean).filter(Boolean);

export function truncate(text, max) {
  const t = clean(text);
  if (t.length <= max) return t;
  const cut = t.slice(0, Math.max(0, max - 1));
  const at = cut.lastIndexOf(' ');
  return `${(at > max * 0.5 ? cut.slice(0, at) : cut).replace(/[\s,;:.\-–·]+$/, '')}…`;
}

export function inrText(value) {
  const n = Number(value || 0);
  return `₹${n.toLocaleString('en-IN', { minimumFractionDigits: Number.isInteger(n) ? 0 : 2, maximumFractionDigits: 2 })}`;
}

/** JSON for an inline <script type="application/ld+json">: "<" is escaped so text can never close the tag. */
export const jsonForScript = (data) => JSON.stringify(data)
  .replaceAll('<', ['\\', 'u003c'].join(''))
  .replaceAll(String.fromCharCode(0x2028), ' ')
  .replaceAll(String.fromCharCode(0x2029), ' ');

// ------------------------------------------------------------------ catalogue helpers

export const isOnDeal = (p) => Boolean(p?.deal && p.dealPrice != null && Number(p.dealPrice) < Number(p.price));
export const activePrice = (p) => (isOnDeal(p) ? Number(p.dealPrice) : Number(p.price));
export const inStock = (p) => Number(p?.stockQuantity) > 0;

const sameName = (a, b) => clean(a).toLowerCase() === clean(b).toLowerCase();

/** True when another product has the same name, so this one needs its item number to be told apart. */
export const hasDuplicateName = (p, all) => Array.isArray(all) && all.some(o => o.id !== p.id && sameName(o.name, p.name));

export const productPath = (p) => `/products/${p.id}`;

/** Absolute URL for an image link stored on a product ("/api/media/…" lives on the API host). */
export function absoluteImage(url, apiOrigin = DEFAULT_API_ORIGIN) {
  if (!url) return null;
  if (/^https?:\/\//i.test(url)) return url;
  if (url.startsWith('/api/')) return `${apiOrigin.replace(/\/+$/, '')}${url}`;
  if (url.startsWith('/')) return `${SITE_URL}${url}`;
  return null;
}

// ------------------------------------------------------------------ titles and descriptions

export function productTitle(p, all) {
  const name = clean(p.name);
  return `${hasDuplicateName(p, all) ? `${name} (Item ${p.id})` : name} | ${SITE_NAME}`;
}

export function productDescription(p, all) {
  const name = clean(p.name);
  const label = hasDuplicateName(p, all) ? `${name} (Item ${p.id})` : name;
  const head = `${label} for ${inrText(activePrice(p))} at ${SITE_NAME}.`;
  const stock = inStock(p) ? 'In stock.' : 'Currently sold out.';
  const room = 155 - head.length - stock.length - 2;
  const body = room > 24 ? truncate(descriptionLines(p.description).join(', '), room) : '';
  return [head, body, stock].filter(Boolean).join(' ');
}

export function homeDescription(products) {
  const prices = (products || []).map(activePrice).filter(n => Number.isFinite(n) && n > 0);
  if (prices.length === 0) return HOME_FALLBACK_DESCRIPTION;
  return `Shop ${prices.length} ${prices.length === 1 ? 'saree' : 'sarees'} online at ${SITE_NAME}, from ${inrText(Math.min(...prices))}. Pay securely online.`;
}

// ------------------------------------------------------------------ structured data

export function organizationJsonLd(info) {
  const org = { '@type': 'Organization', '@id': `${SITE_URL}/#organization`, name: SITE_NAME, url: SITE_URL, logo: `${SITE_URL}${LOGO_PATH}` };
  const phone = clean(info?.supportPhone);
  const email = clean(info?.supportEmail);
  if (phone || email) {
    org.contactPoint = { '@type': 'ContactPoint', contactType: 'customer support', ...(phone && { telephone: phone }), ...(email && { email }) };
  }
  return org;
}

export function websiteJsonLd() {
  return { '@type': 'WebSite', '@id': `${SITE_URL}/#website`, name: SITE_NAME, url: SITE_URL, publisher: { '@id': `${SITE_URL}/#organization` } };
}

export function siteJsonLd(info) {
  return { '@context': 'https://schema.org', '@graph': [organizationJsonLd(info), websiteJsonLd()] };
}

/**
 * Product + Offer + BreadcrumbList from a catalogue product.
 * ctx: { url, images: [absolute urls], info (store info or null) }.
 * SKU only when the product has one; no brand, rating, reviews or return policy; shipping only from the shop's live flat fee.
 */
export function productJsonLd(p, ctx) {
  const price = activePrice(p);
  const offer = {
    '@type': 'Offer',
    url: ctx.url,
    priceCurrency: 'INR',
    price: price.toFixed(2),
    availability: inStock(p) ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
    seller: { '@type': 'Organization', name: SITE_NAME },
  };
  const fee = Number(ctx.info?.shippingFee);
  const freeFrom = Number(ctx.info?.freeShippingThreshold);
  if (Number.isFinite(fee) && fee >= 0 && !(freeFrom > 0)) {
    offer.shippingDetails = {
      '@type': 'OfferShippingDetails',
      shippingRate: { '@type': 'MonetaryAmount', value: fee.toFixed(2), currency: 'INR' },
      shippingDestination: { '@type': 'DefinedRegion', addressCountry: 'IN' },
    };
  }
  // No MerchantReturnPolicy on purpose: the shop's real return policy has not been legally reviewed yet, and a
  // blanket "returns not permitted" must not be published as structured data until it has.
  const description = descriptionLines(p.description).join('. ');
  const product = {
    '@type': 'Product',
    name: clean(p.name),
    ...(description && { description }),
    ...(ctx.images?.length && { image: ctx.images }),
    ...(clean(p.sku) && { sku: clean(p.sku) }),
    url: ctx.url,
    offers: offer,
  };
  const breadcrumb = {
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Home', item: `${SITE_URL}/` },
      { '@type': 'ListItem', position: 2, name: clean(p.name), item: ctx.url },
    ],
  };
  return { '@context': 'https://schema.org', '@graph': [product, breadcrumb] };
}
