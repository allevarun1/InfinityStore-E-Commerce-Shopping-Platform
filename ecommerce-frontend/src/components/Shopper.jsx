import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useStore } from '../contexts/StoreContext';
import { inr, inrCompact, activePrice, imageSrc } from '../lib/format';
import { useCartActions } from '../lib/useCartActions';

const PIN_KEY = 'infinity_pin';
const readPin = () => { try { return localStorage.getItem(PIN_KEY) || ''; } catch { return ''; } };
const savePin = (v) => { try { localStorage.setItem(PIN_KEY, v); } catch { /* ignore */ } };

/** "Check delivery": a PIN code gives an estimated date range from the owner's own delivery settings. */
export function DeliveryCheck({ compact = false }) {
  const [pin, setPin] = useState(readPin);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const check = useCallback(async (value) => {
    const p = (value ?? pin).trim();
    if (!/^[1-9]\d{5}$/.test(p)) { setError('Enter a valid 6-digit PIN code'); setResult(null); return; }
    setBusy(true);
    setError(null);
    try {
      const r = await ApiService.delivery(p);
      setResult(r);
      savePin(p);
    } catch (err) {
      setResult(null);
      setError(err.response?.data?.message || 'Could not check that PIN code right now');
    } finally {
      setBusy(false);
    }
  }, [pin]);

  // A remembered PIN code is checked once on arrival so the estimate is already there.
  useEffect(() => {
    const saved = readPin();
    if (/^[1-9]\d{5}$/.test(saved)) check(saved);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={`delivery${compact ? ' compact' : ''}`}>
      <form onSubmit={(e) => { e.preventDefault(); check(); }}>
        <label htmlFor={compact ? 'pin-c' : 'pin-p'}><i className="bi bi-geo-alt" aria-hidden="true"></i> Check delivery to your PIN code</label>
        <div className="delivery-row">
          <input id={compact ? 'pin-c' : 'pin-p'} inputMode="numeric" autoComplete="postal-code" maxLength={6} placeholder="e.g. 560001"
            value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} />
          <button type="submit" className="btn btn-outline" disabled={busy}>{busy ? 'Checking…' : 'Check'}</button>
        </div>
      </form>
      {error && <p className="delivery-msg bad" role="alert">{error}</p>}
      {result && (
        <p className={`delivery-msg ${result.serviceable ? 'ok' : 'bad'}`} role="status">
          <i className={`bi ${result.serviceable ? 'bi-truck' : 'bi-x-circle'}`} aria-hidden="true"></i> {result.message}
          {result.serviceable && result.codAvailable && <span className="delivery-cod"><i className="bi bi-cash-coin" aria-hidden="true"></i> Cash on delivery available</span>}
        </p>
      )}
    </div>
  );
}

/** The best tier a basket of `units` sarees reaches, the next one to aim for, and what is still missing. */
export function offerStatus(offers, units) {
  const tiers = [...(offers || [])].sort((a, b) => a.minItems - b.minItems);
  const current = [...tiers].reverse().find(t => units >= t.minItems) || null;
  const next = tiers.find(t => units < t.minItems) || null;
  return { current, next, missing: next ? next.minItems - units : 0 };
}

/** All multi-buy tiers on one line, e.g. on a product page: "Buy 2+ save 5% · Buy 3+ save 10%". */
export function OfferStrip() {
  const { offers } = useStore();
  if (!offers?.length) return null;
  return (
    <div className="offer-strip">
      <i className="bi bi-stars" aria-hidden="true"></i>
      <span><strong>Multi-buy savings:</strong> {offers.map(o => `buy ${o.minItems}+ save ${Number(o.percentOff)}%`).join(' · ')}</span>
    </div>
  );
}

/** In the cart: how close the basket is to the next tier, or what it is already saving. */
export function OfferProgress({ units, saving }) {
  const { offers } = useStore();
  const { current, next, missing } = offerStatus(offers, units);
  if (!current && !next) return null;
  return (
    <div className={`offer-progress${current ? ' on' : ''}`}>
      {current && <p><i className="bi bi-check-circle-fill" aria-hidden="true"></i> {current.label} applied{saving ? <> — you save <strong>{inr(saving)}</strong></> : null}</p>}
      {next && (
        <p><i className="bi bi-plus-circle" aria-hidden="true"></i> Add <strong>{missing}</strong> more {missing === 1 ? 'saree' : 'sarees'} to save <strong>{Number(next.percentOff)}%</strong></p>
      )}
      {next && (
        <div className="bar" aria-hidden="true"><span style={{ width: `${Math.min(100, (units / next.minItems) * 100)}%` }}></span></div>
      )}
    </div>
  );
}

/** Price breakup on the product page: what the saree costs, the GST on it, and when shipping is free. */
export function PriceBreakup({ product }) {
  const { info } = useStore();
  if (!info) return null;
  const base = activePrice(product);
  const pct = Number(product.gstPercent ?? info.defaultGstPercent ?? 0);
  const gst = Math.round(base * pct) / 100;
  return (
    <details className="breakup">
      <summary>Price breakup</summary>
      <dl>
        <div><dt>Saree price</dt><dd>{inr(base)}</dd></div>
        <div><dt>GST ({pct}%)</dt><dd>{inr(gst)}</dd></div>
        <div className="sum"><dt>Total with GST</dt><dd>{inr(base + gst)}</dd></div>
        <div><dt>Shipping</dt><dd>{base > Number(info.freeShippingThreshold) ? 'Free' : `${inrCompact(info.shippingFee)}, free above ${inrCompact(info.freeShippingThreshold)}`}</dd></div>
      </dl>
      <p>No hidden charges. Your exact total is shown again before you place the order.</p>
    </details>
  );
}

/** A real, non-invented line of social proof (only present once the numbers are meaningful). */
export function DemandLine({ product }) {
  const bits = [];
  if (product.demandBadge === 'BESTSELLER') bits.push('Bestseller');
  if (product.soldRecently) bits.push(`${product.soldRecently} sold this month`);
  if (product.savedBy) bits.push(`saved by ${product.savedBy} shoppers`);
  if (!bits.length) return null;
  return <span className="demand"><i className={`bi ${product.demandBadge === 'BESTSELLER' ? 'bi-fire' : 'bi-heart-fill'}`} aria-hidden="true"></i> {bits.join(' · ')}</span>;
}

/** Entry point to personal shopping, used on product pages. */
export function VideoCallLink({ product }) {
  return (
    <Link className="btn btn-outline vc-link" to="/personal-shopping" state={{ product: { id: product.id, name: product.name } }}>
      <i className="bi bi-camera-video" aria-hidden="true"></i> See it on a video call
    </Link>
  );
}

/** "Complete your order": in-stock sarees not already in the cart, from the same categories first, one tap to add. */
export function CartCrossSell({ inCartIds, categoryIds, onAdded }) {
  const [products, setProducts] = useState([]);
  const [busyId, setBusyId] = useState(null);
  const { addToCart } = useCartActions();

  useEffect(() => {
    let live = true;
    ApiService.products().then(list => { if (live) setProducts(list); }).catch(() => {});
    return () => { live = false; };
  }, []);

  const key = inCartIds.join(',');
  const picks = products
    .filter(p => !inCartIds.includes(p.id) && (p.stockQuantity || 0) > 0)
    .sort((a, b) => (categoryIds.includes(b.category?.id) ? 1 : 0) - (categoryIds.includes(a.category?.id) ? 1 : 0)
      || (b.soldRecently || 0) - (a.soldRecently || 0))
    .slice(0, 4);

  if (!picks.length) return null;
  const add = async (p) => {
    setBusyId(p.id);
    if (await addToCart(p, 1, { quiet: true })) onAdded?.();
    setBusyId(null);
  };

  return (
    <section className="cross" aria-labelledby={`cross-${key}`}>
      <h2 id={`cross-${key}`}>Complete your order</h2>
      <ul>
        {picks.map(p => (
          <li key={p.id}>
            <Link to={`/products/${p.id}`} className="cross-img"><img src={imageSrc(p.imageUrl)} alt="" loading="lazy" /></Link>
            <div>
              <Link to={`/products/${p.id}`} className="cross-name">{p.name}</Link>
              <span className="cross-price">{inrCompact(activePrice(p))}</span>
              <button type="button" className="btn btn-outline" disabled={busyId === p.id} onClick={() => add(p)}>
                <i className="bi bi-bag-plus" aria-hidden="true"></i> {busyId === p.id ? 'Adding…' : 'Add'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
