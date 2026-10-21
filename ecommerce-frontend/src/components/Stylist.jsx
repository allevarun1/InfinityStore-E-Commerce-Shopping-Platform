import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useStore } from '../contexts/StoreContext';
import { useCartActions } from '../lib/useCartActions';
import { inrCompact, imageSrc } from '../lib/format';

const KEY = 'infinity_stylist';
const NUDGE_KEY = 'infinity_stylist_nudged';
const HIDDEN_ON = ['/checkout', '/admin', '/login', '/register', '/reset-password', '/order/success'];

const WELCOME = {
  from: 'bot',
  text: "Namaste! I'm your Rekha Shopping Mall stylist. Tell me the occasion, your budget or a colour you love, and I'll pick sarees for you.",
  suggestions: ['Wedding sarees', 'Under ₹3,000', 'Silk sarees', "Today's deals"],
};

function load() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY));
    if (saved && Array.isArray(saved.messages)) return saved;
  } catch { /* storage unavailable */ }
  return { messages: [WELCOME], filters: null };
}

/**
 * The shopping assistant: a chat that recommends real sarees from the catalogue, answers delivery and
 * payment questions, and takes a WhatsApp number or email when the shopper wants a person to follow up.
 */
export function Stylist() {
  const { pathname } = useLocation();
  const { info } = useStore();
  const [open, setOpen] = useState(false);
  const [state, setState] = useState(load);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [nudge, setNudge] = useState(false);
  const listRef = useRef(null);
  const inputRef = useRef(null);
  const hidden = HIDDEN_ON.some(p => pathname.startsWith(p));

  useEffect(() => {
    try { sessionStorage.setItem(KEY, JSON.stringify({ ...state, messages: state.messages.slice(-30) })); } catch { /* ignore */ }
  }, [state]);

  useEffect(() => {
    if (open) {
      setNudge(false);
      listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
    }
  }, [open, state.messages.length]);

  useEffect(() => {
    if (open) inputRef.current?.focus({ preventScroll: true });
  }, [open]);

  // One gentle offer of help per visit, after the shopper has had time to browse.
  useEffect(() => {
    let seen = false;
    try { seen = sessionStorage.getItem(NUDGE_KEY) === '1'; } catch { /* ignore */ }
    if (seen || hidden) return undefined;
    const t = setTimeout(() => {
      setNudge(true);
      try { sessionStorage.setItem(NUDGE_KEY, '1'); } catch { /* ignore */ }
    }, 25000);
    return () => clearTimeout(t);
  }, [hidden]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  if (hidden) return null;

  const send = async (text) => {
    const message = (text ?? input).trim();
    if (!message || busy) return;
    setInput('');
    setState(s => ({ ...s, messages: [...s.messages, { from: 'me', text: message }] }));
    setBusy(true);
    try {
      const r = await ApiService.assistantChat(message, state.filters);
      setState(s => ({
        filters: r.filters,
        messages: [...s.messages, {
          from: 'bot', text: r.reply, products: r.products, suggestions: r.suggestions,
          link: r.link, linkLabel: r.linkLabel, contact: r.offerContact, interest: r.summary || message,
        }],
      }));
    } catch (err) {
      const text = err.response?.status === 429
        ? 'You are typing faster than I can think! Give me a few seconds and try again.'
        : "Sorry, I couldn't reach the shop just now. Please try again in a moment.";
      setState(s => ({ ...s, messages: [...s.messages, { from: 'bot', text }] }));
    } finally {
      setBusy(false);
    }
  };

  const last = state.messages[state.messages.length - 1];
  const withWa = Boolean(info?.whatsappNumber);

  return (
    <>
      {!open && (
        <div className={`ai-launch${withWa ? ' with-wa' : ''}`}>
          {nudge && (
            <div className="ai-nudge" role="status">
              <button type="button" className="ai-nudge-x" aria-label="Dismiss" onClick={() => setNudge(false)}>×</button>
              <button type="button" className="ai-nudge-body" onClick={() => setOpen(true)}>
                Looking for something special? <strong>I can help you choose.</strong>
              </button>
            </div>
          )}
          <button type="button" className="ai-fab" onClick={() => setOpen(true)} aria-label="Ask our stylist">
            <i className="bi bi-stars" aria-hidden="true"></i><span>Ask our stylist</span>
          </button>
        </div>
      )}

      {open && (
        <section className="ai-panel" role="dialog" aria-label="Shopping assistant">
          <header className="ai-head">
            <span className="ai-avatar" aria-hidden="true">R</span>
            <div>
              <strong>Rekha Shopping Mall Stylist</strong>
              <small><span className="ai-dot" aria-hidden="true"></span> Online · picks from live stock</small>
            </div>
            <button type="button" className="ai-reset" onClick={() => setState({ messages: [WELCOME], filters: null })} aria-label="Start a new conversation" title="Start over">
              <i className="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
            </button>
            <button type="button" className="ai-close" onClick={() => setOpen(false)} aria-label="Close assistant">
              <i className="bi bi-x-lg" aria-hidden="true"></i>
            </button>
          </header>

          <div className="ai-list" ref={listRef} aria-live="polite">
            {state.messages.map((m, i) => (
              <Message key={i} m={m} onClose={() => setOpen(false)} />
            ))}
            {busy && <div className="ai-msg bot"><div className="ai-bubble ai-typing" aria-label="Typing"><i></i><i></i><i></i></div></div>}
          </div>

          {!busy && last?.suggestions?.length > 0 && (
            <div className="ai-chips">
              {last.suggestions.map(s => (
                <button type="button" key={s} className="ai-chip" onClick={() => send(s)}>{s}</button>
              ))}
            </div>
          )}

          <form className="ai-input" onSubmit={(e) => { e.preventDefault(); send(); }}>
            <input ref={inputRef} value={input} onChange={(e) => setInput(e.target.value)} maxLength={300}
              placeholder="Try: silk for a wedding under 5k" aria-label="Message the stylist" />
            <button type="submit" className="ai-send" disabled={!input.trim() || busy} aria-label="Send">
              <i className="bi bi-send-fill" aria-hidden="true"></i>
            </button>
          </form>
        </section>
      )}
    </>
  );
}

function Message({ m, onClose }) {
  const { addToCart } = useCartActions();
  const [added, setAdded] = useState({});
  if (m.from === 'me') {
    return <div className="ai-msg me"><div className="ai-bubble">{m.text}</div></div>;
  }
  const add = async (p) => {
    if (await addToCart(p, 1)) setAdded(a => ({ ...a, [p.id]: true }));
  };
  return (
    <div className="ai-msg bot">
      <div className="ai-bubble">{m.text}</div>
      {m.products?.length > 0 && (
        <div className="ai-cards">
          {m.products.map(p => (
            <article className="ai-card" key={p.id}>
              <Link to={`/products/${p.id}`} onClick={onClose} className="ai-card-img">
                <img src={imageSrc(p.imageUrl)} alt="" loading="lazy" />
              </Link>
              <div className="ai-card-body">
                <Link to={`/products/${p.id}`} onClick={onClose} className="ai-card-name">{p.name}</Link>
                <div className="ai-card-price">
                  {p.dealPrice != null ? <><strong>{inrCompact(p.dealPrice)}</strong> <s>{inrCompact(p.price)}</s></> : <strong>{inrCompact(p.price)}</strong>}
                </div>
                {p.why && <small>{p.why}</small>}
                {p.inStock ? (
                  <button type="button" className="ai-add" disabled={added[p.id]} onClick={() => add(p)}>
                    {added[p.id] ? <><i className="bi bi-check2" aria-hidden="true"></i> In your bag</> : <><i className="bi bi-bag-plus" aria-hidden="true"></i> Add to bag</>}
                  </button>
                ) : (
                  <Link to={`/products/${p.id}#notify`} onClick={onClose} className="ai-add ghost">Notify me</Link>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
      {m.link && <Link className="ai-link" to={m.link} onClick={onClose}>{m.linkLabel || 'See more'} <i className="bi bi-arrow-right" aria-hidden="true"></i></Link>}
      {m.contact && <ContactCard interest={m.interest} />}
    </div>
  );
}

/** "Have someone help me": a WhatsApp number or an email, with explicit consent. */
function ContactCard({ interest }) {
  const [name, setName] = useState('');
  const [reach, setReach] = useState('');
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);
  const [error, setError] = useState(null);

  if (done) return <div className="ai-contact done"><i className="bi bi-check-circle-fill" aria-hidden="true"></i> {done}</div>;

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    const isEmail = reach.includes('@');
    setBusy(true);
    try {
      const r = await ApiService.captureLead({
        name: name.trim() || null,
        email: isEmail ? reach.trim() : null,
        phone: isEmail ? null : reach.trim(),
        interest: interest || null,
        source: 'assistant_contact',
        consent,
      });
      setDone(r.message);
    } catch (err) {
      setError(err.response?.data?.message || 'Please check the details and try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="ai-contact" onSubmit={submit}>
      <strong>Want a stylist to help you personally?</strong>
      <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name (optional)" aria-label="Your name" maxLength={80} />
      <input value={reach} onChange={(e) => setReach(e.target.value)} placeholder="WhatsApp number or email" aria-label="WhatsApp number or email" required maxLength={120} inputMode="email" />
      <label className="ai-consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>Rekha Shopping Mall may contact me about this request.</span>
      </label>
      {error && <p className="ai-error" role="alert">{error}</p>}
      <button type="submit" className="btn btn-primary" disabled={busy || !consent || !reach.trim()}>{busy ? 'Sending…' : 'Request a call back'}</button>
    </form>
  );
}
