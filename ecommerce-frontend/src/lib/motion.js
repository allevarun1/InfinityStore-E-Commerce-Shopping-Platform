import { useEffect, useRef, useState } from 'react';

/** True when the shopper asked their device for less motion. Everything animated here checks it. */
export const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** One observer shared by every revealed element, so a long product grid costs almost nothing. */
let sharedIo = null;
const watched = new WeakMap();
function observer() {
  if (sharedIo || typeof IntersectionObserver === 'undefined') return sharedIo;
  sharedIo = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      watched.get(e.target)?.();
      watched.delete(e.target);
      sharedIo.unobserve(e.target);
    });
  }, { rootMargin: '0px 0px -6% 0px', threshold: 0.08 });
  return sharedIo;
}

/** Returns [ref, seen]. `seen` flips once when the element first scrolls into view. */
export function useInView() {
  const ref = useRef(null);
  const [seen, setSeen] = useState(false);
  useEffect(() => {
    const el = ref.current;
    const io = observer();
    if (!el || !io || reducedMotion()) { setSeen(true); return undefined; }
    watched.set(el, () => setSeen(true));
    io.observe(el);
    return () => { watched.delete(el); io.unobserve(el); };
  }, []);
  return [ref, seen];
}

/** Counts up to `to` once visible. Under reduced motion it simply shows the final number. */
export function useCountUp(to, { duration = 1100, eager = false } = {}) {
  const [ref, inView] = useInView();
  const [late, setLate] = useState(false);
  useEffect(() => { if (!eager) return undefined; const t = setTimeout(() => setLate(true), 1200); return () => clearTimeout(t); }, [eager]);
  const seen = inView || late;
  const [value, setValue] = useState(reducedMotion() ? to : 0);
  useEffect(() => {
    if (!seen) return undefined;
    if (reducedMotion()) { setValue(to); return undefined; }
    let raf;
    const t0 = performance.now();
    const tick = (t) => {
      const p = Math.min(1, (t - t0) / duration);
      setValue(Math.round(to * (1 - Math.pow(1 - p, 3))));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [seen, to, duration]);
  return [ref, value];
}

/** Soft 3D tilt and a moving highlight that follow the pointer. Mouse only, never on touch. */
export function useTilt(max = 7) {
  const ref = useRef(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || reducedMotion() || !window.matchMedia('(hover: hover) and (pointer: fine)').matches) return undefined;
    let raf = 0;
    const move = (e) => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        const r = el.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - 0.5;
        const y = (e.clientY - r.top) / r.height - 0.5;
        el.style.setProperty('--rx', `${(-y * max).toFixed(2)}deg`);
        el.style.setProperty('--ry', `${(x * max).toFixed(2)}deg`);
        el.style.setProperty('--mx', `${((x + 0.5) * 100).toFixed(0)}%`);
        el.style.setProperty('--my', `${((y + 0.5) * 100).toFixed(0)}%`);
      });
    };
    const leave = () => { el.style.setProperty('--rx', '0deg'); el.style.setProperty('--ry', '0deg'); };
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerleave', leave);
    return () => { cancelAnimationFrame(raf); el.removeEventListener('pointermove', move); el.removeEventListener('pointerleave', leave); };
  }, [max]);
  return ref;
}

/** Buttons that lean towards the pointer. Desktop only. */
export function useMagnetic(strength = 0.28) {
  const ref = useRef(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || reducedMotion() || !window.matchMedia('(hover: hover) and (pointer: fine)').matches) return undefined;
    const move = (e) => {
      const r = el.getBoundingClientRect();
      el.style.transform = `translate(${((e.clientX - r.left - r.width / 2) * strength).toFixed(1)}px, ${((e.clientY - r.top - r.height / 2) * strength).toFixed(1)}px)`;
    };
    const leave = () => { el.style.transform = ''; };
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerleave', leave);
    return () => { el.removeEventListener('pointermove', move); el.removeEventListener('pointerleave', leave); };
  }, [strength]);
  return ref;
}

const visible = (el) => {
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < window.innerHeight && r.left < window.innerWidth;
};

/**
 * The product photo lifts off the page, arcs to the bag icon and shrinks into it.
 * Resolves when it lands (immediately when there is nothing to animate or motion is reduced),
 * so the caller can open the mini cart right after.
 */
export function flyToCart(productId) {
  if (reducedMotion() || typeof document.body.animate !== 'function') return Promise.resolve();
  const source = [...document.querySelectorAll(`[data-fly-src="${productId}"]`)].find(visible);
  const target = [...document.querySelectorAll('[data-cart-target]')].find(visible);
  if (!source || !target) return Promise.resolve();

  const from = source.getBoundingClientRect();
  const to = target.getBoundingClientRect();
  const ghost = document.createElement('img');
  ghost.src = source.currentSrc || source.src;
  ghost.alt = '';
  ghost.setAttribute('aria-hidden', 'true');
  const size = Math.min(from.width, 180);
  const h = size * (from.height / from.width || 1.25);
  Object.assign(ghost.style, {
    position: 'fixed', zIndex: 9999, left: 0, top: 0, width: `${size}px`, height: `${h}px`, objectFit: 'cover',
    borderRadius: '14px', pointerEvents: 'none', boxShadow: '0 18px 40px -12px rgb(0 0 0 / 0.5)', willChange: 'transform, opacity',
  });
  document.body.appendChild(ghost);

  const x0 = from.left + from.width / 2 - size / 2;
  const y0 = from.top + from.height / 2 - h / 2;
  const x1 = to.left + to.width / 2 - size / 2;
  const y1 = to.top + to.height / 2 - h / 2;
  const arcY = Math.min(y0, y1) - 70;
  const anim = ghost.animate([
    { transform: `translate(${x0}px, ${y0}px) scale(1) rotate(0deg)`, opacity: 1 },
    { transform: `translate(${x0 + (x1 - x0) * 0.5}px, ${arcY}px) scale(0.55) rotate(-8deg)`, opacity: 1, offset: 0.55 },
    { transform: `translate(${x1}px, ${y1}px) scale(0.06) rotate(10deg)`, opacity: 0.35 },
  ], { duration: 640, easing: 'cubic-bezier(0.5, 0, 0.2, 1)', fill: 'forwards' });
  return new Promise((resolve) => {
    const done = () => { ghost.remove(); resolve(); };
    anim.onfinish = done;
    anim.oncancel = done;
    setTimeout(done, 900); // never leave the page waiting on a stalled animation
  });
}

const COLOURS = ['#b5862f', '#e9cf95', '#7b1e2b', '#c95b6b', '#f4ede2', '#2e7d4f'];

/** A short burst of paper from the top of the screen. Used when an order is placed. */
export function confetti({ count = 90 } = {}) {
  if (reducedMotion() || typeof document.body.animate !== 'function') return;
  const layer = document.createElement('div');
  Object.assign(layer.style, { position: 'fixed', inset: 0, pointerEvents: 'none', zIndex: 9998, overflow: 'hidden' });
  layer.setAttribute('aria-hidden', 'true');
  document.body.appendChild(layer);
  for (let i = 0; i < count; i++) {
    const bit = document.createElement('span');
    const w = 6 + Math.random() * 7;
    const startX = window.innerWidth * (0.15 + Math.random() * 0.7);
    Object.assign(bit.style, {
      position: 'absolute', left: `${startX}px`, top: '-20px', width: `${w}px`, height: `${w * (Math.random() > 0.5 ? 1 : 0.45)}px`,
      background: COLOURS[i % COLOURS.length], borderRadius: Math.random() > 0.6 ? '50%' : '2px',
    });
    layer.appendChild(bit);
    const drift = (Math.random() - 0.5) * 360;
    bit.animate([
      { transform: 'translate(0, 0) rotate(0deg)', opacity: 1 },
      { transform: `translate(${drift}px, ${window.innerHeight * (0.55 + Math.random() * 0.5)}px) rotate(${(Math.random() - 0.5) * 1080}deg)`, opacity: 0.9, offset: 0.8 },
      { transform: `translate(${drift * 1.1}px, ${window.innerHeight + 40}px) rotate(1200deg)`, opacity: 0 },
    ], { duration: 1700 + Math.random() * 1500, delay: Math.random() * 400, easing: 'cubic-bezier(0.2, 0.6, 0.3, 1)', fill: 'forwards' });
  }
  setTimeout(() => layer.remove(), 4200);
}

/** Click ripple on every button, wired once for the whole app. Returns a cleanup function. */
export function installRipples() {
  if (reducedMotion()) return () => {};
  const onDown = (e) => {
    const btn = e.target.closest?.('.btn, .primary, .link-button, .chip, .occ-row button');
    if (!btn || btn.disabled) return;
    const r = btn.getBoundingClientRect();
    const dot = document.createElement('span');
    const d = Math.max(r.width, r.height) * 2;
    dot.className = 'ripple';
    dot.style.cssText = `width:${d}px;height:${d}px;left:${e.clientX - r.left - d / 2}px;top:${e.clientY - r.top - d / 2}px`;
    if (getComputedStyle(btn).position === 'static') btn.style.position = 'relative';
    btn.classList.add('has-ripple');
    btn.appendChild(dot);
    setTimeout(() => dot.remove(), 650);
  };
  document.addEventListener('pointerdown', onDown, { passive: true });
  return () => document.removeEventListener('pointerdown', onDown);
}

/**
 * Cursor glow: writes the pointer position (--mx/--my, in percent) onto the hovered card or hero so CSS can
 * paint a soft light that follows it. Mouse only, throttled to one write per frame, off for reduced motion.
 */
export function installSpotlight() {
  if (reducedMotion() || !window.matchMedia('(hover: hover)').matches) return () => {};
  let raf = 0;
  const onMove = (e) => {
    if (raf) return;
    raf = requestAnimationFrame(() => {
      raf = 0;
      const el = e.target.closest?.('.pcard, .tile, .hero-x, .voices-card, .reel, .refer-hero');
      if (!el) return;
      const r = el.getBoundingClientRect();
      el.style.setProperty('--mx', `${((e.clientX - r.left) / r.width) * 100}%`);
      el.style.setProperty('--my', `${((e.clientY - r.top) / r.height) * 100}%`);
    });
  };
  document.addEventListener('pointermove', onMove, { passive: true });
  return () => document.removeEventListener('pointermove', onMove);
}
