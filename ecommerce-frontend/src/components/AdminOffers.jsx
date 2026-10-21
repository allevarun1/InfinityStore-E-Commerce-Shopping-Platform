import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';

/** Multi-buy tiers: "buy N or more sarees and save P%". The best tier a basket reaches applies automatically. */
export function AdminOffers() {
  const [tiers, setTiers] = useState([]);
  const [form, setForm] = useState({ minItems: '', percentOff: '' });
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = () => ApiService.adminOffers().then(setTiers).catch(() => toast.error('Could not load offers'));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const add = (e) => {
    e.preventDefault();
    setBusy(true);
    ApiService.createOffer({ minItems: Number(form.minItems), percentOff: Number(form.percentOff) })
      .then(() => { toast.success('Offer added'); setForm({ minItems: '', percentOff: '' }); return load(); })
      .catch(err => toast.error(err.response?.data?.message || Object.values(err.response?.data?.errors || {})[0] || 'Could not add the offer'))
      .finally(() => setBusy(false));
  };

  const act = (fn, msg) => fn().then(() => { toast.success(msg); return load(); }).catch(() => toast.error('That did not work'));

  return (
    <section className="offers-admin">
      <h2>Multi-buy offers</h2>
      <p className="admin-hint">Encourage bigger baskets: shoppers who add more sarees save more, automatically, before any coupon. Savings are taken from your margin, so check your costs first.</p>
      <form className="offers-form" onSubmit={add}>
        <div className="field-group"><label htmlFor="of-min">Buy this many or more</label>
          <input id="of-min" type="number" min="2" max="20" required value={form.minItems} onChange={(e) => setForm({ ...form, minItems: e.target.value })} placeholder="2" /></div>
        <div className="field-group"><label htmlFor="of-pct">Save this % (1 to 50)</label>
          <input id="of-pct" type="number" min="1" max="50" step="0.5" required value={form.percentOff} onChange={(e) => setForm({ ...form, percentOff: e.target.value })} placeholder="5" /></div>
        <button type="submit" className="btn btn-primary" disabled={busy}>Add offer</button>
      </form>
      {tiers.length === 0 ? <p className="ap-empty">No offers yet. A common start: buy 2 save 5%, buy 3 save 10%.</p> : (
        <ul className="offers-list">
          {tiers.map(t => (
            <li key={t.id} className={t.active ? '' : 'off'}>
              <strong>{t.label}</strong>
              <span>{t.active ? 'Live' : 'Paused'}</span>
              <button type="button" onClick={() => act(() => ApiService.setOfferActive(t.id, !t.active), t.active ? 'Paused' : 'Live again')}>{t.active ? 'Pause' : 'Resume'}</button>
              <button type="button" className="danger-btn" onClick={() => act(() => ApiService.deleteOffer(t.id), 'Offer removed')}>Remove</button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
