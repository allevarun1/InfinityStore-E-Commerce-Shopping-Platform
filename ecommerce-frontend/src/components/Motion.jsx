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

/** Drifting gold dust and soft glows behind the hero. Pure CSS, decorative. */
export function HeroDust() {
  return (
    <div className="hero-dust" aria-hidden="true">
      <span className="orb o1"></span><span className="orb o2"></span>
      {Array.from({ length: 16 }, (_, i) => (
        <i key={i} style={{ '--x': `${(i * 37) % 100}%`, '--s': `${3 + (i % 4) * 2}px`, '--t': `${9 + (i % 5) * 3}s`, '--w': `${-(i * 1.7)}s` }}></i>
      ))}
    </div>
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
