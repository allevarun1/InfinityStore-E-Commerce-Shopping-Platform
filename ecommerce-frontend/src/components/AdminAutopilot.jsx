import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { inrCompact } from '../lib/format';
import { Loader } from './Loader';
import { ago, waLink } from '../lib/when';

const MODES = [
  ['OFF', 'Off'],
  ['PREVIEW', 'Ask me first'],
  ['AUTO', 'Automatic'],
];

const RULE_ICON = { BACK_IN_STOCK: 'bi-bell', ABANDONED_CART: 'bi-bag-heart', REVIEW_REQUEST: 'bi-star', DAILY_DIGEST: 'bi-sunrise' };

/**
 * Autopilot: what the shop's automations did, what they prepared for the owner, and today's to-do list.
 * Customer-facing reminders start in "Ask me first" so nothing reaches a shopper unseen.
 */
export function AdminAutopilot({ onNavigate }) {
  const [data, setData] = useState(null);
  const [view, setView] = useState('needs');
  const [running, setRunning] = useState(false);
  const [open, setOpen] = useState(null);
  const toast = useToast();

  const load = () => ApiService.autopilot().then(setData).catch(() => toast.error('Could not load Autopilot'));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (!data) return <Loader message="Loading Autopilot…" />;

  const run = async () => {
    setRunning(true);
    try {
      const r = await ApiService.runAutopilot();
      const parts = [r.sent && `${r.sent} sent`, r.drafted && `${r.drafted} ready for you`, r.noMail && `${r.noMail} waiting for email setup`].filter(Boolean);
      toast.success(parts.length ? `Autopilot ran: ${parts.join(', ')}` : 'Autopilot ran: nothing new to do');
      await load();
    } catch {
      toast.error('Autopilot could not run');
    } finally {
      setRunning(false);
    }
  };

  const setMode = async (rule, mode) => {
    if (mode === rule.mode) return;
    try {
      await ApiService.setAutopilotMode(rule.key, mode);
      toast.success(`${rule.name}: ${MODES.find(m => m[0] === mode)[1]}`);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not change the setting');
    }
  };

  const act = async (fn, okMsg) => {
    try {
      await fn();
      toast.success(okMsg);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'That did not work');
    }
  };

  const needs = data.events.filter(e => e.status === 'DRAFT' || e.status === 'NO_MAIL');
  const shown = view === 'needs' ? needs : data.events;
  const s = data.stats;

  return (
    <div className="tab-content autopilot">
      <div className="ap-hero">
        <div>
          {data.scheduled
            ? <span className="ap-badge"><span className="ap-pulse" aria-hidden="true"></span> Autopilot is on</span>
            : <span className="ap-badge paused"><i className="bi bi-pause-circle" aria-hidden="true"></i> Schedule paused (AUTOMATION_ENABLED=false). Use Run now.</span>}
          <h2>Your shop's assistant</h2>
          <p>Checks the store every 15 minutes: follows up with shoppers, tells people when sarees are back and writes your to-do list.</p>
        </div>
        <div className="ap-hero-actions">
          <span className={`ap-mail ${data.mailConfigured ? 'ok' : 'off'}`}>
            <i className={`bi ${data.mailConfigured ? 'bi-envelope-check' : 'bi-envelope-slash'}`} aria-hidden="true"></i>
            {data.mailConfigured ? 'Email connected' : 'Email not set up'}
          </span>
          <button type="button" className="btn btn-primary" onClick={run} disabled={running}>
            <i className="bi bi-lightning-charge" aria-hidden="true"></i> {running ? 'Running…' : 'Run now'}
          </button>
        </div>
      </div>

      <div className="ap-stats">
        <Stat icon="bi-bag" label="Bags left behind" value={s.abandonedBags} sub={s.abandonedBags ? `${inrCompact(s.abandonedValue)} waiting` : 'none this week'} />
        <Stat icon="bi-bell" label="Waiting for restock" value={s.waitingForStock} sub="shoppers to notify" />
        <Stat icon="bi-fire" label="Hot leads" value={s.hotLeads} sub={`${s.openLeads} open in total`} />
        <Stat icon="bi-send-check" label="Sent this week" value={s.sentThisWeek} sub={`${s.draftsWaiting} waiting for you`} />
      </div>

      <section className="ap-section">
        <h3>Today's to-do</h3>
        {data.tasks.length === 0 ? (
          <p className="ap-empty"><i className="bi bi-check2-circle" aria-hidden="true"></i> All caught up. A good moment to add new sarees.</p>
        ) : (
          <ol className="ap-tasks">
            {data.tasks.map(t => (
              <li key={t.key} className={`ap-task p-${t.priority.toLowerCase()}`}>
                <span className="ap-prio">{t.priority === 'HIGH' ? 'Urgent' : t.priority === 'MEDIUM' ? 'Today' : 'Setup'}</span>
                <div>
                  <strong>{t.title}</strong>
                  <p>{t.detail}</p>
                </div>
                {t.tab && t.tab !== 'autopilot' && onNavigate && (
                  <button type="button" className="btn btn-outline" onClick={() => onNavigate(t.tab)}>{t.actionLabel}</button>
                )}
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="ap-section">
        <h3>Automations</h3>
        <div className="ap-rules">
          {data.rules.map(r => (
            <article key={r.key} className="ap-rule">
              <div className="ap-rule-head">
                <i className={`bi ${RULE_ICON[r.key] || 'bi-gear'}`} aria-hidden="true"></i>
                <div>
                  <strong>{r.name}</strong>
                  {r.customerFacing && <span className="ap-tag">Messages customers</span>}
                </div>
              </div>
              <p>{r.description}</p>
              <div className="ap-seg" role="radiogroup" aria-label={`${r.name} mode`}>
                {MODES.map(([m, label]) => (
                  <button key={m} type="button" role="radio" aria-checked={r.mode === m} className={r.mode === m ? 'on' : ''} onClick={() => setMode(r, m)}>{label}</button>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="ap-section">
        <div className="ap-msg-head">
          <h3>Messages</h3>
          <div className="ap-seg small" role="tablist">
            <button type="button" role="tab" aria-selected={view === 'needs'} className={view === 'needs' ? 'on' : ''} onClick={() => setView('needs')}>Needs you ({needs.length})</button>
            <button type="button" role="tab" aria-selected={view === 'all'} className={view === 'all' ? 'on' : ''} onClick={() => setView('all')}>History</button>
          </div>
        </div>
        {!data.mailConfigured && needs.length > 0 && (
          <p className="ap-note"><i className="bi bi-info-circle" aria-hidden="true"></i> Email isn't set up, so messages wait here. Send them on WhatsApp, or set <code>MAIL_USERNAME</code> and <code>MAIL_PASSWORD</code> on the server.</p>
        )}
        {shown.length === 0 ? (
          <p className="ap-empty">{view === 'needs' ? 'Nothing waiting for you.' : 'No messages yet. They appear here as Autopilot works.'}</p>
        ) : (
          <ul className="ap-msgs">
            {shown.map(e => (
              <li key={e.id} className={`ap-msg s-${e.status.toLowerCase()}`}>
                <button type="button" className="ap-msg-line" onClick={() => setOpen(open === e.id ? null : e.id)} aria-expanded={open === e.id}>
                  <i className={`bi ${RULE_ICON[e.ruleKey] || 'bi-envelope'}`} aria-hidden="true"></i>
                  <span className="ap-msg-title">{e.title}</span>
                  <span className={`ap-status s-${e.status.toLowerCase()}`}>{STATUS[e.status] || e.status}</span>
                  <small>{ago(e.createdAt, data.serverTime)}</small>
                </button>
                {open === e.id && (
                  <div className="ap-preview">
                    <div className="ap-meta">To: {e.recipient || (e.phone ? `+91 ${e.phone} (WhatsApp)` : '—')}{e.emailSubject && <> · Subject: <strong>{e.emailSubject}</strong></>}</div>
                    <pre>{e.body}</pre>
                    {e.status !== 'SENT' && e.status !== 'DISMISSED' && (
                      <div className="ap-actions">
                        {e.recipient && (
                          <button type="button" className="btn btn-primary" onClick={() => act(() => ApiService.sendAutopilotMessage(e.id), 'Email sent')}>
                            <i className="bi bi-send" aria-hidden="true"></i> Send email
                          </button>
                        )}
                        {e.phone && (
                          <a className="btn btn-outline wa" href={waLink(e.phone, e.body)} target="_blank" rel="noopener noreferrer">
                            <i className="bi bi-whatsapp" aria-hidden="true"></i> Open in WhatsApp
                          </a>
                        )}
                        {e.phone && (
                          <button type="button" className="btn btn-outline" onClick={() => act(() => ApiService.closeAutopilotMessage(e.id, true), 'Marked as sent')}>Mark as sent</button>
                        )}
                        <button type="button" className="btn btn-outline" onClick={() => act(() => ApiService.closeAutopilotMessage(e.id, false), 'Skipped')}>Skip</button>
                      </div>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

const STATUS = { SENT: 'Sent', DRAFT: 'Ready for you', NO_MAIL: 'Waiting for email setup', DISMISSED: 'Skipped' };

function Stat({ icon, label, value, sub }) {
  return (
    <div className="ap-stat">
      <i className={`bi ${icon}`} aria-hidden="true"></i>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{sub}</small>
    </div>
  );
}
