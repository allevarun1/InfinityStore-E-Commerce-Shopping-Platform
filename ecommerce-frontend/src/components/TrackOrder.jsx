import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useStore } from '../contexts/StoreContext';
import { inr } from '../lib/format';
import { OrderTimeline } from './OrderViews';
import { whatsappLink } from './Growth';

const MESSAGE = {
  PENDING: 'We have your order and are getting it ready. We will confirm it shortly.',
  CONFIRMED: 'Your order is confirmed and is being packed.',
  SHIPPED: 'Your order is on its way to you.',
  DELIVERED: 'Delivered. We hope you love it!',
  CANCELLED: 'This order was cancelled.',
};

const fmt = (v) => new Date(v).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });

/** Track an order with just the order number and the mobile number used at checkout. No account needed. */
export function TrackOrder() {
  const [params] = useSearchParams();
  const { info } = useStore();
  const [orderNumber, setOrderNumber] = useState(params.get('o') || '');
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  useEffect(() => { document.title = 'Track your order | Rekha Shopping Mall'; }, []);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setResult(await ApiService.trackOrder(orderNumber.trim(), phone.trim()));
    } catch (err) {
      setResult(null);
      setError(err.response?.status === 429
        ? 'Too many tries. Please wait a few minutes and try again.'
        : err.response?.data?.message || 'Could not check that order right now.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="track">
      <h1>Track your order</h1>
      <p className="track-lead">Enter the order number from your confirmation and the mobile number you used at checkout.</p>

      <form className="track-form" onSubmit={submit}>
        <div className="field-group">
          <label htmlFor="tk-order">Order number</label>
          <input id="tk-order" value={orderNumber} onChange={(e) => setOrderNumber(e.target.value)} placeholder="INF-20261021-AB12CD" required maxLength={40} autoCapitalize="characters" autoComplete="off" />
        </div>
        <div className="field-group">
          <label htmlFor="tk-phone">Mobile number</label>
          <input id="tk-phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="98765 43210" required maxLength={20} inputMode="tel" autoComplete="tel" />
        </div>
        <button type="submit" className="btn btn-primary" disabled={busy || !orderNumber.trim() || !phone.trim()}>{busy ? 'Checking…' : 'Track order'}</button>
      </form>
      {error && <p className="track-error" role="alert"><i className="bi bi-exclamation-circle" aria-hidden="true"></i> {error}</p>}

      {result && (
        <section className="track-result" aria-live="polite">
          <header>
            <div><span className="eyebrow">Order</span><h2>{result.orderNumber}</h2></div>
            <span className={`track-pill s-${result.status.toLowerCase()}`}>{result.status === 'PENDING' ? 'Placed' : result.status.charAt(0) + result.status.slice(1).toLowerCase()}</span>
          </header>
          <OrderTimeline status={result.status} />
          <p className="track-note">{MESSAGE[result.status]} <small>Updated {fmt(result.updatedAt)}.</small></p>
          <ul className="track-items">
            {result.items.map((i, n) => (
              <li key={n}>{i.productId ? <Link to={`/products/${i.productId}`}>{i.name}</Link> : i.name} <b>× {i.quantity}</b></li>
            ))}
          </ul>
          <p className="track-total">
            {result.paymentMethod === 'COD' && result.paymentStatus !== 'PAID' ? 'Pay on delivery' : 'Total'}: <strong>{inr(result.total)}</strong>
          </p>
          {info?.whatsappNumber && (
            <a className="btn btn-outline wa-ask" target="_blank" rel="noopener noreferrer"
              href={whatsappLink(info.whatsappNumber, `Hi Rekha Shopping Mall, about my order ${result.orderNumber}`)}>
              <i className="bi bi-whatsapp" aria-hidden="true"></i> Ask about this order
            </a>
          )}
        </section>
      )}
    </div>
  );
}
