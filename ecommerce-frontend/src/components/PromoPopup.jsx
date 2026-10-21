import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { imageSrc } from '../lib/format';
import { analyticsConfigured, getConsent } from '../lib/analytics';

const pad = (n) => String(n).padStart(2, '0');

function useCountdown(endsAt, on) {
  const [left, setLeft] = useState(null);
  useEffect(() => {
    if (!on || !endsAt) { setLeft(null); return undefined; }
    const tick = () => setLeft(Math.max(0, new Date(endsAt).getTime() - Date.now()));
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [endsAt, on]);
  if (left === null) return null;
  const s = Math.floor(left / 1000);
  return { d: Math.floor(s / 86400), h: Math.floor((s % 86400) / 3600), m: Math.floor((s % 3600) / 60), s: s % 60, done: left === 0 };
}

/** The look of an offer pop-up. Used for the real pop-up and for the live preview in the admin. */
export function PromoCard({ promo, onClose, onCopy, onCta, compact = false }) {
  const cd = useCountdown(promo.endsAt, promo.countdown);
  const cta = promo.ctaLabel && promo.ctaUrl;
  const internal = cta && promo.ctaUrl.startsWith('/');
  return (
    <div className={`promo promo-${(promo.theme || 'MAROON').toLowerCase()} ${compact ? 'compact' : ''}`}>
      {onClose && <button type="button" className="promo-x" onClick={onClose} aria-label="Close offer"><i className="bi bi-x-lg" aria-hidden="true"></i></button>}
      {promo.imageUrl && <div className="promo-img"><img src={imageSrc(promo.imageUrl)} alt="" /></div>}
      <div className="promo-body">
        <span className="promo-eyebrow">Special offer</span>
        <h2 id="promo-title">{promo.headline || 'Your headline'}</h2>
        {promo.message && <p>{promo.message}</p>}
        {cd && !cd.done && (
          <div className="promo-clock" role="timer" aria-label="Time left on this offer">
            {cd.d > 0 && <span><b>{cd.d}</b>days</span>}
            <span><b>{pad(cd.h)}</b>hrs</span><span><b>{pad(cd.m)}</b>min</span><span><b>{pad(cd.s)}</b>sec</span>
          </div>
        )}
        {promo.couponCode && (
          <div className="promo-code">
            <span>Use code</span>
            <code>{promo.couponCode}</code>
            <button type="button" onClick={() => onCopy?.(promo.couponCode)}>Copy</button>
          </div>
        )}
        <div className="promo-actions">
          {cta && (internal
            ? <Link className="promo-cta" to={promo.ctaUrl} onClick={onCta}>{promo.ctaLabel}</Link>
            : <a className="promo-cta" href={promo.ctaUrl} target="_blank" rel="noopener noreferrer" onClick={onCta}>{promo.ctaLabel}</a>)}
          {onClose && <button type="button" className="promo-skip" onClick={onClose}>No thanks</button>}
        </div>
      </div>
    </div>
  );
}

const key = (p) => `rsm_promo_${p.id}_${p.version}`;

function alreadySeen(p) {
  try {
    if (p.frequency === 'ALWAYS') return false;
    if (p.frequency === 'SESSION') return sessionStorage.getItem(key(p)) === '1';
    const at = Number(localStorage.getItem(key(p)) || 0);
    return at && Date.now() - at < 24 * 3600 * 1000;
  } catch { return false; }
}

function remember(p) {
  try {
    if (p.frequency === 'SESSION') sessionStorage.setItem(key(p), '1');
    else if (p.frequency === 'DAY') localStorage.setItem(key(p), String(Date.now()));
  } catch { /* private mode: the offer may show again, which is harmless */ }
}

/**
 * Shows the live offer pop-up the owner designed. It waits for the cookie choice, respects how often the owner wants it
 * shown, never appears on checkout or admin pages, and closes with the Escape key or a click outside.
 */
export function PromoPopup() {
  const { pathname } = useLocation();
  const toast = useToast();
  const [promo, setPromo] = useState(null);
  const [open, setOpen] = useState(false);
  const shown = useRef(false);
  const box = useRef(null);
  // Automated browsers (end-to-end tests) are left alone unless a test asks to see the pop-up.
  const automated = typeof navigator !== 'undefined' && navigator.webdriver && localStorage.getItem('rsm_promo_force') !== '1';
  const quiet = automated || /^\/(admin|checkout|order|login|register|reset-password)/.test(pathname);

  useEffect(() => { ApiService.storePopup().then(setPromo).catch(() => {}); }, []);

  useEffect(() => {
    if (!promo || quiet || shown.current || alreadySeen(promo)) return undefined;
    const reveal = () => {
      if (shown.current) return;
      if (analyticsConfigured() && !getConsent()) return; // the cookie banner goes first
      shown.current = true;
      remember(promo);
      setOpen(true);
    };
    const timers = [];
    let onLeave; let onScroll;
    if (promo.trigger === 'EXIT' && window.matchMedia('(hover: hover)').matches) {
      onLeave = (e) => { if (e.clientY <= 0 && !e.relatedTarget) reveal(); };
      document.addEventListener('mouseout', onLeave);
    } else if (promo.trigger === 'EXIT') {
      // phones have no cursor to leave with: show after the shopper has read well down the page
      onScroll = () => { if (window.scrollY > document.documentElement.scrollHeight * 0.4) timers.push(setTimeout(reveal, 6000)); };
      window.addEventListener('scroll', onScroll, { passive: true, once: true });
    } else {
      timers.push(setTimeout(function wait() { if (analyticsConfigured() && !getConsent()) timers.push(setTimeout(wait, 1000)); else reveal(); }, Math.max(promo.delaySeconds, 1) * 1000));
    }
    return () => { timers.forEach(clearTimeout); if (onLeave) document.removeEventListener('mouseout', onLeave); if (onScroll) window.removeEventListener('scroll', onScroll); };
  }, [promo, quiet]);

  useEffect(() => {
    if (!open) return undefined;
    const prev = document.activeElement;
    document.body.classList.add('no-scroll');
    box.current?.querySelector('a, button')?.focus();
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
      if (e.key === 'Tab') {
        const f = [...box.current.querySelectorAll('a[href], button:not([disabled])')];
        if (!f.length) return;
        const first = f[0]; const last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('no-scroll'); prev?.focus?.(); };
  }, [open]);

  if (!open || !promo || quiet) return null;
  const copy = (code) => navigator.clipboard?.writeText(code).then(() => toast.success(`Code ${code} copied`), () => toast.info(`Your code is ${code}`));
  return (
    <div className="promo-back" onMouseDown={(e) => { if (e.target === e.currentTarget) setOpen(false); }}>
      <div className="promo-dialog" role="dialog" aria-modal="true" aria-labelledby="promo-title" ref={box}>
        <PromoCard promo={promo} onClose={() => setOpen(false)} onCopy={copy} onCta={() => setOpen(false)} />
      </div>
    </div>
  );
}
