import React, { useEffect, useMemo, useState } from 'react';
import { ApiService } from '../services/api';
import { inr } from '../lib/format';
import { markSent, sentAt, waLink, winBackMessage } from '../lib/share';

const DAY = 86400000;
const since = (d) => (d ? Math.floor((Date.now() - new Date(d).getTime()) / DAY) : null);
const when = (d) => {
  const n = since(d);
  if (n === null) return 'never';
  return n === 0 ? 'today' : n === 1 ? 'yesterday' : n < 60 ? `${n} days ago` : `${Math.round(n / 30)} months ago`;
};

const FILTERS = [
  ['all', 'All'],
  ['best', 'Best customers'],
  ['repeat', 'Bought twice or more'],
  ['quiet', 'Not in 60 days'],
  ['saved', 'Saved, never bought'],
];

/** Customers ranked by what they spend, with a one-tap WhatsApp to bring quiet ones back. Staff accounts listed apart. */
export function AdminCustomers({ onDelete }) {
  const [list, setList] = useState(null);
  const [filter, setFilter] = useState('all');
  const [q, setQ] = useState('');
  const [sure, setSure] = useState(null);
  const [, setTick] = useState(0);

  const load = () => ApiService.adminCustomers().then(setList).catch(() => setList([]));
  useEffect(() => { load(); }, []);

  const buyers = useMemo(() => (list || []).filter(c => !c.admin), [list]);
  const staff = useMemo(() => (list || []).filter(c => c.admin), [list]);
  const stats = useMemo(() => {
    const paying = buyers.filter(c => c.orders > 0);
    const spent = paying.reduce((s, c) => s + Number(c.spent || 0), 0);
    return { total: buyers.length, paying: paying.length, repeat: paying.filter(c => c.orders >= 2).length, avg: paying.length ? spent / paying.length : 0 };
  }, [buyers]);
  const bestCut = useMemo(() => {
    const sp = buyers.filter(c => c.orders > 0).map(c => Number(c.spent)).sort((a, b) => b - a);
    return sp[Math.max(0, Math.ceil(sp.length * 0.2) - 1)] || Infinity;
  }, [buyers]);

  const shown = buyers.filter(c => {
    if (q && !`${c.name} ${c.username} ${c.email} ${c.phone || ''}`.toLowerCase().includes(q.toLowerCase())) return false;
    if (filter === 'best') return c.orders > 0 && Number(c.spent) >= bestCut;
    if (filter === 'repeat') return c.orders >= 2;
    if (filter === 'quiet') return c.orders > 0 && since(c.lastOrderAt) >= 60;
    if (filter === 'saved') return c.orders === 0 && c.saved > 0;
    return true;
  });

  const remove = async (c) => { setSure(null); await onDelete({ id: c.id, username: c.username }); load(); };

  return (
    <div className="tab-content">
      <h2>Customers</h2>
      <div className="cust-stats">
        <div><b>{stats.total}</b><span>customers</span></div>
        <div><b>{stats.paying}</b><span>have ordered</span></div>
        <div><b>{stats.paying ? Math.round((stats.repeat / stats.paying) * 100) : 0}%</b><span>came back for more</span></div>
        <div><b>{inr(stats.avg, { decimals: 0 })}</b><span>average spend</span></div>
      </div>
      <div className="toolbar">
        <div className="chips" role="group" aria-label="Show customers">
          {FILTERS.map(([k, l]) => <button key={k} type="button" className={filter === k ? 'on' : ''} aria-pressed={filter === k} onClick={() => setFilter(k)}>{l}</button>)}
        </div>
        <input type="search" placeholder="Search name, email or phone" value={q} onChange={e => setQ(e.target.value)} aria-label="Search customers" />
      </div>
      {list === null && <p className="ap-empty">Loading customers…</p>}
      {list && shown.length === 0 && <p className="ap-empty">{buyers.length === 0 ? 'No customers have signed up yet.' : 'No customers match that.'}</p>}
      <div className="user-list">
        {shown.map(c => {
          const sent = c.phone && sentAt('winback', c.phone);
          const quiet = c.orders > 0 && since(c.lastOrderAt) >= 60;
          return (
            <div className="list-row user-row cust" key={c.id}>
              <span className="avatar" aria-hidden="true">{(c.name || c.username || '?')[0].toUpperCase()}</span>
              <div className="cust-who">
                <strong>{c.name}{c.orders > 0 && Number(c.spent) >= bestCut && <span className="chip-status live">Top customer</span>}
                  {quiet && <span className="chip-status wait">Quiet {when(c.lastOrderAt)}</span>}</strong>
                <p>{c.email}{c.phone && <> · {c.phone}</>}</p>
                <p className="cust-facts">
                  <span><b>{c.orders}</b> {c.orders === 1 ? 'order' : 'orders'}</span>
                  <span><b>{inr(c.spent, { decimals: 0 })}</b> spent</span>
                  <span>Last order <b>{when(c.lastOrderAt)}</b></span>
                  {c.saved > 0 && <span><b>{c.saved}</b> saved</span>}
                  {c.favouriteCategory && <span>Loves <b>{c.favouriteCategory.replace(/ Sarees$/i, '').toLowerCase()}</b></span>}
                </p>
              </div>
              <div className="cust-actions">
                {c.phone
                  ? <a className="btn btn-wa sm" href={waLink(c.phone, winBackMessage(c))} target="_blank" rel="noopener noreferrer"
                      onClick={() => { markSent('winback', c.phone); setTick(t => t + 1); }}>
                      <i className="bi bi-whatsapp" aria-hidden="true"></i> {sent ? 'Message again' : quiet ? 'Bring back' : 'Message'}
                    </a>
                  : <span className="muted small">No phone yet</span>}
                {sure === c.id
                  ? <span><button className="danger-btn" onClick={() => remove(c)}>Yes, delete</button> <button onClick={() => setSure(null)}>Keep</button></span>
                  : <button className="danger-btn" onClick={() => setSure(c.id)}>Delete</button>}
              </div>
            </div>
          );
        })}
      </div>
      {staff.length > 0 && (
        <details className="cust-staff">
          <summary>Staff accounts ({staff.length})</summary>
          {staff.map(s => <p key={s.id}><b>{s.username}</b> · {s.email}</p>)}
        </details>
      )}
    </div>
  );
}
