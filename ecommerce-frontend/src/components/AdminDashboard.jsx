import React, { useMemo } from 'react';
import { inr } from '../lib/format';

const DAY = 24 * 3600 * 1000;

/** The owner's morning glance: sales, orders waiting, stock to watch, a two-week trend and one-tap shortcuts. */
export function AdminDashboard({ orders, products, onNavigate }) {
  const d = useMemo(() => {
    const real = orders.filter(o => o.status !== 'CANCELLED' && !o.testPayment);
    const now = Date.now();
    const within = (o, days) => now - new Date(o.orderDate).getTime() < days * DAY;
    const days = Array.from({ length: 14 }, (_, i) => ({ n: 0, v: 0, t: now - (13 - i) * DAY }));
    for (const o of real) {
      const age = Math.floor((now - new Date(o.orderDate).getTime()) / DAY);
      if (age >= 0 && age < 14) { days[13 - age].n += 1; days[13 - age].v += Number(o.totalPrice); }
    }
    const published = products.filter(p => p.status === 'PUBLISHED');
    return {
      rev30: real.filter(o => within(o, 30)).reduce((s, o) => s + Number(o.totalPrice), 0),
      rev7: real.filter(o => within(o, 7)).reduce((s, o) => s + Number(o.totalPrice), 0),
      count30: real.filter(o => within(o, 30)).length,
      today: orders.filter(o => within(o, 1)).length,
      toConfirm: orders.filter(o => o.status === 'PENDING').length,
      toShip: orders.filter(o => o.status === 'CONFIRMED').length,
      live: published.length,
      drafts: products.length - published.length,
      low: published.filter(p => p.stockQuantity > 0 && p.stockQuantity <= 3),
      out: published.filter(p => p.stockQuantity === 0),
      days,
      recent: [...orders].sort((a, b) => new Date(b.orderDate) - new Date(a.orderDate)).slice(0, 5),
    };
  }, [orders, products]);

  const max = Math.max(1, ...d.days.map(x => x.n));
  const pts = d.days.map((x, i) => `${(i / 13) * 100},${28 - (x.n / max) * 24}`).join(' ');

  return (
    <section className="dash" aria-label="Business at a glance">
      <ul className="kpis">
        <li className="kpi accent">
          <span className="kpi-label">Sales, last 30 days</span>
          <strong>{inr(d.rev30, { decimals: 0 })}</strong>
          <small>{d.count30} order{d.count30 === 1 ? '' : 's'} · {inr(d.rev7, { decimals: 0 })} this week</small>
          <svg viewBox="0 0 100 30" preserveAspectRatio="none" className="spark" aria-label="Orders per day, last two weeks" role="img">
            <polyline points={pts} fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
          </svg>
        </li>
        <li className="kpi">
          <button type="button" onClick={() => onNavigate('orders')}>
            <span className="kpi-label">Orders waiting</span>
            <strong className={d.toConfirm ? 'hot' : ''}>{d.toConfirm}</strong>
            <small>{d.toConfirm ? 'to confirm' : 'all confirmed'} · {d.toShip} to ship · {d.today} today</small>
          </button>
        </li>
        <li className="kpi">
          <button type="button" onClick={() => onNavigate('products')}>
            <span className="kpi-label">Sarees live</span>
            <strong>{d.live}</strong>
            <small>{d.drafts} draft{d.drafts === 1 ? '' : 's'} not yet published</small>
          </button>
        </li>
        <li className="kpi">
          <button type="button" onClick={() => onNavigate('products')}>
            <span className="kpi-label">Stock to watch</span>
            <strong className={d.out.length ? 'hot' : ''}>{d.low.length + d.out.length}</strong>
            <small>{d.out.length} sold out · {d.low.length} nearly gone</small>
          </button>
        </li>
      </ul>

      <div className="quick" role="group" aria-label="Shortcuts">
        <button type="button" onClick={() => onNavigate('management')}><i className="bi bi-plus-circle" aria-hidden="true"></i><span>Add a saree</span></button>
        <button type="button" onClick={() => onNavigate('studio')}><i className="bi bi-stars" aria-hidden="true"></i><span>Make photos and reels</span></button>
        <button type="button" onClick={() => onNavigate('media')}><i className="bi bi-cloud-arrow-up" aria-hidden="true"></i><span>Upload photos or videos</span></button>
        <button type="button" onClick={() => onNavigate('popups')}><i className="bi bi-megaphone" aria-hidden="true"></i><span>Create an offer pop-up</span></button>
      </div>

      {d.recent.length > 0 && (
        <div className="recent card">
          <h3>Latest orders</h3>
          <ul>
            {d.recent.map(o => (
              <li key={o.id}>
                <span><strong>{o.trackingNumber}</strong><small>{o.user ? `${o.user.firstName} ${o.user.lastName}` : 'Customer'}</small></span>
                <span className={`chip-status ${o.status === 'PENDING' ? 'wait' : o.status === 'CANCELLED' ? 'off' : 'live'}`}>{o.status}</span>
                <b>{inr(o.totalPrice, { decimals: 0 })}</b>
              </li>
            ))}
          </ul>
          <button type="button" className="linklike" onClick={() => onNavigate('orders')}>See all orders</button>
        </div>
      )}
    </section>
  );
}
