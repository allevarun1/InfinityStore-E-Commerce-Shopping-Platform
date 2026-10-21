import React, { useId } from 'react';
import { R, S, R_AT, S_AT, TRIM, ARCH, ARCH_IN, DIAMOND, MONO } from './brandPaths';

/**
 * The Rekha Shopping Mall emblem: an upright R whose leg sweeps across an italic S, in a jharokha arch.
 * Drawn inline so it is sharp on every screen and can animate: the letters trace themselves in gold once on the first
 * visit, and a sheen of light crosses the arch now and then (a transform-only overlay, so it never slows scrolling).
 */
export function BrandMark({ className = '', draw = false }) {
  const uid = useId().replace(/:/g, '');
  const g = `url(#g${uid})`;
  return (
    <span className={`rs-mark ${draw ? 'draw' : ''} ${className}`} aria-hidden="true">
      <svg viewBox="0 0 100 134" focusable="false">
        <defs>
          <linearGradient id={`g${uid}`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#f7e3b0" /><stop offset=".45" stopColor="#e2bf74" /><stop offset=".7" stopColor="#c99a45" /><stop offset="1" stopColor="#f1d898" />
          </linearGradient>
          <linearGradient id={`m${uid}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#8e2433" /><stop offset="1" stopColor="#4f0f19" /></linearGradient>
          <mask id={`t${uid}`} maskUnits="userSpaceOnUse" x="0" y="0" width="140" height="140">
            <rect width="140" height="140" fill="#fff" /><rect x={TRIM[0]} y={TRIM[1]} width={TRIM[2]} height={TRIM[3]} fill="#000" />
          </mask>
        </defs>
        <path d={ARCH} fill={`url(#m${uid})`} />
        <path className="rs-zari" d={ARCH_IN} fill="none" stroke={g} strokeWidth="1.6" pathLength="1" />
        <path className="rs-bindu" d={DIAMOND} fill={g} />
        <g transform={MONO}>
          <g mask={`url(#t${uid})`}>
            <path className="rs-gap" transform={S_AT} d={S} fill="none" stroke="#5e1520" strokeWidth="30" strokeLinejoin="round" />
            <path className="rs-s" transform={S_AT} d={S} fill="#fbf3e2" stroke="#fbf3e2" strokeWidth="12" pathLength="1" />
          </g>
          <path className="rs-gap" transform={R_AT} d={R} fill="none" stroke="#5e1520" strokeWidth="32.5" strokeLinejoin="round" />
          <path className="rs-r" transform={R_AT} d={R} fill={g} stroke={g} strokeWidth="14" pathLength="1" />
        </g>
        <path d="M33 117 H45 M55 117 H67" stroke={g} strokeWidth="1.1" />
        <path d="M50 114.6 L52.4 117 L50 119.4 L47.6 117 Z" fill={g} />
      </svg>
      <span className="rs-sheen"></span>
    </span>
  );
}

const SIGNATURE = {
  light: { r: ['#8a5a1c', '#c99a45', '#e7c77e', '#9b6a22'], s: ['#c2185b', '#9d1149', '#6e0d33'] },
  dark: { r: ['#f7e3b0', '#e2bf74', '#c99a45', '#f1d898'], s: ['#fff4dc', '#f4cfd9', '#f0a8c0'] },
};

/**
 * The free-standing RS monogram (no arch), for the header and footer: a gold R whose leg sweeps across a rani-pink S.
 * `tone` picks colours for light or dark backgrounds.
 */
export function BrandMonogram({ tone = 'light', draw = false }) {
  const uid = useId().replace(/:/g, '');
  const c = SIGNATURE[tone] || SIGNATURE.light;
  const stops = (list) => list.map((col, n) => <stop key={n} offset={n / (list.length - 1)} stopColor={col} />);
  return (
    <span className={`rs-mono ${draw ? 'draw' : ''}`} aria-hidden="true">
      <svg viewBox="14 33 84 66" focusable="false">
        <defs>
          <linearGradient id={`r${uid}`} x1="0" y1="0" x2="1" y2="1">{stops(c.r)}</linearGradient>
          <linearGradient id={`s${uid}`} x1="0" y1="0" x2="0.6" y2="1">{stops(c.s)}</linearGradient>
          <mask id={`t${uid}`} maskUnits="userSpaceOnUse" x="0" y="0" width="140" height="140">
            <rect width="140" height="140" fill="#fff" /><rect x={TRIM[0]} y={TRIM[1]} width={TRIM[2]} height={TRIM[3]} fill="#000" />
            {/* the gap where the R crosses is cut out of the S, so the mark works on any background */}
            <path transform={R_AT} d={R} fill="#000" stroke="#000" strokeWidth="34" strokeLinejoin="round" />
          </mask>
        </defs>
        <g mask={`url(#t${uid})`}>
          <path className="rs-s" transform={S_AT} d={S} fill={`url(#s${uid})`} stroke={`url(#s${uid})`} strokeWidth="12" pathLength="1" />
        </g>
        <path className="rs-r" transform={R_AT} d={R} fill={`url(#r${uid})`} stroke={`url(#r${uid})`} strokeWidth="14" pathLength="1" />
      </svg>
    </span>
  );
}

/** The signature lockup from the brand boards: monogram, REKHA in capitals, and "— SHOPPING MALL —" beneath. */
export function BrandSignature({ tone = 'light', draw = false }) {
  return (
    <span className={`rs-signature rs-${tone}`}>
      <BrandMonogram tone={tone} draw={draw} />
      <span className="rs-words">
        <span className="rs-name">Rekha</span>
        <span className="rs-sub">Shopping Mall</span>
      </span>
    </span>
  );
}

/** Emblem plus wordmark. `tone="dark"` for dark backgrounds such as the footer. */
export function BrandLockup({ tone = 'light', draw = false, sub = 'Shopping Mall' }) {
  return (
    <span className={`rs-lockup rs-${tone}`}>
      <BrandMark draw={draw} />
      <span className="rs-words">
        <span className="rs-name">Rekha</span>
        <span className="rs-sub">{sub}</span>
      </span>
    </span>
  );
}
