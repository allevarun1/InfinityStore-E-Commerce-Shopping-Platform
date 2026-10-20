// Optional GA4 + Meta pixel. Nothing loads unless the store has configured an id AND the visitor has
// accepted analytics cookies. Purchase events are sent only after the server has confirmed an order.
const CONSENT_KEY = 'infinity_analytics_consent';
let ids = { ga: null, meta: null };
let started = false;

export function getConsent() {
  try {
    return localStorage.getItem(CONSENT_KEY); // 'yes' | 'no' | null
  } catch {
    return null;
  }
}

export function setConsent(value) {
  try {
    localStorage.setItem(CONSENT_KEY, value);
  } catch {
    // ignore
  }
  if (value === 'yes') start();
}

export function configure(info) {
  ids = { ga: info?.gaMeasurementId || null, meta: info?.metaPixelId || null };
  if (getConsent() === 'yes') start();
}

export const analyticsConfigured = () => Boolean(ids.ga || ids.meta);

function start() {
  if (started || !analyticsConfigured()) return;
  started = true;
  if (ids.ga && /^G-[A-Z0-9]+$/.test(ids.ga)) {
    window.dataLayer = window.dataLayer || [];
    window.gtag = function gtag() { window.dataLayer.push(arguments); };
    window.gtag('js', new Date());
    window.gtag('config', ids.ga, { send_page_view: false });
    const s = document.createElement('script');
    s.async = true;
    s.src = `https://www.googletagmanager.com/gtag/js?id=${ids.ga}`;
    document.head.appendChild(s);
  }
  if (ids.meta && /^\d{5,20}$/.test(ids.meta)) {
    /* eslint-disable */
    !function (f, b, e, v, n, t, s) {
      if (f.fbq) return; n = f.fbq = function () { n.callMethod ? n.callMethod.apply(n, arguments) : n.queue.push(arguments); };
      if (!f._fbq) f._fbq = n; n.push = n; n.loaded = !0; n.version = '2.0'; n.queue = []; t = b.createElement(e); t.async = !0;
      t.src = v; s = b.getElementsByTagName(e)[0]; s.parentNode.insertBefore(t, s);
    }(window, document, 'script', 'https://connect.facebook.net/en_US/fbevents.js');
    /* eslint-enable */
    window.fbq('init', ids.meta);
  }
  pageView();
}

const ready = () => getConsent() === 'yes' && started;

export function pageView() {
  if (!ready()) return;
  window.gtag?.('event', 'page_view', { page_path: location.pathname + location.search, page_title: document.title });
  window.fbq?.('track', 'PageView');
}

const item = (p, quantity = 1) => ({
  item_id: String(p.id), item_name: p.name, item_category: p.category?.name,
  price: Number(p.deal && p.dealPrice != null && Number(p.dealPrice) < Number(p.price) ? p.dealPrice : p.price), quantity,
});

export const track = {
  viewItem(p) {
    if (!ready()) return;
    const it = item(p);
    window.gtag?.('event', 'view_item', { currency: 'INR', value: it.price, items: [it] });
    window.fbq?.('track', 'ViewContent', { content_ids: [String(p.id)], content_type: 'product', value: it.price, currency: 'INR' });
  },
  addToCart(p, quantity = 1) {
    if (!ready()) return;
    const it = item(p, quantity);
    window.gtag?.('event', 'add_to_cart', { currency: 'INR', value: it.price * quantity, items: [it] });
    window.fbq?.('track', 'AddToCart', { content_ids: [String(p.id)], content_type: 'product', value: it.price * quantity, currency: 'INR' });
  },
  beginCheckout(quote) {
    if (!ready() || !quote) return;
    window.gtag?.('event', 'begin_checkout', { currency: 'INR', value: Number(quote.total) });
    window.fbq?.('track', 'InitiateCheckout', { value: Number(quote.total), currency: 'INR' });
  },
  /** Call only with an order the server has accepted. */
  purchase(order) {
    if (!ready() || !order || order.testPayment) return;
    const value = Number(order.totalPrice);
    window.gtag?.('event', 'purchase', {
      transaction_id: order.trackingNumber, currency: 'INR', value,
      tax: Number(order.taxAmount), shipping: Number(order.shippingAmount),
    });
    window.fbq?.('track', 'Purchase', { value, currency: 'INR' });
  },
  subscribe() {
    if (!ready()) return;
    window.gtag?.('event', 'generate_lead');
    window.fbq?.('track', 'Lead');
  },
};
