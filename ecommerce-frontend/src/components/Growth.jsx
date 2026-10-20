import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useStore } from '../contexts/StoreContext';
import { useToast } from '../contexts/ToastContext';
import { getConsent, setConsent, analyticsConfigured, track } from '../lib/analytics';
import { ProductCard } from './ProductCard';
import { useCartActions } from '../lib/useCartActions';

export const whatsappLink = (number, text) =>
  `https://wa.me/${number}${text ? `?text=${encodeURIComponent(text)}` : ''}`;

/** Floating chat button; the main assisted-sales channel for sarees. Hidden until a number is configured. */
export function WhatsAppFab() {
  const { info } = useStore();
  const { pathname } = useLocation();
  // Checkout keeps the bottom edge for its Place order bar.
  if (!info?.whatsappNumber || pathname === '/checkout') return null;
  return (
    <a className="wa-fab" target="_blank" rel="noopener noreferrer"
      href={whatsappLink(info.whatsappNumber, 'Hi InfinityStore, I need help choosing a saree.')}
      aria-label="Chat with us on WhatsApp">
      <i className="bi bi-whatsapp" aria-hidden="true"></i>
      <span>Chat with us</span>
    </a>
  );
}

/** "Ask about this saree" link with the product name and page pre-filled. */
export function WhatsAppAsk({ product }) {
  const { info } = useStore();
  if (!info?.whatsappNumber) return null;
  const text = `Hi InfinityStore, I'm interested in "${product.name}" ${window.location.href}`;
  return (
    <a className="btn btn-outline wa-ask" target="_blank" rel="noopener noreferrer"
      href={whatsappLink(info.whatsappNumber, text)}>
      <i className="bi bi-whatsapp" aria-hidden="true"></i> Ask about this saree on WhatsApp
    </a>
  );
}

/** Email sign-up. Opt-in only; shows the welcome coupon once subscribed. */
export function Newsletter() {
  const { info } = useStore();
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [offer, setOffer] = useState(null);
  const [done, setDone] = useState(false);

  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    ApiService.subscribe(email.trim())
      .then(res => {
        setDone(true);
        setOffer(res.offer || null);
        track.subscribe();
      })
      .catch(err => toast.error(err.response?.data?.errors?.email || err.response?.data?.message || 'Could not subscribe. Please try again.'))
      .finally(() => setBusy(false));
  };

  const pitch = info?.welcomeOffer ? `Get ${info.welcomeOffer.summary}.` : 'Be first to know about new weaves and festive offers.';

  return (
    <section className="newsletter" aria-labelledby="nl-title">
      <div>
        <h2 id="nl-title">Join the InfinityStore circle</h2>
        <p>{pitch}</p>
      </div>
      {done ? (
        <div className="nl-done" role="status">
          <strong>You’re in — thank you!</strong>
          {offer && <span>Use code <b className="nl-code">{offer.code}</b> at checkout for {offer.summary}.</span>}
        </div>
      ) : (
        <form onSubmit={submit} className="nl-form">
          <label htmlFor="nl-email" className="sr-only">Email address</label>
          <input id="nl-email" type="email" required maxLength={120} autoComplete="email"
            placeholder="you@example.com" value={email} onChange={e => setEmail(e.target.value)} />
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Joining…' : 'Subscribe'}</button>
          <small>We’ll only email about offers and new arrivals. Unsubscribe any time. See our <Link to="/privacy">privacy policy</Link>.</small>
        </form>
      )}
    </section>
  );
}

/** Asks before any analytics or advertising pixel is loaded. Renders nothing when none is configured. */
export function ConsentBanner() {
  const { info } = useStore();
  const [choice, setChoice] = useState(getConsent());
  useEffect(() => { setChoice(getConsent()); }, [info]);
  if (!info || !analyticsConfigured() || choice) return null;
  const decide = (value) => { setConsent(value); setChoice(value); };
  return (
    <div className="consent" role="dialog" aria-label="Cookie preferences">
      <p>We use cookies for analytics and to measure our advertising, so we can improve the store. Essential cookies are always on.
        See our <Link to="/privacy">privacy policy</Link>.</p>
      <div>
        <button type="button" className="btn btn-outline" onClick={() => decide('no')}>Decline</button>
        <button type="button" className="btn btn-primary" onClick={() => decide('yes')}>Accept</button>
      </div>
    </div>
  );
}

const RECENT_KEY = 'infinity_recent';

export function rememberViewed(id) {
  try {
    const list = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]').filter(x => x !== id);
    localStorage.setItem(RECENT_KEY, JSON.stringify([id, ...list].slice(0, 12)));
  } catch {
    // ignore
  }
}

function recentIds() {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
  } catch {
    return [];
  }
}

/** "You may also like" (same category / fabric / occasion) and "Recently viewed" shelves for a product page. */
export function ProductShelves({ product }) {
  const [all, setAll] = useState([]);
  const { addToCart } = useCartActions();
  const [busyId, setBusyId] = useState(null);
  useEffect(() => {
    ApiService.products().then(setAll).catch(() => setAll([]));
  }, [product.id]);

  const score = (p) => (p.category?.id === product.category?.id ? 3 : 0)
    + (p.fabric && p.fabric === product.fabric ? 2 : 0)
    + (p.occasion && p.occasion === product.occasion ? 2 : 0);
  const related = all
    .filter(p => p.id !== product.id && p.stockQuantity > 0)
    .map(p => ({ p, s: score(p) }))
    .filter(x => x.s > 0)
    .sort((a, b) => b.s - a.s || a.p.id - b.p.id)
    .slice(0, 4)
    .map(x => x.p);
  const seen = recentIds().filter(id => id !== product.id);
  const recent = seen.map(id => all.find(p => p.id === id)).filter(Boolean).slice(0, 4);

  const add = async (p) => {
    setBusyId(p.id);
    await addToCart(p);
    setBusyId(null);
  };

  const shelf = (title, list, id) => list.length > 0 && (
    <section className="shelf" aria-labelledby={id}>
      <h2 id={id}>{title}</h2>
      <div className="shelf-grid">
        {list.map(p => <ProductCard key={p.id} product={p} busy={busyId === p.id} onAdd={add} />)}
      </div>
    </section>
  );

  return (
    <>
      {shelf('You may also like', related, 'related-title')}
      {shelf('Recently viewed', recent, 'recent-title')}
    </>
  );
}
