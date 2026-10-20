import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { inrCompact, isOnDeal, activePrice, discountPercent, imageSrc } from '../lib/format';

/** Fades a section in once as it scrolls into view. Content stays visible if motion is reduced or JS is slow. */
export function Reveal({ children, className = '', as: Tag = 'div' }) {
  const ref = useRef(null);
  const [seen, setSeen] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === 'undefined' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setSeen(true);
      return undefined;
    }
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) { setSeen(true); io.disconnect(); } }, { rootMargin: '0px 0px -8% 0px' });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  return <Tag ref={ref} className={`reveal ${seen ? 'in' : ''} ${className}`}>{children}</Tag>;
}

/** Rotating hero showcase of current deals. Pauses on hover/focus and never autoplays under reduced motion. */
export function Spotlight({ products }) {
  const slides = products.slice(0, 4);
  const [i, setI] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduce = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  useEffect(() => {
    if (paused || reduce || slides.length < 2) return undefined;
    const t = setInterval(() => setI(n => (n + 1) % slides.length), 5200);
    return () => clearInterval(t);
  }, [paused, reduce, slides.length]);

  if (!slides.length) {
    return <img src="/images/sarees/hero-sarees.jpg" alt="A selection of folded handwoven silk sarees" width="900" height="1125" fetchPriority="high" />;
  }
  return (
    <div className="spot" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)} onBlur={() => setPaused(false)} role="group" aria-roledescription="carousel" aria-label="Featured sarees">
      {slides.map((p, n) => (
        <Link key={p.id} to={`/products/${p.id}`} className={`spot-slide ${n === i ? 'on' : ''}`}
          aria-hidden={n !== i} tabIndex={n === i ? 0 : -1} aria-label={`${p.name}, ${inrCompact(activePrice(p))}`}>
          <img src={imageSrc(p.imageUrl)} alt="" width="900" height="1125" loading={n === 0 ? 'eager' : 'lazy'} fetchPriority={n === 0 ? 'high' : 'auto'} />
          <span className="spot-cap">
            <small>{isOnDeal(p) ? `${discountPercent(p)}% off today` : p.category?.name}</small>
            <strong>{p.name}</strong>
            <span>{inrCompact(activePrice(p))}{isOnDeal(p) && <s>{inrCompact(p.price)}</s>} <b>Shop now →</b></span>
          </span>
        </Link>
      ))}
      <div className="spot-dots">
        {slides.map((p, n) => (
          <button key={p.id} type="button" className={n === i ? 'on' : ''} aria-label={`Show ${p.name}`} aria-current={n === i} onClick={() => setI(n)}></button>
        ))}
      </div>
    </div>
  );
}

/** Image tiles for each category, using a real product photo from that category. */
export function CategoryTiles({ categories, products, onPick }) {
  const tiles = categories.map(c => {
    const inCat = products.filter(p => p.category?.id === c.id);
    return { c, count: inCat.length, cover: inCat.find(p => p.stockQuantity > 0) || inCat[0] };
  }).filter(t => t.cover);
  if (tiles.length < 2) return null;
  return (
    <section className="tiles" aria-labelledby="tiles-title">
      <div className="tiles-head">
        <div><span className="eyebrow">Browse</span><h2 id="tiles-title">Shop by category</h2></div>
      </div>
      <ul className="tiles-grid">
        {tiles.map(({ c, count, cover }) => (
          <li key={c.id}>
            <button type="button" className="tile" onClick={() => onPick(String(c.id))}>
              <img src={imageSrc(cover.imageUrl)} alt="" loading="lazy" width="400" height="500" />
              <span className="tile-label"><strong>{c.name}</strong><small>{count} {count === 1 ? 'saree' : 'sarees'}</small></span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Occasion shortcuts, the way shoppers actually decide: "something for a wedding". */
export function OccasionStrip({ products, active, onPick }) {
  const occasions = [...new Set(products.map(p => p.occasion).filter(Boolean))].sort();
  if (occasions.length < 2) return null;
  return (
    <section className="occasions" aria-labelledby="occ-title">
      <h2 id="occ-title">Shop by occasion</h2>
      <div className="occ-row">
        {occasions.map(o => (
          <button key={o} type="button" aria-pressed={active === o} onClick={() => onPick(active === o ? '' : o)}>{o}</button>
        ))}
      </div>
    </section>
  );
}
