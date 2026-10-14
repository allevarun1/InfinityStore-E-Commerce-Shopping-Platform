import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { inr } from '../lib/format';

const SEVERITY = { HIGH: 'Act soon', MEDIUM: 'Worth a look', LOW: 'Idea' };
const RANGES = [7, 30, 90];

function Stat({ label, value, hint }) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {hint && <small>{hint}</small>}
    </div>
  );
}

function SalesBars({ daily }) {
  const max = Math.max(1, ...daily.map(d => Number(d.netSales)));
  return (
    <div className="bars" role="img" aria-label={`Net sales per day over ${daily.length} days`}>
      {daily.map(d => (
        <div key={d.date} className="bar-col" title={`${d.date}: ${inr(d.netSales)} from ${d.orders} order${d.orders === 1 ? '' : 's'}`}>
          <div className="bar" style={{ height: `${Math.max(2, (Number(d.netSales) / max) * 100)}%` }} />
        </div>
      ))}
    </div>
  );
}

/** Business dashboard and Opportunity Scout. Read-only: every action is the owner's decision. */
export function AdminInsights({ onOpenProduct, onStartDraft }) {
  const [days, setDays] = useState(30);
  const [dash, setDash] = useState(null);
  const [scout, setScout] = useState(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    setError(false);
    Promise.all([ApiService.insightsDashboard(days), ApiService.insightsOpportunities()])
      .then(([d, s]) => { setDash(d); setScout(s); })
      .catch(() => setError(true));
  }, [days]);

  if (error) return <p className="admin-warn" role="alert">Could not load insights. Try again in a moment.</p>;
  if (!dash || !scout) return <p className="admin-hint">Loading insights…</p>;

  return (
    <div className="insights">
      <div className="insights-head">
        <h2>Business overview</h2>
        <div className="chips" role="group" aria-label="Period">
          {RANGES.map(r => (
            <button key={r} type="button" className="chip" aria-pressed={days === r} onClick={() => setDays(r)}>Last {r} days</button>
          ))}
        </div>
      </div>

      <div className="stats">
        <Stat label="Orders" value={dash.orders} hint={dash.pendingOrders > 0 ? `${dash.pendingOrders} waiting to be confirmed` : 'None waiting'} />
        <Stat label="Net sales" value={inr(dash.netSales)} hint="before GST and shipping" />
        <Stat label="Average order" value={inr(dash.averageOrderValue)} hint="including GST and shipping" />
        <Stat label="Gross profit" value={dash.grossProfit == null ? 'Not available' : inr(dash.grossProfit)}
          hint={dash.costCoveragePercent == null ? 'add product costs to see profit' : `on ${dash.costCoveragePercent}% of sales with a known cost`} />
        <Stat label="Units sold" value={dash.units} />
        <Stat label="GST collected" value={inr(dash.gstCollected)} hint="owed to the tax authority, not income" />
      </div>

      <section aria-labelledby="sales-chart">
        <h3 id="sales-chart">Net sales per day</h3>
        <SalesBars daily={dash.daily} />
      </section>

      <div className="insights-cols">
        <section aria-labelledby="top-products">
          <h3 id="top-products">Best sellers</h3>
          {dash.topProducts.length === 0 ? <p className="admin-hint">No sales in this period yet.</p> : (
            <table className="mini-table">
              <thead><tr><th>Product</th><th>Units</th><th>Net sales</th><th>Margin</th></tr></thead>
              <tbody>
                {dash.topProducts.map(p => (
                  <tr key={p.productId}>
                    <td><button type="button" className="linklike" onClick={() => onOpenProduct(p.productId)}>{p.name}</button></td>
                    <td>{p.units}</td>
                    <td>{inr(p.netSales)}</td>
                    <td>{p.marginPercent == null ? '—' : `${p.marginPercent}%`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section aria-labelledby="stock-alerts">
          <h3 id="stock-alerts">Stock</h3>
          {dash.outOfStock.length === 0 && dash.lowStock.length === 0 && <p className="admin-hint">Every live product has healthy stock.</p>}
          <ul className="plain-list">
            {dash.outOfStock.map(s => (
              <li key={s.productId}><span className="status-pill status-out">Sold out</span> <button type="button" className="linklike" onClick={() => onOpenProduct(s.productId)}>{s.name}</button></li>
            ))}
            {dash.lowStock.map(s => (
              <li key={s.productId}><span className="status-pill status-READY_FOR_REVIEW">{s.stock} left</span> <button type="button" className="linklike" onClick={() => onOpenProduct(s.productId)}>{s.name}</button></li>
            ))}
          </ul>
        </section>
      </div>

      <ul className="insight-notes">
        {dash.notes.map(n => <li key={n}>{n}</li>)}
        {dash.testOrdersExcluded > 0 && <li>{dash.testOrdersExcluded} test-payment order{dash.testOrdersExcluded === 1 ? ' was' : 's were'} left out.</li>}
      </ul>

      <h2>Opportunity Scout</h2>
      <p className="admin-hint">Suggestions from your own orders, stock, wishlists and costs. Nothing happens until you act on it.</p>
      {scout.opportunities.length === 0 && <p>Nothing needs attention right now.</p>}
      {scout.opportunities.map((o, i) => (
        <article className={`opp opp-${o.severity}`} key={`${o.type}-${o.productId ?? 'x'}-${i}`}>
          <div className="opp-top">
            <span className={`status-pill sev-${o.severity}`}>{SEVERITY[o.severity]}</span>
            <strong>{o.title}</strong>
          </div>
          <p>{o.evidence}</p>
          <p className="opp-action">{o.suggestedAction}</p>
          <div className="admin-actions">
            {o.productId && <button type="button" onClick={() => onOpenProduct(o.productId)}>Open product</button>}
            {o.startDraftWith && <button type="button" className="primary" onClick={() => onStartDraft(o.startDraftWith)}>Start a draft</button>}
          </div>
        </article>
      ))}
      <ul className="insight-notes">
        {scout.notes.map(n => <li key={n}>{n}</li>)}
      </ul>
    </div>
  );
}
