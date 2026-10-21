import React, { useEffect, useMemo, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';
import { minutesAgo } from '../lib/when';

const SOURCE = {
  back_in_stock: 'Waiting for restock', assistant: 'Chatted with stylist', assistant_contact: 'Asked stylist for help',
  contact: 'Asked to be contacted', footer: 'Newsletter', popup: 'Newsletter', checkout: 'Checkout',
};
const FILTERS = [
  ['open', 'Open'], ['hot', 'Hot'], ['warm', 'Warm'], ['cold', 'Cold'], ['CONTACTED', 'Contacted'], ['WON', 'Won'], ['LOST', 'Lost'],
];
const STATUSES = [['NEW', 'New'], ['CONTACTED', 'Contacted'], ['WON', 'Won (bought)'], ['LOST', 'Not interested']];

/** The owner's follow-up list: everyone who opted in, scored by how likely they are to buy, with a ready message. */
export function AdminLeads() {
  const [leads, setLeads] = useState(null);
  const [filter, setFilter] = useState('open');
  const [q, setQ] = useState('');
  const toast = useToast();

  const load = () => ApiService.adminLeads().then(setLeads).catch(() => toast.error('Could not load leads'));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const counts = useMemo(() => {
    const c = {};
    for (const l of leads || []) {
      const open = l.status === 'NEW' || l.status === 'CONTACTED';
      if (open) { c.open = (c.open || 0) + 1; c[l.temperature] = (c[l.temperature] || 0) + 1; }
      c[l.status] = (c[l.status] || 0) + 1;
    }
    return c;
  }, [leads]);

  if (!leads) return <Loader message="Loading leads…" />;

  const term = q.trim().toLowerCase();
  const shown = leads.filter(l => {
    const open = l.status === 'NEW' || l.status === 'CONTACTED';
    const pass = filter === 'open' ? open
      : ['hot', 'warm', 'cold'].includes(filter) ? open && l.temperature === filter
        : l.status === filter;
    if (!pass) return false;
    if (!term) return true;
    return [l.name, l.email, l.phone, l.interest, l.productName].some(v => v && v.toLowerCase().includes(term));
  });

  const update = async (lead, patch, msg) => {
    try {
      const saved = await ApiService.updateLead(lead.id, patch);
      setLeads(ls => ls.map(l => (l.id === saved.id ? saved : l)));
      if (msg) toast.success(msg);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not update the lead');
    }
  };

  const exportCsv = async () => {
    try {
      const blob = await ApiService.exportLeads();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'infinitystore-leads.csv'; a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Could not export');
    }
  };

  return (
    <div className="tab-content leads">
      <div className="leads-head">
        <div>
          <h2>Leads</h2>
          <p>People who asked to hear from you. Hottest first: each has a ready-to-send WhatsApp message.</p>
        </div>
        <button type="button" className="btn btn-outline" onClick={exportCsv} disabled={!leads.length}>
          <i className="bi bi-download" aria-hidden="true"></i> Export CSV
        </button>
      </div>

      <div className="leads-tools">
        <div className="chips" role="tablist" aria-label="Filter leads">
          {FILTERS.map(([k, label]) => (
            <button key={k} type="button" role="tab" aria-selected={filter === k} className={`chip${filter === k ? ' active' : ''}`} onClick={() => setFilter(k)}>
              {label} <span className="chip-count">{counts[k] || 0}</span>
            </button>
          ))}
        </div>
        <input className="leads-search" type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, phone, interest…" aria-label="Search leads" />
      </div>

      {shown.length === 0 ? (
        <p className="ap-empty">
          {leads.length === 0
            ? 'No leads yet. They arrive from the stylist chat, "Notify me" on sold-out sarees and the newsletter.'
            : 'No leads match this filter.'}
        </p>
      ) : (
        <ul className="lead-list">
          {shown.map(l => <LeadCard key={l.id} lead={l} onUpdate={update} />)}
        </ul>
      )}
    </div>
  );
}

function LeadCard({ lead: l, onUpdate }) {
  const [notes, setNotes] = useState(l.notes || '');
  const who = l.name || l.email || (l.phone ? `+91 ${l.phone}` : 'Shopper');
  const mail = l.email && `mailto:${l.email}?subject=${encodeURIComponent('From InfinityStore')}&body=${encodeURIComponent(l.followUp)}`;
  const contacted = () => { if (l.status === 'NEW') onUpdate(l, { status: 'CONTACTED' }); };

  return (
    <li className={`lead t-${l.temperature}`}>
      <div className="lead-score" title={l.reasons.join(', ')}>
        <strong>{l.score}</strong>
        <span>{l.temperature}</span>
      </div>
      <div className="lead-main">
        <div className="lead-top">
          <strong className="lead-name">{who}</strong>
          <span className="lead-src">{SOURCE[l.source] || l.source}</span>
          <small>{minutesAgo(l.minutesSinceActivity)}{l.touches > 1 ? ` · ${l.touches} visits` : ''}</small>
        </div>
        <div className="lead-contact">
          {l.phone && <span><i className="bi bi-phone" aria-hidden="true"></i> +91 {l.phone}</span>}
          {l.email && l.name && <span><i className="bi bi-envelope" aria-hidden="true"></i> {l.email}</span>}
        </div>
        {(l.interest || l.productName) && (
          <p className="lead-interest"><i className="bi bi-chat-quote" aria-hidden="true"></i> {l.interest || l.productName}</p>
        )}
        <div className="lead-reasons">{l.reasons.map(r => <span key={r}>{r}</span>)}</div>
        <details className="lead-more">
          <summary>Message &amp; notes</summary>
          <p className="lead-draft">{l.followUp}</p>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={2000} rows={2}
            placeholder="Private notes (e.g. prefers pastel, call after 6pm)" aria-label="Notes"
            onBlur={() => notes !== (l.notes || '') && onUpdate(l, { notes }, 'Notes saved')} />
        </details>
      </div>
      <div className="lead-actions">
        {l.whatsappLink && (
          <a className="btn btn-primary wa" href={l.whatsappLink} target="_blank" rel="noopener noreferrer" onClick={contacted}>
            <i className="bi bi-whatsapp" aria-hidden="true"></i> WhatsApp
          </a>
        )}
        {mail && <a className="btn btn-outline" href={mail} onClick={contacted}><i className="bi bi-envelope" aria-hidden="true"></i> Email</a>}
        <select value={l.status} onChange={(e) => onUpdate(l, { status: e.target.value }, 'Lead updated')} aria-label="Lead status">
          {STATUSES.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
      </div>
    </li>
  );
}
