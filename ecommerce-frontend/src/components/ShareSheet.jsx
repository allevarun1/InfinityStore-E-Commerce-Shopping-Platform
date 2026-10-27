import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { imageSrc, inr } from '../lib/format';
import {
  TEMPLATES, captionFor, collectionMessage, defaultTemplate, markSent, productMessage, sentAt, sharePicture, statusImage, waLink,
} from '../lib/share';

const ago = (t) => {
  const d = Math.floor((Date.now() - t) / 86400000);
  return d <= 0 ? 'today' : d === 1 ? 'yesterday' : `${d} days ago`;
};

const REASON_FILTERS = [
  ['all', 'Everyone'],
  ['Saved this saree', 'Saved it'],
  ['Asked', 'Asked about it'],
  ['Bought', 'Bought similar'],
  ['Repeat customer', 'Repeat customers'],
];

/**
 * Tell people about a saree on WhatsApp. Pick a message, then either send it to everyone you choose in WhatsApp
 * (a chat, group or broadcast list), share the picture and text from a phone (WhatsApp Status, Instagram), or go down
 * the list of people most likely to buy and send each a personal message with one tap. Nothing sends by itself.
 */
export function ShareSheet({ products, onClose, startTemplate }) {
  const toast = useToast();
  const many = products.length > 1;
  const p = products[0];
  const [key, setKey] = useState(startTemplate || defaultTemplate(p));
  const [text, setText] = useState('');
  const [edited, setEdited] = useState(false);
  const [img, setImg] = useState(null);           // { blob, url }
  const [people, setPeople] = useState(null);
  const [filter, setFilter] = useState('all');
  const [, setTick] = useState(0);                // re-render after marking someone as sent
  const box = useRef(null);

  useEffect(() => { if (!edited) setText(many ? collectionMessage(products) : productMessage(p, key)); }, [key, edited, many, p, products]);

  useEffect(() => {
    if (many) return undefined;
    let gone = false; let made = null;
    setImg(null);
    statusImage(p, key).then(blob => { if (gone || !blob) return; made = URL.createObjectURL(blob); setImg({ blob, url: made }); }).catch(() => {});
    return () => { gone = true; if (made) URL.revokeObjectURL(made); };
  }, [p, key, many]);

  useEffect(() => {
    if (many) return;
    ApiService.shareAudience(p.id).then(setPeople).catch(() => setPeople([]));
  }, [p, many]);

  useEffect(() => {
    const prev = document.activeElement;
    box.current?.querySelector('button')?.focus();
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    document.body.classList.add('no-scroll');
    return () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('no-scroll'); prev?.focus?.(); };
  }, [onClose]);

  const shown = useMemo(() => (people || []).filter(x => filter === 'all' || x.reasons.some(r => r.startsWith(filter))), [people, filter]);
  const scope = many ? 'collection' : `p${p.id}`;

  const copy = (t, what) => navigator.clipboard?.writeText(t).then(() => toast.success(`${what} copied`), () => toast.info('Select the text and copy it'));
  const download = () => {
    if (!img) return;
    const a = document.createElement('a'); a.href = img.url; a.download = `${p.name.replace(/[^\w-]+/g, '-').toLowerCase()}-status.png`; a.click();
  };
  const sharePic = async () => {
    if (!img) return;
    const ok = await sharePicture(img.blob, p.name, text);
    if (!ok) { download(); toast.info('Picture saved. Open WhatsApp, tap Status and pick it from your photos.'); }
  };
  const personal = (x) => (edited || many ? text.replace(/^Hi [^!,]*[!,] ?/, '').replace(/^/, `Hi ${x.name.split(' ')[0]}! `) : productMessage(p, key, x));
  const sendTo = (x) => { markSent(scope, x.phone); setTick(t => t + 1); };

  return (
    <div className="sh-back" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="share" role="dialog" aria-modal="true" aria-labelledby="share-title" ref={box}>
        <header className="share-head">
          <div>
            <span className="eyebrow">Share on WhatsApp</span>
            <h2 id="share-title">{many ? `${products.length} new sarees` : p.name}</h2>
          </div>
          <button type="button" className="share-x" onClick={onClose} aria-label="Close"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
        </header>

        <div className="share-body">
          {!many && (
            <aside className="share-pic">
              {img ? <img src={img.url} alt={`Status picture for ${p.name}`} /> : <div className="share-pic-wait" aria-label="Making the picture"><span></span></div>}
              <div className="share-pic-actions">
                <button type="button" className="btn btn-primary" onClick={sharePic} disabled={!img}><i className="bi bi-share" aria-hidden="true"></i> Share picture + text</button>
                <button type="button" className="btn btn-outline" onClick={download} disabled={!img}><i className="bi bi-download" aria-hidden="true"></i> Save for Status</button>
                <button type="button" className="text-link" onClick={() => copy(captionFor(p, key), 'Instagram caption')}>Copy Instagram caption</button>
              </div>
            </aside>
          )}

          <section className="share-msg">
            {!many && (
              <div className="chips" role="group" aria-label="Message">
                {TEMPLATES.map(t => (
                  <button key={t.key} type="button" className={key === t.key ? 'on' : ''} aria-pressed={key === t.key}
                    onClick={() => { setEdited(false); setKey(t.key); }}>{t.label}</button>
                ))}
              </div>
            )}
            <label htmlFor="share-text" className="sr-only">Message</label>
            <textarea id="share-text" value={text} rows={many ? 14 : 10} onChange={(e) => { setText(e.target.value); setEdited(true); }} />
            <div className="share-send">
              <a className="btn btn-wa" href={waLink(null, text)} target="_blank" rel="noopener noreferrer" onClick={() => markSent(scope, 'broadcast')}>
                <i className="bi bi-whatsapp" aria-hidden="true"></i> Send on WhatsApp
              </a>
              <button type="button" className="btn btn-outline" onClick={() => copy(text, 'Message')}><i className="bi bi-clipboard" aria-hidden="true"></i> Copy</button>
            </div>
            <p className="share-hint">
              <i className="bi bi-lightbulb" aria-hidden="true"></i>
              WhatsApp opens with the message ready. Pick a <b>broadcast list</b> to reach many customers at once: they get it as a private message,
              and only people who saved your number receive it.
              {sentAt(scope, 'broadcast') && <> You last sent this {ago(sentAt(scope, 'broadcast'))}.</>}
            </p>
          </section>
        </div>

        {!many && (
          <section className="share-people" aria-labelledby="people-title">
            <div className="share-people-head">
              <div>
                <h3 id="people-title">Most likely to buy {people && people.length > 0 && <small>{people.length}</small>}</h3>
                <p>From your orders, wishlists and enquiries. Each gets a personal message; tap to open the chat.</p>
              </div>
              {people && people.length > 0 && (
                <div className="chips" role="group" aria-label="Show">
                  {REASON_FILTERS.map(([k, l]) => <button key={k} type="button" className={filter === k ? 'on' : ''} aria-pressed={filter === k} onClick={() => setFilter(k)}>{l}</button>)}
                </div>
              )}
            </div>
            {people === null && <p className="ap-empty">Finding customers…</p>}
            {people && people.length === 0 && (
              <p className="ap-empty">No one to suggest yet. As customers order, save sarees or ask about them, they will appear here.
                Until then, use <b>Send on WhatsApp</b> above to reach your contacts.</p>
            )}
            <ul className="people">
              {shown.map(x => {
                const sent = sentAt(scope, x.phone);
                return (
                  <li key={x.key} className={sent ? 'sent' : ''}>
                    <span className="avatar" aria-hidden="true">{x.name[0]?.toUpperCase()}</span>
                    <div className="people-who">
                      <strong>{x.name}</strong>
                      <span className="people-why">{x.reasons.slice(0, 3).map(r => <em key={r}>{r}</em>)}</span>
                      {x.orders > 0 && <small>{x.orders} {x.orders === 1 ? 'order' : 'orders'} · {inr(x.spent, { decimals: 0 })}</small>}
                    </div>
                    {sent ? <span className="people-sent"><i className="bi bi-check2" aria-hidden="true"></i> Sent {ago(sent)}</span> : null}
                    <a className="btn btn-wa sm" href={waLink(x.phone, personal(x))} target="_blank" rel="noopener noreferrer" onClick={() => sendTo(x)}
                      aria-label={`Send to ${x.name} on WhatsApp`}>
                      <i className="bi bi-whatsapp" aria-hidden="true"></i> {sent ? 'Again' : 'Send'}
                    </a>
                  </li>
                );
              })}
            </ul>
            {people && people.length > 0 && <p className="share-hint small">Send personal messages, the way you would in the shop. Mark a lead as “Lost” and they won't be suggested again.</p>}
          </section>
        )}
        {many && (
          <ul className="share-strip">
            {products.slice(0, 8).map(x => <li key={x.id}><img src={imageSrc(x.imageUrl)} alt="" loading="lazy" /><span>{x.name}</span></li>)}
          </ul>
        )}
      </div>
    </div>
  );
}
