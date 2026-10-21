import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { BrandMonogram } from './BrandMark';
import { Lotus } from './Motion';
import { reducedMotion } from '../lib/motion';

const KEY = 'rsm_intro_seen';

function shouldShow(pathname) {
  if (pathname !== '/' || reducedMotion()) return false;
  try {
    // automated browsers (tests, crawlers) skip it unless a test asks for it
    if (navigator.webdriver && localStorage.getItem('rsm_intro_force') !== '1') return false;
    if (sessionStorage.getItem(KEY)) return false;
    sessionStorage.setItem(KEY, '1');
    return true;
  } catch { return false; }
}

/**
 * The landing logo: on a shopper's first visit to the home page, the RS monogram traces itself in gold on ivory, the
 * wordmark rises, and the curtain lifts onto the store (about 1.6 s). Tap, click or any key skips it. Once per visit,
 * never on other pages, never for people who ask their device for less motion.
 */
export function LandingIntro() {
  const { pathname } = useLocation();
  const [phase, setPhase] = useState(() => (shouldShow(pathname) ? 'on' : 'off'));

  useEffect(() => {
    if (phase !== 'on') return undefined;
    const lift = setTimeout(() => setPhase('lifting'), 1650);
    const skip = () => setPhase('lifting');
    window.addEventListener('keydown', skip, { once: true });
    return () => { clearTimeout(lift); window.removeEventListener('keydown', skip); };
  }, [phase]);

  useEffect(() => {
    if (phase !== 'lifting') return undefined;
    const t = setTimeout(() => setPhase('off'), 700);
    return () => clearTimeout(t);
  }, [phase]);

  if (phase === 'off') return null;
  return (
    <div className={`intro ${phase}`} aria-hidden="true" onClick={() => setPhase('lifting')}>
      <div className="intro-mark">
        <BrandMonogram draw />
        <span className="intro-name">Rekha</span>
        <span className="intro-sub">Shopping Mall</span>
        <Lotus />
      </div>
    </div>
  );
}
