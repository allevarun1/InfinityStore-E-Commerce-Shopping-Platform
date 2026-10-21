import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useStore } from '../contexts/StoreContext';
import { whatsappLink } from './Growth';

const DAYS = ['Today', 'Tomorrow', 'This weekend', 'Next week'];
const SLOTS = ['Morning (10-12)', 'Afternoon (12-4)', 'Evening (4-8)'];
const FOR = ['Wedding or bridal', 'Festival', 'Party or function', 'Office or daily wear', 'Gift'];
const BUDGET = ['Under ₹3,000', '₹3,000 - ₹7,000', '₹7,000 - ₹15,000', 'Above ₹15,000'];

function Done({ title, text }) {
  return (
    <div className="ps-done" role="status">
      <i className="bi bi-check-circle-fill" aria-hidden="true"></i>
      <div><strong>{title}</strong><p>{text}</p></div>
    </div>
  );
}

function Consent({ checked, onChange }) {
  return (
    <label className="ai-consent">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span>InfinityStore may contact me about this request.</span>
    </label>
  );
}

function useSubmit(build, source) {
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);
  const [error, setError] = useState(null);
  const submit = async (e, values, consent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const reach = values.reach.trim();
      const isEmail = reach.includes('@');
      const r = await ApiService.captureLead({
        name: values.name.trim() || null,
        email: isEmail ? reach : null,
        phone: isEmail ? null : reach,
        interest: build(values),
        productId: null,
        source,
        consent,
      });
      setDone(r.message);
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.errors?.consent || 'Please check the details and try again.');
    } finally {
      setBusy(false);
    }
  };
  return { busy, done, error, submit };
}

function VideoCall({ product }) {
  const [v, setV] = useState({ name: '', reach: '', day: DAYS[1], slot: SLOTS[2], purpose: FOR[0], budget: BUDGET[1], note: '' });
  const [consent, setConsent] = useState(false);
  const s = useSubmit((x) => `Video call, ${x.day} ${x.slot}: ${x.purpose}, budget ${x.budget}${product ? `, interested in ${product.name}` : ''}${x.note ? `. ${x.note}` : ''}`.slice(0, 300), 'video_call');
  const set = (k) => (e) => setV(o => ({ ...o, [k]: e.target.value }));
  if (s.done) return <Done title="Your call is requested" text="We'll message you to confirm the time. Keep your WhatsApp handy." />;
  return (
    <form className="ps-form" onSubmit={(e) => s.submit(e, v, consent)}>
      {product && <p className="ps-product"><i className="bi bi-bag-heart" aria-hidden="true"></i> About: <strong>{product.name}</strong></p>}
      <div className="field-group"><label htmlFor="vc-name">Your name</label><input id="vc-name" value={v.name} onChange={set('name')} maxLength={80} autoComplete="name" /></div>
      <div className="field-group"><label htmlFor="vc-reach">WhatsApp number</label><input id="vc-reach" required value={v.reach} onChange={set('reach')} maxLength={20} inputMode="tel" autoComplete="tel" placeholder="98765 43210" /></div>
      <div className="ps-grid">
        <div className="field-group"><label htmlFor="vc-day">Day</label><select id="vc-day" value={v.day} onChange={set('day')}>{DAYS.map(d => <option key={d}>{d}</option>)}</select></div>
        <div className="field-group"><label htmlFor="vc-slot">Time</label><select id="vc-slot" value={v.slot} onChange={set('slot')}>{SLOTS.map(d => <option key={d}>{d}</option>)}</select></div>
        <div className="field-group"><label htmlFor="vc-for">Shopping for</label><select id="vc-for" value={v.purpose} onChange={set('purpose')}>{FOR.map(d => <option key={d}>{d}</option>)}</select></div>
        <div className="field-group"><label htmlFor="vc-budget">Budget</label><select id="vc-budget" value={v.budget} onChange={set('budget')}>{BUDGET.map(d => <option key={d}>{d}</option>)}</select></div>
      </div>
      <div className="field-group"><label htmlFor="vc-note">Anything we should know? (optional)</label><input id="vc-note" value={v.note} onChange={set('note')} maxLength={120} placeholder="Colours you like, who it's for…" /></div>
      <Consent checked={consent} onChange={setConsent} />
      {s.error && <p className="ai-error" role="alert">{s.error}</p>}
      <button type="submit" className="btn btn-primary" disabled={s.busy || !consent || !v.reach.trim()}>{s.busy ? 'Sending…' : 'Request a video call'}</button>
    </form>
  );
}

function Enquiry() {
  const [v, setV] = useState({ name: '', reach: '', kind: 'Wedding / trousseau', qty: '', date: '', note: '' });
  const [consent, setConsent] = useState(false);
  const s = useSubmit((x) => `Enquiry: ${x.kind}${x.qty ? `, about ${x.qty} sarees` : ''}${x.date ? `, needed by ${x.date}` : ''}${x.note ? `. ${x.note}` : ''}`.slice(0, 300), 'enquiry');
  const set = (k) => (e) => setV(o => ({ ...o, [k]: e.target.value }));
  if (s.done) return <Done title="Enquiry received" text="Our team will get back to you with options and prices." />;
  return (
    <form className="ps-form" onSubmit={(e) => s.submit(e, v, consent)}>
      <div className="field-group"><label htmlFor="en-name">Your name</label><input id="en-name" value={v.name} onChange={set('name')} maxLength={80} autoComplete="name" /></div>
      <div className="field-group"><label htmlFor="en-reach">WhatsApp number or email</label><input id="en-reach" required value={v.reach} onChange={set('reach')} maxLength={120} autoComplete="tel" /></div>
      <div className="ps-grid">
        <div className="field-group"><label htmlFor="en-kind">What is it for?</label>
          <select id="en-kind" value={v.kind} onChange={set('kind')}>
            {['Wedding / trousseau', 'Gifting for a group or company', 'Resale / boutique', 'Custom colour or design'].map(d => <option key={d}>{d}</option>)}
          </select></div>
        <div className="field-group"><label htmlFor="en-qty">How many sarees?</label><input id="en-qty" value={v.qty} onChange={set('qty')} maxLength={10} inputMode="numeric" placeholder="e.g. 10" /></div>
        <div className="field-group"><label htmlFor="en-date">Needed by</label><input id="en-date" type="date" value={v.date} onChange={set('date')} /></div>
      </div>
      <div className="field-group"><label htmlFor="en-note">Tell us more</label><textarea id="en-note" rows={3} value={v.note} onChange={set('note')} maxLength={150} placeholder="Colours, fabrics, budget per saree…" /></div>
      <Consent checked={consent} onChange={setConsent} />
      {s.error && <p className="ai-error" role="alert">{s.error}</p>}
      <button type="submit" className="btn btn-primary" disabled={s.busy || !consent || !v.reach.trim()}>{s.busy ? 'Sending…' : 'Send enquiry'}</button>
    </form>
  );
}

/** Personal shopping: a live video call to see sarees, or a wedding / bulk / custom enquiry. Both create leads. */
export function PersonalShopping() {
  const { info } = useStore();
  const product = useLocation().state?.product || null;
  const [tab, setTab] = useState('video');
  useEffect(() => { document.title = 'Personal shopping | InfinityStore'; }, []);

  return (
    <div className="ps">
      <section className="ps-hero">
        <span className="eyebrow">Personal shopping</span>
        <h1>See it before you choose it</h1>
        <p>Book a live video call and we'll show you sarees in daylight, drape them for you and help you pick. Or send us a wedding, bulk or custom enquiry.</p>
        <ul className="ps-points">
          <li><i className="bi bi-camera-video" aria-hidden="true"></i> Live colour and drape check</li>
          <li><i className="bi bi-person-heart" aria-hidden="true"></i> Advice for the occasion and budget</li>
          <li><i className="bi bi-cash-coin" aria-hidden="true"></i> No obligation, cash on delivery</li>
        </ul>
      </section>

      <div className="ps-card">
        <div className="ps-tabs" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'video'} className={tab === 'video' ? 'on' : ''} onClick={() => setTab('video')}><i className="bi bi-camera-video" aria-hidden="true"></i> Video call</button>
          <button type="button" role="tab" aria-selected={tab === 'enquiry'} className={tab === 'enquiry' ? 'on' : ''} onClick={() => setTab('enquiry')}><i className="bi bi-gift" aria-hidden="true"></i> Wedding, bulk &amp; custom</button>
        </div>
        {tab === 'video' ? <VideoCall product={product} /> : <Enquiry />}
        {info?.whatsappNumber && (
          <p className="ps-alt">Prefer to chat now? <a href={whatsappLink(info.whatsappNumber, 'Hi InfinityStore, I would like help choosing a saree.')} target="_blank" rel="noopener noreferrer"><i className="bi bi-whatsapp" aria-hidden="true"></i> Message us on WhatsApp</a></p>
        )}
      </div>
    </div>
  );
}
