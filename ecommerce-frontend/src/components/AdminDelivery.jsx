import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';

const errorText = (err, fallback) => err.response?.data?.message || Object.values(err.response?.data?.errors || {})[0] || fallback;
const show = (v) => (v == null ? '' : String(Number(v)));

/** The owner sets the delivery charge and the order amount above which delivery is free. Changes apply at once. */
export function AdminDelivery() {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [fee, setFee] = useState('');
  const [freeAbove, setFreeAbove] = useState('');
  const [busy, setBusy] = useState(false);

  const apply = (d) => { setData(d); setFee(show(d.fee)); setFreeAbove(show(d.freeAbove)); };

  useEffect(() => {
    ApiService.deliverySettings().then(apply).catch(err => toast.error(errorText(err, 'Could not load delivery charges')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      apply(await ApiService.saveDeliverySettings({ fee: Number(fee || 0), freeAbove: freeAbove === '' ? null : Number(freeAbove) }));
      toast.success('Delivery charges saved. Shoppers see them straight away.');
    } catch (err) {
      toast.error(errorText(err, 'Could not save delivery charges'));
    } finally { setBusy(false); }
  };

  const reset = async () => {
    setBusy(true);
    try {
      apply(await ApiService.resetDeliverySettings());
      toast.success('Back to the original delivery charges.');
    } catch (err) {
      toast.error(errorText(err, 'Could not reset'));
    } finally { setBusy(false); }
  };

  if (!data) return <Loader />;

  const f = Number(fee || 0);
  const t = freeAbove === '' ? 0 : Number(freeAbove);
  const preview = f === 0 ? 'Delivery is free on every order.'
    : t > 0 ? `Delivery is ₹${f} on orders up to ₹${t}, and free above ₹${t}.`
    : `Delivery is always ₹${f} (no free delivery).`;

  return (
    <div className="tab-content">
      <form className="admin-card" onSubmit={save} style={{ maxWidth: 520 }}>
        <h3>Delivery charges</h3>
        <p className="admin-hint">Change these whenever you like. New prices show in carts and checkout straight away; orders already placed keep the charge they were placed with.</p>
        <label htmlFor="dl-fee">Delivery charge (₹)</label>
        <input id="dl-fee" type="number" min="0" max="100000" step="1" inputMode="decimal" required value={fee} onChange={e => setFee(e.target.value)} />
        <label htmlFor="dl-free" style={{ marginTop: 12 }}>Free delivery on orders above (₹)</label>
        <input id="dl-free" type="number" min="0" max="100000" step="1" inputMode="decimal" placeholder="Leave empty for no free delivery" value={freeAbove} onChange={e => setFreeAbove(e.target.value)} />
        <p className="admin-hint" role="status" style={{ marginTop: 12 }}><i className="bi bi-info-circle" aria-hidden="true"></i> {preview}</p>
        <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
          <button type="submit" className="btn btn-primary" disabled={busy}>Save</button>
          <button type="button" className="btn btn-outline" disabled={busy} onClick={reset}>
            Reset to original (₹{show(data.defaultFee)}{data.defaultFreeAbove ? `, free above ₹${show(data.defaultFreeAbove)}` : ', no free delivery'})
          </button>
        </div>
      </form>
    </div>
  );
}
