import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { AdminOffers } from './AdminOffers';

const BLANK = { code: '', description: '', kind: 'percent', amount: '', maxDiscount: '', minSubtotal: '', firstOrderOnly: false, expiresOn: '', usageLimit: '' };

const describe = (c) => {
  const off = c.percentOff != null ? `${Number(c.percentOff)}% off` : `₹${Number(c.flatOff)} off`;
  const cap = c.maxDiscount != null ? `, max ₹${Number(c.maxDiscount)}` : '';
  const min = c.minSubtotal != null ? `, min ₹${Number(c.minSubtotal)}` : '';
  return `${off}${cap}${min}${c.firstOrderOnly ? ', first order only' : ''}`;
};

export function AdminCoupons() {
  const [coupons, setCoupons] = useState([]);
  const [form, setForm] = useState(BLANK);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = () => ApiService.adminCoupons().then(setCoupons).catch(() => toast.error('Could not load coupons'));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const set = (key) => (e) => setForm(f => ({ ...f, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const num = (v) => (v === '' ? null : Number(v));

  const create = (e) => {
    e.preventDefault();
    setBusy(true);
    ApiService.createCoupon({
      code: form.code,
      description: form.description || null,
      percentOff: form.kind === 'percent' ? num(form.amount) : null,
      flatOff: form.kind === 'flat' ? num(form.amount) : null,
      maxDiscount: form.kind === 'percent' ? num(form.maxDiscount) : null,
      minSubtotal: num(form.minSubtotal),
      firstOrderOnly: form.firstOrderOnly,
      expiresAt: form.expiresOn ? `${form.expiresOn}T23:59:59` : null,
      usageLimit: num(form.usageLimit),
    })
      .then(() => { toast.success('Coupon created'); setForm(BLANK); return load(); })
      .catch(err => toast.error(err.response?.data?.message || Object.values(err.response?.data?.errors || {})[0] || 'Could not create coupon'))
      .finally(() => setBusy(false));
  };

  const toggle = (c) => ApiService.setCouponActive(c.id, !c.active).then(load).catch(() => toast.error('Could not update coupon'));

  return (
    <div className="tab-content">
      <AdminOffers />
      <h2>Coupons</h2>
      <p style={{ color: 'var(--text-muted)' }}>
        Codes are applied after sale prices and before GST. The newsletter sign-up advertises the code set in <code>WELCOME_CODE</code> (default WELCOME10).
      </p>

      <form onSubmit={create} className="form-card" style={{ maxWidth: 640, display: 'grid', gap: 12 }}>
        <div className="field-group"><label htmlFor="cp-code">Code</label>
          <input id="cp-code" required maxLength={40} value={form.code} onChange={set('code')} placeholder="FESTIVE10" /></div>
        <div className="field-group"><label htmlFor="cp-desc">Note (internal)</label>
          <input id="cp-desc" maxLength={200} value={form.description} onChange={set('description')} /></div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <div className="field-group" style={{ flex: 1, minWidth: 140 }}><label htmlFor="cp-kind">Type</label>
            <select id="cp-kind" value={form.kind} onChange={set('kind')}>
              <option value="percent">Percentage off</option><option value="flat">Flat ₹ off</option>
            </select></div>
          <div className="field-group" style={{ flex: 1, minWidth: 140 }}><label htmlFor="cp-amount">{form.kind === 'percent' ? 'Percent (1-90)' : 'Amount ₹'}</label>
            <input id="cp-amount" required type="number" min="1" step="0.01" value={form.amount} onChange={set('amount')} /></div>
          {form.kind === 'percent' && (
            <div className="field-group" style={{ flex: 1, minWidth: 140 }}><label htmlFor="cp-max">Max discount ₹</label>
              <input id="cp-max" type="number" min="0" step="1" value={form.maxDiscount} onChange={set('maxDiscount')} placeholder="no cap" /></div>
          )}
        </div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <div className="field-group" style={{ flex: 1, minWidth: 140 }}><label htmlFor="cp-min">Minimum order ₹</label>
            <input id="cp-min" type="number" min="0" step="1" value={form.minSubtotal} onChange={set('minSubtotal')} placeholder="none" /></div>
          <div className="field-group" style={{ flex: 1, minWidth: 140 }}><label htmlFor="cp-exp">Expires on</label>
            <input id="cp-exp" type="date" value={form.expiresOn} onChange={set('expiresOn')} /></div>
          <div className="field-group" style={{ flex: 1, minWidth: 140 }}><label htmlFor="cp-limit">Total uses</label>
            <input id="cp-limit" type="number" min="1" step="1" value={form.usageLimit} onChange={set('usageLimit')} placeholder="unlimited" /></div>
        </div>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input type="checkbox" checked={form.firstOrderOnly} onChange={set('firstOrderOnly')} /> First order only
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create coupon'}</button>
      </form>

      <h3 style={{ marginTop: 24 }}>Existing coupons</h3>
      {!coupons.length && <p>No coupons yet.</p>}
      {coupons.map(c => (
        <div className="order-card" key={c.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <div>
            <strong>{c.code}</strong> <span className="tag">{c.active ? 'Active' : 'Paused'}</span>
            <div style={{ fontSize: '0.9rem' }}>{describe(c)}</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Used {c.usedCount}{c.usageLimit != null ? ` of ${c.usageLimit}` : ''}
              {c.expiresAt ? ` · expires ${new Date(c.expiresAt).toLocaleDateString()}` : ''}
              {c.description ? ` · ${c.description}` : ''}
            </div>
          </div>
          <button type="button" className="btn btn-outline" onClick={() => toggle(c)}>{c.active ? 'Pause' : 'Activate'}</button>
        </div>
      ))}
    </div>
  );
}
