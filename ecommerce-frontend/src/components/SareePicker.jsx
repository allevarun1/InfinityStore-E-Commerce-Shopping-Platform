import React, { useMemo, useState } from 'react';
import { imageSrc, inr, activePrice } from '../lib/format';

/**
 * Tick the sarees a coupon works on. Shows each saree with its photo and price, with a search box and
 * "select all shown" / "clear" shortcuts. `value` is an array of product ids.
 */
export function SareePicker({ products, value, onChange, idPrefix = 'sp' }) {
  const [q, setQ] = useState('');
  const chosen = useMemo(() => new Set(value), [value]);
  const shown = useMemo(() => {
    const t = q.trim().toLowerCase();
    return (products || []).filter(p => !t || p.name.toLowerCase().includes(t) || String(p.id) === t);
  }, [products, q]);

  const toggle = (id) => {
    const next = new Set(chosen);
    if (next.has(id)) next.delete(id); else next.add(id);
    onChange([...next]);
  };
  const addShown = () => onChange([...new Set([...value, ...shown.map(p => p.id)])]);

  if (!products) return <p className="admin-hint">Loading sarees…</p>;
  return (
    <div className="saree-picker">
      <div className="sp-bar">
        <label htmlFor={`${idPrefix}-q`} className="sr-only">Search sarees</label>
        <input id={`${idPrefix}-q`} type="search" value={q} onChange={e => setQ(e.target.value)} placeholder="Search sarees by name" />
        <button type="button" className="btn btn-outline" onClick={addShown} disabled={!shown.length}>Tick all shown</button>
        <button type="button" className="btn btn-outline" onClick={() => onChange([])} disabled={!value.length}>Clear</button>
      </div>
      <p className="sp-count" aria-live="polite"><strong>{value.length}</strong> {value.length === 1 ? 'saree' : 'sarees'} ticked</p>
      <ul className="sp-grid">
        {shown.map(p => {
          const on = chosen.has(p.id);
          return (
            <li key={p.id}>
              <label className={`sp-tile ${on ? 'on' : ''}`}>
                <input type="checkbox" checked={on} onChange={() => toggle(p.id)} />
                <span className="sp-img">{(p.previewImage || p.imageUrl) ? <img src={p.previewImage || imageSrc(p.imageUrl)} alt="" loading="lazy" /> : <i className="bi bi-image" aria-hidden="true"></i>}</span>
                <span className="sp-name">{p.name}</span>
                <span className="sp-price">{inr(activePrice(p), { decimals: 0 })}{p.status && p.status !== 'PUBLISHED' ? <em> · not live yet</em> : null}</span>
                {on && <i className="bi bi-check-circle-fill sp-tick" aria-hidden="true"></i>}
              </label>
            </li>
          );
        })}
        {!shown.length && <li className="admin-hint">No saree matches “{q}”.</li>}
      </ul>
    </div>
  );
}
