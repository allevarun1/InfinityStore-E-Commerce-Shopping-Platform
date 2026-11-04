import React, { useEffect, useMemo, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { AdminOffers } from './AdminOffers';
import { SareePicker } from './SareePicker';
import { imageSrc } from '../lib/format';
import '../coupons.css';

const BLANK = { code: '', description: '', kind: 'percent', amount: '', maxDiscount: '', minSubtotal: '', firstOrderOnly: false, expiresOn: '', usageLimit: '', scope: 'all', productIds: [] };

const describe = (c) => {
  const off = c.percentOff != null ? `${Number(c.percentOff)}% off` : `₹${Number(c.flatOff)} off`;
  const cap = c.maxDiscount != null ? `, max ₹${Number(c.maxDiscount)}` : '';
  const min = c.minSubtotal != null ? `, min ₹${Number(c.minSubtotal)}` : '';
  return `${off}${cap}${min}${c.firstOrderOnly ? ', first order only' : ''}`;
};

const errorText = (err, fallback) => err.response?.data?.message || Object.values(err.response?.data?.errors || {})[0] || fallback;

/** One switch for the welcome offer: the pop-up, top bar, saree-page banner, newsletter box and the code itself. */
function WelcomeSwitch({ welcome, onToggle, busy }) {
  if (!welcome) return null;
  if (!welcome.exists) {
    return (
      <section className="welcome-switch off" aria-labelledby="ws-title">
        <div>
          <h3 id="ws-title">Welcome offer</h3>
          <p>There is no coupon called <code>{welcome.code}</code> yet, so nothing is shown to shoppers. Create one below with that code to start the welcome offer.</p>
        </div>
      </section>
    );
  }
  const on = welcome.active;
  return (
    <section className={`welcome-switch ${on ? 'on' : 'off'}`} aria-labelledby="ws-title">
      <div>
        <h3 id="ws-title">Welcome offer · <code>{welcome.code}</code> <span className={`chip-status ${on ? 'live' : 'off'}`}>{on ? 'ON' : 'OFF'}</span></h3>
        {on
          ? <p>Shoppers see the welcome pop-up, the message in the top bar, the offer on saree pages and in the newsletter box, and the code works at checkout.</p>
          : <p>Hidden everywhere: no welcome pop-up, no top-bar message, no saree-page offer, and the code does not work at checkout.</p>}
        <p className="admin-hint">Shoppers already on the site may take up to 5 minutes to see the change.</p>
      </div>
      <button type="button" role="switch" aria-checked={on} aria-labelledby="ws-title" className="ws-toggle" disabled={busy} onClick={onToggle}>
        <span className="ws-knob" aria-hidden="true"></span>
        <span className="ws-label">{on ? 'Turn off' : 'Turn on'}</span>
      </button>
    </section>
  );
}

/** "All sarees", or the chosen sarees as small photos with names. */
function Covers({ ids, byId }) {
  if (!ids?.length) return <span className="cv-all"><i className="bi bi-grid" aria-hidden="true"></i> All sarees</span>;
  return (
    <ul className="cv-list" aria-label="Works on these sarees">
      {ids.map(id => {
        const p = byId.get(id);
        return (
          <li key={id}>
            {p?.imageUrl || p?.previewImage ? <img src={p.previewImage || imageSrc(p.imageUrl)} alt="" /> : <i className="bi bi-image" aria-hidden="true"></i>}
            <span>{p ? p.name : `Saree #${id} (removed)`}</span>
          </li>
        );
      })}
    </ul>
  );
}

export function AdminCoupons() {
  const [coupons, setCoupons] = useState([]);
  const [products, setProducts] = useState(null);
  const [welcome, setWelcome] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(null); // { id, code, productIds, scope }
  const toast = useToast();

  const load = () => Promise.all([
    ApiService.adminCoupons().then(setCoupons),
    ApiService.welcomeOfferStatus().then(setWelcome).catch(() => setWelcome(null)),
  ]).catch(() => toast.error('Could not load coupons'));
  useEffect(() => {
    load();
    ApiService.adminProducts().then(list => setProducts([...list].sort((a, b) => a.name.localeCompare(b.name)))).catch(() => setProducts([]));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const byId = useMemo(() => new Map((products || []).map(p => [p.id, p])), [products]);

  const set = (key) => (e) => setForm(f => ({ ...f, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const num = (v) => (v === '' ? null : Number(v));

  const create = (e) => {
    e.preventDefault();
    if (form.scope === 'some' && !form.productIds.length) { toast.error('Tick at least one saree, or choose "All sarees"'); return; }
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
      productIds: form.scope === 'some' ? form.productIds : [],
    })
      .then((c) => { toast.success(`Coupon ${c.code} created`); setForm(BLANK); return load(); })
      .catch(err => toast.error(errorText(err, 'Could not create coupon')))
      .finally(() => setBusy(false));
  };

  const toggle = (c) => ApiService.setCouponActive(c.id, !c.active).then(load).catch(() => toast.error('Could not update coupon'));

  const toggleWelcome = () => {
    setBusy(true);
    ApiService.setCouponActive(welcome.id, !welcome.active)
      .then(() => { toast.success(welcome.active ? 'Welcome offer turned off' : 'Welcome offer turned on'); return load(); })
      .catch(() => toast.error('Could not change the welcome offer'))
      .finally(() => setBusy(false));
  };

  const saveSarees = () => {
    if (editing.scope === 'some' && !editing.productIds.length) { toast.error('Tick at least one saree, or choose "All sarees"'); return; }
    setBusy(true);
    ApiService.setCouponProducts(editing.id, editing.scope === 'some' ? editing.productIds : [])
      .then(() => { toast.success(`Sarees for ${editing.code} saved`); setEditing(null); return load(); })
      .catch(err => toast.error(errorText(err, 'Could not save the sarees')))
      .finally(() => setBusy(false));
  };

  // Sarees that already have a code of their own, so the owner can see at a glance what is covered.
  const usage = useMemo(() => {
    const m = new Map();
    coupons.filter(c => c.active).forEach(c => (c.productIds || []).forEach(id => m.set(id, [...(m.get(id) || []), c.code])));
    return m;
  }, [coupons]);

  return (
    <div className="tab-content coupons-admin">
      <AdminOffers />
      <h2>Coupons</h2>
      <p className="admin-hint">Codes are taken off after sale prices. A code can work on every saree or only on the sarees you tick.</p>

      <WelcomeSwitch welcome={welcome} onToggle={toggleWelcome} busy={busy} />

      <details className="cp-help">
        <summary><i className="bi bi-lightbulb" aria-hidden="true"></i> How to give a code to only some sarees</summary>
        <ul>
          <li><strong>One code for two sarees:</strong> create one coupon (say <code>SILK10</code>), choose <em>Only the sarees I tick</em>, and tick both sarees.</li>
          <li><strong>A different code for each saree:</strong> create <code>KANCHI10</code> and tick the first saree, then create <code>MYSORE200</code> and tick the second saree.</li>
          <li>A shopper can use one code per order. If their bag has other sarees too, only the ticked sarees get the discount.</li>
          <li>To change the sarees later, press <em>Change sarees</em> on the coupon below.</li>
        </ul>
      </details>

      <form onSubmit={create} className="form-card cp-form">
        <h3>New coupon</h3>
        <div className="cp-row">
          <div className="field-group"><label htmlFor="cp-code">Code shoppers type</label>
            <input id="cp-code" required maxLength={40} value={form.code} onChange={e => setForm(f => ({ ...f, code: e.target.value.toUpperCase().replace(/\s/g, '') }))} placeholder="FESTIVE10" /></div>
          <div className="field-group"><label htmlFor="cp-desc">Note for you <span className="admin-hint">(shoppers don't see it)</span></label>
            <input id="cp-desc" maxLength={200} value={form.description} onChange={set('description')} placeholder="Diwali offer on silk sarees" /></div>
        </div>
        <div className="cp-row">
          <div className="field-group"><label htmlFor="cp-kind">Discount type</label>
            <select id="cp-kind" value={form.kind} onChange={set('kind')}>
              <option value="percent">Percentage off</option><option value="flat">Flat ₹ off</option>
            </select></div>
          <div className="field-group"><label htmlFor="cp-amount">{form.kind === 'percent' ? 'Percent (1-90)' : 'Amount ₹'}</label>
            <input id="cp-amount" required type="number" min="1" max={form.kind === 'percent' ? 90 : undefined} step="0.01" value={form.amount} onChange={set('amount')} /></div>
          {form.kind === 'percent' && (
            <div className="field-group"><label htmlFor="cp-max">Max discount ₹</label>
              <input id="cp-max" type="number" min="0" step="1" value={form.maxDiscount} onChange={set('maxDiscount')} placeholder="no cap" /></div>
          )}
        </div>

        <fieldset className="cp-scope">
          <legend>Which sarees does this code work on?</legend>
          <label className={`cp-scope-opt ${form.scope === 'all' ? 'on' : ''}`}>
            <input type="radio" name="cp-scope" value="all" checked={form.scope === 'all'} onChange={set('scope')} />
            <span><strong>All sarees</strong><small>Works on everything in the shop</small></span>
          </label>
          <label className={`cp-scope-opt ${form.scope === 'some' ? 'on' : ''}`}>
            <input type="radio" name="cp-scope" value="some" checked={form.scope === 'some'} onChange={set('scope')} />
            <span><strong>Only the sarees I tick</strong><small>Other sarees in the bag pay full price</small></span>
          </label>
        </fieldset>
        {form.scope === 'some' && <SareePicker products={products} value={form.productIds} onChange={ids => setForm(f => ({ ...f, productIds: ids }))} idPrefix="cp-new" />}

        <div className="cp-row">
          <div className="field-group"><label htmlFor="cp-min">{form.scope === 'some' ? 'Minimum spend on ticked sarees ₹' : 'Minimum order ₹'}</label>
            <input id="cp-min" type="number" min="0" step="1" value={form.minSubtotal} onChange={set('minSubtotal')} placeholder="none" /></div>
          <div className="field-group"><label htmlFor="cp-exp">Expires on</label>
            <input id="cp-exp" type="date" value={form.expiresOn} onChange={set('expiresOn')} /></div>
          <div className="field-group"><label htmlFor="cp-limit">Total uses</label>
            <input id="cp-limit" type="number" min="1" step="1" value={form.usageLimit} onChange={set('usageLimit')} placeholder="unlimited" /></div>
        </div>
        <label className="admin-check">
          <input type="checkbox" checked={form.firstOrderOnly} onChange={set('firstOrderOnly')} /> First order only
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Create coupon'}</button>
      </form>

      <h3 className="cp-list-title">Your coupons</h3>
      {!coupons.length && <p className="admin-hint">No coupons yet.</p>}
      <ul className="cp-list">
        {coupons.map(c => (
          <li key={c.id} className={`cp-card ${c.active ? '' : 'paused'}`}>
            <div className="cp-card-head">
              <div>
                <strong className="cp-code">{c.code}</strong>
                <span className={`chip-status ${c.active ? 'live' : 'off'}`}>{c.active ? 'Active' : 'Paused'}</span>
                {welcome?.code === c.code && <span className="chip-status wait">Welcome offer</span>}
                <div className="cp-desc">{describe(c)}</div>
                <div className="admin-hint">
                  Used {c.usedCount}{c.usageLimit != null ? ` of ${c.usageLimit}` : ''}
                  {c.expiresAt ? ` · expires ${new Date(c.expiresAt).toLocaleDateString()}` : ''}
                  {c.description ? ` · ${c.description}` : ''}
                </div>
              </div>
              <div className="cp-card-actions">
                <button type="button" className="btn btn-outline" onClick={() => setEditing(editing?.id === c.id ? null : { id: c.id, code: c.code, productIds: c.productIds || [], scope: c.productIds?.length ? 'some' : 'all' })}>
                  {editing?.id === c.id ? 'Close' : 'Change sarees'}
                </button>
                <button type="button" className="btn btn-outline" onClick={() => toggle(c)}>{c.active ? 'Pause' : 'Activate'}</button>
              </div>
            </div>
            <div className="cp-covers"><span className="cp-covers-label">Works on:</span> <Covers ids={c.productIds} byId={byId} /></div>

            {editing?.id === c.id && (
              <div className="cp-edit">
                <fieldset className="cp-scope">
                  <legend>Which sarees does {c.code} work on?</legend>
                  <label className={`cp-scope-opt ${editing.scope === 'all' ? 'on' : ''}`}>
                    <input type="radio" name={`ed-scope-${c.id}`} checked={editing.scope === 'all'} onChange={() => setEditing({ ...editing, scope: 'all' })} />
                    <span><strong>All sarees</strong></span>
                  </label>
                  <label className={`cp-scope-opt ${editing.scope === 'some' ? 'on' : ''}`}>
                    <input type="radio" name={`ed-scope-${c.id}`} checked={editing.scope === 'some'} onChange={() => setEditing({ ...editing, scope: 'some' })} />
                    <span><strong>Only the sarees I tick</strong></span>
                  </label>
                </fieldset>
                {editing.scope === 'some' && <SareePicker products={products} value={editing.productIds} onChange={ids => setEditing({ ...editing, productIds: ids })} idPrefix={`cp-ed-${c.id}`} />}
                <div className="admin-actions">
                  <button type="button" className="btn btn-primary" disabled={busy} onClick={saveSarees}>Save sarees</button>
                  <button type="button" className="btn btn-outline" onClick={() => setEditing(null)}>Cancel</button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>

      {usage.size > 0 && (
        <>
          <h3 className="cp-list-title">Sarees with their own code</h3>
          <ul className="cv-list cv-summary">
            {[...usage.entries()].map(([id, codes]) => (
              <li key={id}>
                {byId.get(id)?.imageUrl ? <img src={imageSrc(byId.get(id).imageUrl)} alt="" /> : <i className="bi bi-image" aria-hidden="true"></i>}
                <span>{byId.get(id)?.name || `Saree #${id}`}</span>
                <span className="cv-codes">{codes.map(code => <code key={code}>{code}</code>)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
