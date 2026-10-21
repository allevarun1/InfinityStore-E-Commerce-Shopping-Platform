import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { PromoCard } from './PromoPopup';
import { MediaPicker } from './MediaPicker';

const THEMES = [['MAROON', 'Maroon and gold'], ['GOLD', 'Bright gold'], ['EMERALD', 'Emerald'], ['NIGHT', 'Midnight']];

const PRESETS = [
  { label: 'Welcome offer', headline: 'Welcome to Rekha Shopping Mall', message: 'Take 10% off your first order. Handwoven silks, cottons and bridal sarees, delivered to your door.', ctaLabel: 'Shop the collection', ctaUrl: '/#products', theme: 'MAROON', trigger: 'LOAD', delaySeconds: 6, frequency: 'SESSION' },
  { label: 'Festival sale', headline: 'Festive sale is on', message: 'Special prices on silks and bridal sarees for a short time only.', ctaLabel: 'See the deals', ctaUrl: '/?view=deals#products', theme: 'GOLD', trigger: 'LOAD', delaySeconds: 5, frequency: 'DAY', countdown: true },
  { label: 'Free shipping', headline: 'Free shipping on orders above Rs. 999', message: 'Cash on delivery available across India.', ctaLabel: 'Start shopping', ctaUrl: '/#products', theme: 'EMERALD', trigger: 'LOAD', delaySeconds: 8, frequency: 'SESSION' },
  { label: 'Before you go', headline: 'Wait! Take 10% off before you go', message: 'Your first order is on us. The code works on any saree above Rs. 1,499.', ctaLabel: 'Claim my offer', ctaUrl: '/#products', theme: 'NIGHT', trigger: 'EXIT', delaySeconds: 0, frequency: 'DAY' },
];

const BLANK = { name: '', headline: '', message: '', couponCode: '', ctaLabel: 'Shop now', ctaUrl: '/#products', imageUrl: '', theme: 'MAROON', trigger: 'LOAD', frequency: 'SESSION', delaySeconds: 5, countdown: false, startsAt: '', endsAt: '', active: true };

/** The browser's datetime-local value (local time, no zone) from a server value, and back. */
const toInput = (v) => (v ? String(v).slice(0, 16) : '');
const fromInput = (v) => (v ? `${v}:00` : null);

function status(p) {
  const now = Date.now();
  if (!p.active) return ['Paused', 'off'];
  if (p.startsAt && new Date(p.startsAt).getTime() > now) return ['Scheduled', 'wait'];
  if (p.endsAt && new Date(p.endsAt).getTime() <= now) return ['Ended', 'off'];
  return ['Live', 'live'];
}

/** Design the offer pop-ups shoppers see, with a live preview, a schedule and a countdown. */
export function AdminPopups() {
  const toast = useToast();
  const [list, setList] = useState(null);
  const [coupons, setCoupons] = useState([]);
  const [form, setForm] = useState(BLANK);
  const [editing, setEditing] = useState(null);
  const [picker, setPicker] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = () => ApiService.adminPopups().then(setList).catch(() => { setList([]); toast.error('Could not load the pop-ups'); });
  useEffect(() => { load(); ApiService.adminCoupons().then(c => setCoupons(c.filter(x => x.active))).catch(() => {}); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const payload = () => ({ ...form, delaySeconds: Number(form.delaySeconds), couponCode: form.couponCode || null, startsAt: fromInput(form.startsAt), endsAt: fromInput(form.endsAt), imageUrl: form.imageUrl || null, ctaUrl: form.ctaUrl || null, ctaLabel: form.ctaLabel || null });

  const save = (e) => {
    e.preventDefault();
    setBusy(true);
    (editing ? ApiService.updatePopup(editing, payload()) : ApiService.createPopup(payload()))
      .then(() => { toast.success(editing ? 'Pop-up updated' : 'Pop-up created and live'); setForm(BLANK); setEditing(null); load(); })
      .catch(err => toast.error(err.response?.data?.message || 'Could not save the pop-up'))
      .finally(() => setBusy(false));
  };

  const edit = (p) => { setEditing(p.id); setForm({ ...BLANK, ...p, couponCode: p.couponCode || '', ctaLabel: p.ctaLabel || '', ctaUrl: p.ctaUrl || '', imageUrl: p.imageUrl || '', startsAt: toInput(p.startsAt), endsAt: toInput(p.endsAt), message: p.message || '' }); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const act = (fn, msg) => fn().then(() => { toast.success(msg); load(); }).catch(() => toast.error('That did not work'));

  return (
    <div className="tab-content popups">
      <h2>Offers &amp; pop-ups</h2>
      <p className="admin-hint">Design the offer shoppers see when they arrive, like the welcome and sale pop-ups on big shopping sites. Only one shows at a time: the newest live one. Shoppers see it once per visit (or per day) and never on checkout.</p>

      <div className="presets" role="group" aria-label="Start from a ready-made offer">
        <span>Start from:</span>
        {PRESETS.map(p => <button type="button" key={p.label} onClick={() => { setEditing(null); setForm({ ...BLANK, ...p, name: p.label }); }}>{p.label}</button>)}
      </div>

      <div className="popup-builder">
        <form onSubmit={save} className="card">
          <h3>{editing ? 'Edit this pop-up' : 'New pop-up'}</h3>
          <div className="field-group"><label htmlFor="pp-head">Headline <small>{form.headline.length}/80</small></label>
            <input id="pp-head" required maxLength={80} value={form.headline} onChange={set('headline')} placeholder="Festive sale: 15% off silks" /></div>
          <div className="field-group"><label htmlFor="pp-msg">Message <small>{form.message.length}/240</small></label>
            <textarea id="pp-msg" rows="3" maxLength={240} value={form.message} onChange={set('message')} placeholder="One or two short lines." /></div>
          <div className="admin-two">
            <div className="field-group"><label htmlFor="pp-code">Coupon code to show</label>
              <select id="pp-code" value={form.couponCode} onChange={set('couponCode')}>
                <option value="">No code</option>
                {coupons.map(c => <option key={c.id} value={c.code}>{c.code}</option>)}
              </select></div>
            <div className="field-group"><label htmlFor="pp-theme">Colours</label>
              <select id="pp-theme" value={form.theme} onChange={set('theme')}>{THEMES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></div>
            <div className="field-group"><label htmlFor="pp-cta">Button label</label>
              <input id="pp-cta" maxLength={30} value={form.ctaLabel} onChange={set('ctaLabel')} /></div>
            <div className="field-group"><label htmlFor="pp-url">Button goes to</label>
              <input id="pp-url" maxLength={200} value={form.ctaUrl} onChange={set('ctaUrl')} placeholder="/#products" /></div>
          </div>
          <div className="field-group"><label>Picture <span className="admin-hint">(optional)</span></label>
            <div className="pp-pic">
              {form.imageUrl ? <button type="button" className="btn btn-outline" onClick={() => setForm({ ...form, imageUrl: '' })}>Remove picture</button> : null}
              <button type="button" className="btn btn-outline" onClick={() => setPicker(true)}>{form.imageUrl ? 'Change picture' : 'Choose from library'}</button>
            </div></div>
          <div className="admin-two">
            <div className="field-group"><label htmlFor="pp-trig">When to show</label>
              <select id="pp-trig" value={form.trigger} onChange={set('trigger')}>
                <option value="LOAD">A few seconds after they arrive</option>
                <option value="EXIT">When they are about to leave</option>
              </select></div>
            <div className="field-group"><label htmlFor="pp-delay">Delay (seconds)</label>
              <input id="pp-delay" type="number" min="0" max="60" disabled={form.trigger === 'EXIT'} value={form.delaySeconds} onChange={set('delaySeconds')} /></div>
            <div className="field-group"><label htmlFor="pp-freq">How often</label>
              <select id="pp-freq" value={form.frequency} onChange={set('frequency')}>
                <option value="SESSION">Once per visit</option>
                <option value="DAY">Once a day</option>
                <option value="ALWAYS">Every page load</option>
              </select></div>
            <div className="field-group"><label className="admin-check"><input type="checkbox" checked={form.countdown} onChange={set('countdown')} /> Show a countdown to the end time</label></div>
            <div className="field-group"><label htmlFor="pp-start">Starts <span className="admin-hint">(optional)</span></label>
              <input id="pp-start" type="datetime-local" value={form.startsAt} onChange={set('startsAt')} /></div>
            <div className="field-group"><label htmlFor="pp-end">Ends <span className="admin-hint">(optional)</span></label>
              <input id="pp-end" type="datetime-local" value={form.endsAt} onChange={set('endsAt')} /></div>
          </div>
          <div className="admin-actions">
            <button type="submit" className="btn btn-primary" disabled={busy}>{editing ? 'Save changes' : 'Create pop-up'}</button>
            {editing && <button type="button" className="btn btn-outline" onClick={() => { setEditing(null); setForm(BLANK); }}>Cancel</button>}
          </div>
        </form>

        <aside className="popup-preview" aria-label="Live preview">
          <h3>Live preview</h3>
          <div className="preview-stage"><PromoCard promo={{ ...form, endsAt: form.endsAt ? `${form.endsAt}:00` : null }} compact onCopy={() => toast.info('Shoppers can copy the code here')} /></div>
          <p className="admin-hint">This is exactly what shoppers will see. {form.trigger === 'EXIT' ? 'It appears as they move to leave the page (on phones, after they have read well down it).' : `It appears ${form.delaySeconds || 0} seconds after they arrive.`}</p>
        </aside>
      </div>

      <h3>Your pop-ups</h3>
      {list === null && <p className="admin-hint">Loading…</p>}
      {list && list.length === 0 && <p className="ap-empty">None yet. Pick a ready-made offer above to begin.</p>}
      <ul className="offers-list popup-list">
        {(list || []).map(p => {
          const [label, tone] = status(p);
          return (
            <li key={p.id} className={tone === 'off' ? 'off' : ''}>
              <strong>{p.headline}<small>{p.name}{p.couponCode ? ` · code ${p.couponCode}` : ''} · {p.trigger === 'EXIT' ? 'on exit' : `after ${p.delaySeconds}s`} · {p.frequency === 'SESSION' ? 'once per visit' : p.frequency === 'DAY' ? 'once a day' : 'every load'}</small></strong>
              <span className={`chip-status ${tone}`}>{label}</span>
              <button type="button" onClick={() => edit(p)}>Edit</button>
              <button type="button" onClick={() => act(() => ApiService.setPopupActive(p.id, !p.active), p.active ? 'Paused' : 'Live again')}>{p.active ? 'Pause' : 'Resume'}</button>
              <button type="button" className="danger-btn" onClick={() => act(() => ApiService.deletePopup(p.id), 'Removed')}>Remove</button>
            </li>
          );
        })}
      </ul>

      {picker && <MediaPicker kind="IMAGE" onClose={() => setPicker(false)} onPick={(a) => { setForm({ ...form, imageUrl: a.url }); setPicker(false); }} />}
    </div>
  );
}
