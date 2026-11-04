import React, { useMemo, useState } from 'react';
import { imageSrc, inr } from '../lib/format';
import { attemptReason, REASONS } from '../lib/orderBuckets';

const DAY = 24 * 3600 * 1000;
const RANGES = [['7', 'Last 7 days'], ['30', 'Last 30 days'], ['all', 'All time']];

const waLink = (phone, text) => `https://wa.me/91${String(phone).replace(/\D/g, '').slice(-10)}?text=${encodeURIComponent(text)}`;

/**
 * Checkout attempts that did not turn into paid orders: who tried, what they wanted, and why it stopped.
 * Nothing here needs packing or shipping; it is for follow-up and for seeing where customers drop off.
 */
export function AdminAttempts({ attempts, placed }) {
  const [range, setRange] = useState('30');
  const [reason, setReason] = useState('ALL');

  const d = useMemo(() => {
    const since = range === 'all' ? 0 : Date.now() - Number(range) * DAY;
    const inRange = (o) => new Date(o.orderDate).getTime() >= since;
    const tries = attempts.filter(o => inRange(o) && !o.testPayment);
    const paid = placed.filter(o => inRange(o) && !o.testPayment && o.paymentMethod === 'ONLINE');
    const byReason = Object.fromEntries(Object.keys(REASONS).map(k => [k, tries.filter(o => attemptReason(o) === k)]));
    const lost = tries.reduce((s, o) => s + Number(o.totalPrice || 0), 0);
    const started = tries.length + paid.length;
    const sarees = new Map();
    for (const o of tries) {
      for (const it of o.orderItems || []) {
        const key = it.product?.id ?? it.productName;
        const cur = sarees.get(key) || { name: it.product?.name || it.productName || 'Saree', img: it.product?.imageUrl, n: 0, v: 0 };
        cur.n += it.quantity;
        cur.v += Number(it.price || 0) * it.quantity;
        sarees.set(key, cur);
      }
    }
    return {
      tries,
      byReason,
      lost,
      payRate: started ? Math.round((paid.length / started) * 100) : null,
      paidCount: paid.length,
      started,
      topSarees: [...sarees.values()].sort((a, b) => b.n - a.n).slice(0, 5),
      list: (reason === 'ALL' ? tries : byReason[reason]).slice().sort((a, b) => new Date(b.orderDate) - new Date(a.orderDate)),
      testCount: attempts.filter(o => inRange(o) && o.testPayment).length,
    };
  }, [attempts, placed, range, reason]);

  return (
    <div className="attempts">
      <div className="toolbar">
        <p className="admin-hint" style={{ margin: 0 }}>Customers who started checkout but did not pay. <strong>Nothing here needs packing or shipping.</strong></p>
        <div className="chips" role="group" aria-label="Time range">
          {RANGES.map(([k, l]) => <button key={k} type="button" className={range === k ? 'on' : ''} aria-pressed={range === k} onClick={() => setRange(k)}>{l}</button>)}
        </div>
      </div>

      <ul className="kpis">
        <li className="kpi accent">
          <span className="kpi-label">Paid, out of everyone who started paying</span>
          <strong>{d.payRate == null ? '—' : `${d.payRate}%`}</strong>
          <small>{d.paidCount} paid of {d.started} started</small>
        </li>
        <li className="kpi"><span className="kpi-label">Did not pay</span><strong>{d.tries.length}</strong><small>checkout attempts</small></li>
        <li className="kpi"><span className="kpi-label">Value not paid</span><strong>{inr(d.lost, { decimals: 0 })}</strong><small>what those bags were worth</small></li>
        <li className="kpi"><span className="kpi-label">Left without paying</span><strong>{d.byReason.TIMED_OUT.length}</strong><small>{d.byReason.CANCELLED.length} cancelled · {d.byReason.FAILED.length} failed</small></li>
      </ul>

      {d.topSarees.length > 0 && (
        <section className="att-top" aria-labelledby="att-top-title">
          <h3 id="att-top-title">Sarees left unpaid most often</h3>
          <ul>
            {d.topSarees.map(s => (
              <li key={s.name}>
                {s.img ? <img src={imageSrc(s.img)} alt="" loading="lazy" /> : <span className="ph" aria-hidden="true"></span>}
                <span>{s.name}<small>{s.n} {s.n === 1 ? 'time' : 'times'} · {inr(s.v, { decimals: 0 })}</small></span>
              </li>
            ))}
          </ul>
          <p className="admin-hint">A saree that is often left unpaid may need a clearer photo, a better price or a coupon.</p>
        </section>
      )}

      <div className="chips" role="group" aria-label="Show attempts">
        <button type="button" className={reason === 'ALL' ? 'on' : ''} aria-pressed={reason === 'ALL'} onClick={() => setReason('ALL')}>All <small>{d.tries.length}</small></button>
        {Object.entries(REASONS).map(([k, r]) => (
          <button key={k} type="button" className={reason === k ? 'on' : ''} aria-pressed={reason === k} onClick={() => setReason(k)}>{r.label} <small>{d.byReason[k].length}</small></button>
        ))}
      </div>
      {reason !== 'ALL' && <p className="admin-hint">{REASONS[reason].hint}</p>}

      {d.list.length === 0 && <p className="ap-empty">Nothing here. Every customer who started paying in this period finished.</p>}
      <ul className="att-list">
        {d.list.map(o => {
          const r = REASONS[attemptReason(o)];
          const name = o.user ? `${o.user.firstName || ''} ${o.user.lastName || ''}`.trim() || o.user.username : 'Customer';
          const items = (o.orderItems || []).map(i => i.product?.name || i.productName).filter(Boolean);
          return (
            <li key={o.id} className="att-row">
              <div className="att-main">
                <strong>{name}</strong> <span className={`chip-status ${r.tone}`}>{r.label}</span>
                <small>{new Date(o.orderDate).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })} · {o.trackingNumber}</small>
                <span className="att-items">{items.join(', ') || 'No items'}</span>
              </div>
              <b>{inr(o.totalPrice, { decimals: 0 })}</b>
              {o.phoneNumber && attemptReason(o) !== 'PAYING' && (
                <a className="btn btn-outline sm" target="_blank" rel="noopener noreferrer"
                  href={waLink(o.phoneNumber, `Hi ${name}, this is Rekha Shopping Mall. We saw you were looking at ${items[0] || 'a saree'} but the payment didn't go through. Can we help you complete your order?`)}>
                  <i className="bi bi-whatsapp" aria-hidden="true"></i> Follow up
                </a>
              )}
            </li>
          );
        })}
      </ul>
      {d.testCount > 0 && <p className="admin-hint">{d.testCount} test-mode attempts are not counted.</p>}
    </div>
  );
}
