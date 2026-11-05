// Shared money and pricing helpers for display only. The server remains the authority
// for every amount that is charged (see PricingService on the backend).

export const inr = (value, { decimals = 2 } = {}) =>
  `₹${Number(value || 0).toLocaleString('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}`;

/** Whole rupees when the amount has no paise, otherwise two decimals. */
export const inrCompact = (value) => {
  const n = Number(value || 0);
  return inr(n, { decimals: Number.isInteger(n) ? 0 : 2 });
};

export const isOnDeal = (p) =>
  Boolean(p?.deal && p.dealPrice != null && Number(p.dealPrice) < Number(p.price));

export const activePrice = (p) => (isOnDeal(p) ? Number(p.dealPrice) : Number(p.price));

export const discountPercent = (p) =>
  isOnDeal(p) ? Math.round(((p.price - p.dealPrice) / p.price) * 100) : 0;

export const LOW_STOCK = 5;

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

/** Where uploaded photos and videos load from: the storefront's /media function (R2), e.g. https://x.pages.dev. */
const MEDIA_BASE = (import.meta.env.VITE_MEDIA_URL || '').replace(/\/+$/, '');
const LIBRARY_LINK = /^\/api\/media\/(\d{1,12})\/([a-f0-9]{10})$/;

/**
 * Uploaded images are served by the API (/api/media/:id/:checksum); bundled ones live with the frontend. With
 * VITE_MEDIA_URL set, library files load from Cloudflare (R2) and never touch the API server.
 */
export const imageSrc = (url) => {
  if (!url || !url.startsWith('/api/')) return url;
  const m = MEDIA_BASE && LIBRARY_LINK.exec(url);
  if (m) return `${MEDIA_BASE}/media/${m[1]}/${m[2]}`;
  return API_BASE.replace(/\/api\/?$/, '') + url;
};

const LOCAL_PHOTO = /^\/images\/sarees\/(.+)\.jpg$/;

/**
 * Props for an <img> of a catalogue photo. Bundled photos come in 400px and 800px WebP (see tools/optimize_assets.py)
 * so a phone downloads a card-sized file, not the full picture; uploaded or external photos are used as they are.
 */
export const imageSet = (url, sizes) => {
  const m = LOCAL_PHOTO.exec(url || '');
  if (!m) return { src: imageSrc(url) };
  const base = `/images/sarees/${m[1]}`;
  return { src: url, srcSet: `${base}-400.webp 400w, ${base}-800.webp 800w`, sizes };
};
