import React, { useEffect, useState } from 'react';
import { useStore } from '../contexts/StoreContext';
import { inrCompact } from '../lib/format';

/** Split-screen frame for sign-in, registration and password reset: brand panel + the form. */
export function AuthShell({ children }) {
  const { info } = useStore();
  return (
    <div className="auth-shell">
      <aside className="auth-art" aria-hidden="true">
        <img src="/images/sarees/hero-sarees.jpg" alt="" width="900" height="1125" />
        <div className="auth-art-copy">
          <span className="eyebrow">Rekha Shopping Mall</span>
          <p className="auth-quote">Handwoven drapes, chosen with care and delivered to your door.</p>
          <ul>
            {info && <li><i className="bi bi-truck"></i> Free shipping above {inrCompact(info.freeShippingThreshold)}</li>}
            <li><i className="bi bi-cash-coin"></i> Cash on delivery</li>
            <li><i className="bi bi-heart"></i> Save favourites and track orders</li>
          </ul>
        </div>
      </aside>
      <div className="auth-form">{children}</div>
    </div>
  );
}

/** Adds a "scrolled" class to the header for the compact state, and offers a way back to the top. */
export function ScrollChrome() {
  const [scrolled, setScrolled] = useState(false);
  const [far, setFar] = useState(false);
  useEffect(() => {
    const onScroll = () => {
      const y = window.scrollY;
      setScrolled(y > 24);
      setFar(y > 1200);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);
  useEffect(() => {
    document.documentElement.classList.toggle('is-scrolled', scrolled);
  }, [scrolled]);
  if (!far) return null;
  return (
    <button type="button" className="to-top" aria-label="Back to top"
      onClick={() => window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })}>
      <i className="bi bi-arrow-up" aria-hidden="true"></i>
    </button>
  );
}
