import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';

const BLANK = { name: '', location: '', quote: '', rating: 5, productId: '' };

/** Customer quotes the owner features on the home page. Only words a customer really gave you belong here. */
export function AdminTestimonials({ products = [] }) {
  const [list, setList] = useState([]);
  const [form, setForm] = useState(BLANK);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = () => ApiService.adminTestimonials().then(setList).catch(() => toast.error('Could not load testimonials'));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const add = (e) => {
    e.preventDefault();
    setBusy(true);
    ApiService.createTestimonial({ ...form, rating: Number(form.rating), productId: form.productId ? Number(form.productId) : null })
      .then(() => { toast.success('Testimonial added'); setForm(BLANK); return load(); })
      .catch(err => toast.error(err.response?.data?.message || Object.values(err.response?.data?.errors || {})[0] || 'Could not add the testimonial'))
      .finally(() => setBusy(false));
  };
  const act = (fn, msg) => fn().then(() => { toast.success(msg); return load(); }).catch(() => toast.error('That did not work'));
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <div className="tab-content testimonials-admin">
      <h2>Customer testimonials</h2>
      <p className="admin-hint">
        Feature kind words that customers really sent you (WhatsApp, email, a review). Ask their permission and use a first name and city.
        Writing quotes nobody said is misleading and can breach consumer-protection law, so please do not.
        {list.some(t => t.sample) && <> Entries marked <strong>Sample</strong> are placeholder demo text: pause or remove them before you launch.</>}
      </p>
      <form className="videos-form" onSubmit={add}>
        <div className="field-group"><label htmlFor="t-name">First name</label>
          <input id="t-name" required maxLength={60} value={form.name} onChange={set('name')} placeholder="Priya" /></div>
        <div className="field-group"><label htmlFor="t-loc">City <span className="admin-hint">(optional)</span></label>
          <input id="t-loc" maxLength={60} value={form.location} onChange={set('location')} placeholder="Chennai" /></div>
        <div className="field-group wide"><label htmlFor="t-quote">What they said (20 to 300 characters)</label>
          <textarea id="t-quote" required minLength={20} maxLength={300} rows="3" value={form.quote} onChange={set('quote')} /></div>
        <div className="field-group"><label htmlFor="t-rate">Stars</label>
          <select id="t-rate" value={form.rating} onChange={set('rating')}>{[5, 4, 3].map(n => <option key={n} value={n}>{n}</option>)}</select></div>
        <div className="field-group"><label htmlFor="t-prod">About a saree <span className="admin-hint">(optional)</span></label>
          <select id="t-prod" value={form.productId} onChange={set('productId')}>
            <option value="">None</option>
            {products.filter(p => p.status === 'PUBLISHED' || !p.status).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select></div>
        <button type="submit" className="btn btn-primary" disabled={busy}>Add testimonial</button>
      </form>
      {list.length === 0 ? <p className="ap-empty">None yet. The home page hides this section until you add one.</p> : (
        <ul className="offers-list videos-list">
          {list.map(t => (
            <li key={t.id} className={t.active ? '' : 'off'}>
              <strong>{t.name}{t.location ? `, ${t.location}` : ''}<small>{t.rating} stars{t.sample ? ' · Sample' : ''}: {t.quote.slice(0, 70)}{t.quote.length > 70 ? '…' : ''}</small></strong>
              <span>{t.active ? 'Live' : 'Paused'}</span>
              <button type="button" onClick={() => act(() => ApiService.setTestimonialActive(t.id, !t.active), t.active ? 'Paused' : 'Live again')}>{t.active ? 'Pause' : 'Resume'}</button>
              <button type="button" className="danger-btn" onClick={() => act(() => ApiService.deleteTestimonial(t.id), 'Removed')}>Remove</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
