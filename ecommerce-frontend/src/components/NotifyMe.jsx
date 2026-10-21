import React, { useState } from 'react';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

/** Sold out: take an email or WhatsApp number, and Autopilot writes the moment it is back in stock. */
export function NotifyMe({ product }) {
  const { currentUser } = useAuth();
  const [reach, setReach] = useState(currentUser?.email || '');
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);
  const [error, setError] = useState(null);

  if (done) {
    return (
      <div className="notify done" id="notify" role="status">
        <i className="bi bi-bell-fill" aria-hidden="true"></i>
        <div><strong>You're on the list.</strong><span>{done}</span></div>
      </div>
    );
  }

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    const isEmail = reach.includes('@');
    setBusy(true);
    try {
      const r = await ApiService.captureLead({
        email: isEmail ? reach.trim() : null,
        phone: isEmail ? null : reach.trim(),
        productId: product.id,
        source: 'back_in_stock',
        consent,
      });
      setDone(r.message);
    } catch (err) {
      setError(err.response?.data?.message || 'Please check the details and try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="notify" id="notify" onSubmit={submit}>
      <div className="notify-head">
        <i className="bi bi-bell" aria-hidden="true"></i>
        <div>
          <strong>Sold out, but it may come back</strong>
          <span>Leave your email or WhatsApp number and we'll tell you the moment it's restocked.</span>
        </div>
      </div>
      <div className="notify-row">
        <input value={reach} onChange={(e) => setReach(e.target.value)} placeholder="Email or WhatsApp number"
          aria-label="Email or WhatsApp number" required maxLength={120} />
        <button type="submit" className="btn btn-primary" disabled={busy || !consent || !reach.trim()}>{busy ? 'Saving…' : 'Notify me'}</button>
      </div>
      <label className="notify-consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>Tell me when this saree is back. We'll message you once about it.</span>
      </label>
      {error && <p className="notify-error" role="alert">{error}</p>}
    </form>
  );
}
