import React, { useEffect, useRef } from 'react';
import { useCountUp, useInView, reducedMotion } from '../lib/motion';

/** Slides its content up and in the first time it scrolls into view. `delay` (ms) staggers siblings. */
export function Rise({ as: Tag = 'div', delay = 0, className = '', children, ...rest }) {
  const [ref, seen] = useInView();
  return (
    <Tag ref={ref} className={`rise ${seen ? 'in' : ''} ${className}`} style={{ '--d': `${delay}ms` }} {...rest}>
      {children}
    </Tag>
  );
}

/**
 * A headline that arrives one word at a time. Screen readers get the whole sentence once;
 * `accent` words are wrapped in <em> for the gold italic.
 */
export function SplitWords({ text, accent = [], base = 0 }) {
  const words = text.split(' ');
  return (
    <>
      <span className="sr-only">{text}</span>
      <span aria-hidden="true">
        {words.map((w, i) => (
          <React.Fragment key={i}>
            <span className="word" style={{ '--i': base + i }}>
              {accent.includes(w.replace(/[^\w]/g, '')) ? <em>{w}</em> : w}
            </span>{' '}
          </React.Fragment>
        ))}
      </span>
    </>
  );
}

/** A number that counts up when it scrolls into view. */
export function CountUp({ to, suffix = '', eager = false }) {
  const [ref, value] = useCountUp(to, { eager });
  return <span ref={ref} aria-label={`${to}${suffix}`}>{value}{suffix}</span>;
}

/** Slow horizontal ticker. The repeated half is hidden from assistive tech; pauses on hover. */
export function Marquee({ items }) {
  if (!items.length) return null;
  const row = (hidden) => (
    <ul aria-hidden={hidden || undefined}>
      {items.map((t, i) => <li key={i}><i className="bi bi-stars" aria-hidden="true"></i>{t}</li>)}
    </ul>
  );
  return (
    <div className="marquee full-bleed" role="presentation">
      <div className="marquee-track">{row(false)}{row(true)}</div>
    </div>
  );
}

/** Gold reading-progress line along the top edge. Updates a CSS variable, never re-renders. */
export function ScrollProgress() {
  const bar = useRef(null);
  useEffect(() => {
    if (reducedMotion()) return undefined;
    let raf = 0;
    const update = () => {
      raf = 0;
      const max = document.documentElement.scrollHeight - window.innerHeight;
      bar.current?.style.setProperty('--p', max > 0 ? Math.min(1, window.scrollY / max).toFixed(4) : '0');
    };
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(update); };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => { window.removeEventListener('scroll', onScroll); window.removeEventListener('resize', onScroll); cancelAnimationFrame(raf); };
  }, []);
  return <div className="scroll-progress" ref={bar} aria-hidden="true"></div>;
}

const PETALS = [
  { x: '8%', d: '-2s', t: '13s', s: 1, dx: '60px', r: '220deg' }, { x: '24%', d: '-9s', t: '16s', s: 0.7, dx: '-40px', r: '-160deg' },
  { x: '41%', d: '-5s', t: '14s', s: 0.85, dx: '70px', r: '260deg' }, { x: '58%', d: '-12s', t: '17s', s: 1.15, dx: '-60px', r: '-210deg' },
  { x: '72%', d: '-3s', t: '12s', s: 0.75, dx: '50px', r: '190deg' }, { x: '86%', d: '-7s', t: '15s', s: 0.95, dx: '-50px', r: '-240deg' },
];

/**
 * The banner's scenery: warm light, a colonnade of Mughal arches drawn in fine gold, the saree photograph fading in from
 * the right with a slow push-in, a sweep of light, and a few rose petals drifting down. Decorative only; every moving
 * part animates transform or opacity, so it costs the scroll nothing.
 */
export function BannerScene() {
  return (
    <div className="hb-scene" aria-hidden="true">
      <span className="hb-glow"></span>
      <svg className="hb-arches" viewBox="0 0 640 420" preserveAspectRatio="xMinYMax slice" focusable="false">
        {[0, 160, 320].map(x => (
          <g key={x} transform={`translate(${x} 0)`}>
            <path d="M14 420 V190 C14 140 52 118 70 92 C76 84 78 76 80 64 C82 76 84 84 90 92 C108 118 146 140 146 190 V420" />
            <path d="M28 420 V196 C28 154 60 136 74 112 C77 106 79 100 80 92 C81 100 83 106 86 112 C100 136 132 154 132 196 V420" />
          </g>
        ))}
      </svg>
      <picture className="hb-photo">
        <source media="(max-width: 760px)" srcSet="/images/banner/rekha-banner-m.webp" type="image/webp" />
        <source srcSet="/images/banner/rekha-banner-900.webp 900w, /images/banner/rekha-banner-1400.webp 1400w, /images/banner/rekha-banner-1800.webp 1800w"
          sizes="66vw" type="image/webp" />
        <img src="/images/banner/rekha-banner.jpg" alt="" width="1802" height="800" fetchPriority="high" decoding="async" />
      </picture>
      <span className="hb-sweep"></span>
      {PETALS.map((p, i) => (
        <i key={i} className="hb-petal" style={{ '--x': p.x, '--d': p.d, '--t': p.t, '--s': p.s, '--dx': p.dx, '--r': p.r }}></i>
      ))}
    </div>
  );
}

/** A small lotus drawn in gold, used as an ornament. */
export function Lotus() {
  return (
    <svg className="lotus" viewBox="0 0 32 20" aria-hidden="true" focusable="false">
      <path d="M16 2 C19 6 19 11 16 16 C13 11 13 6 16 2 Z M16 16 C11 15 7 11 6 6 C10 7 14 10 16 16 Z M16 16 C21 15 25 11 26 6 C22 7 18 10 16 16 Z M16 16 C10 17 5 15 2 11 C6 11 11 13 16 16 Z M16 16 C22 17 27 15 30 11 C26 11 21 13 16 16 Z" />
    </svg>
  );
}

/** A gold and maroon thread that draws itself across the page between sections. */
export function ThreadDivider() {
  const [ref, seen] = useInView();
  return (
    <svg ref={ref} className={`thread ${seen ? 'in' : ''}`} viewBox="0 0 1000 28" preserveAspectRatio="none" aria-hidden="true">
      <path pathLength="1" d="M0 14 C 120 0, 200 28, 330 14 S 560 0, 680 14 S 900 28, 1000 14" />
      <path pathLength="1" d="M0 14 C 140 28, 230 0, 350 14 S 580 28, 700 14 S 910 0, 1000 14" />
    </svg>
  );
}
