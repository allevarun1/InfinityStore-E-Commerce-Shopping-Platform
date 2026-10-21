import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Rise } from './Motion';
import { inrCompact, isOnDeal, activePrice, discountPercent, imageSet } from '../lib/format';

const HERO_SIZES = '(min-width: 900px) 440px, 92vw';
const TILE_SIZES = '(min-width: 1100px) 280px, 50vw';

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
    return <img {...imageSet("/images/sarees/hero-sarees.jpg", HERO_SIZES)} alt="A selection of folded handwoven silk sarees" width="900" height="1125" fetchPriority="high" />;
  }
  return (
    <div className="spot" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)} onBlur={() => setPaused(false)} role="group" aria-roledescription="carousel" aria-label="Featured sarees">
      {slides.map((p, n) => (
        <Link key={p.id} to={`/products/${p.id}`} className={`spot-slide ${n === i ? 'on' : ''}`}
          aria-hidden={n !== i} tabIndex={n === i ? 0 : -1} aria-label={`${p.name}, ${inrCompact(activePrice(p))}`}>
          <img {...imageSet(p.imageUrl, HERO_SIZES)} alt="" width="900" height="1125" loading={n === 0 ? 'eager' : 'lazy'} fetchPriority={n === 0 ? 'high' : 'auto'} />
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

const TAGLINES = [
  [/silk/i, 'Grace in every drape'], [/cotton|handloom/i, 'Easy elegance, every day'], [/designer/i, 'Statement pieces'],
  [/bridal|wedding/i, 'For the day you will remember'], [/kasavu|kerala/i, 'Gold on ivory'], [/banarasi/i, 'Woven in Varanasi'],
];
const tagline = (name) => TAGLINES.find(([re]) => re.test(name))?.[1] || 'Handpicked from weavers';

/** A section heading in the brand-board style: a script word above a spaced capital title. */
export function ScriptHead({ id, script, title, sub }) {
  return (
    <div className="sh">
      <h2 id={id}><span className="sh-script">{script}</span> <span className="sh-title">{title}</span></h2>
      {sub && <p>{sub}</p>}
    </div>
  );
}

/** Horizontal carousel: swipe on phones, arrows on larger screens, snaps to each card. */
function useCarousel() {
  const ref = useRef(null);
  const [ends, setEnds] = useState({ start: true, end: false });
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    let raf = 0;
    const check = () => { raf = 0; setEnds({ start: el.scrollLeft < 8, end: el.scrollLeft + el.clientWidth > el.scrollWidth - 8 }); };
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(check); };
    check();
    el.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => { el.removeEventListener('scroll', onScroll); window.removeEventListener('resize', onScroll); cancelAnimationFrame(raf); };
  }, []);
  const go = (dir) => {
    const el = ref.current;
    const card = el?.firstElementChild;
    if (el && card) el.scrollBy({ left: dir * (card.getBoundingClientRect().width + 18) * (el.clientWidth > 700 ? 2 : 1), behavior: 'smooth' });
  };
  return { ref, ends, go };
}

function Arrows({ ends, go, label }) {
  if (ends.start && ends.end) return null;
  return (
    <div className="car-arrows">
      <button type="button" onClick={() => go(-1)} disabled={ends.start} aria-label={`Previous ${label}`}><i className="bi bi-chevron-left" aria-hidden="true"></i></button>
      <button type="button" onClick={() => go(1)} disabled={ends.end} aria-label={`More ${label}`}><i className="bi bi-chevron-right" aria-hidden="true"></i></button>
    </div>
  );
}

/** Category cards in a carousel, each with a real product photo from that category. */
export function CategoryTiles({ categories, products, onPick }) {
  const car = useCarousel();
  const tiles = categories.map(c => {
    const inCat = products.filter(p => p.category?.id === c.id);
    return { c, count: inCat.length, cover: inCat.find(p => p.stockQuantity > 0) || inCat[0] };
  }).filter(t => t.cover);
  if (tiles.length < 2) return null;
  return (
    <section className="tiles" aria-labelledby="tiles-title">
      <div className="tiles-head">
        <ScriptHead id="tiles-title" script="Shop by" title="Category" />
        <Arrows ends={car.ends} go={car.go} label="categories" />
      </div>
      <ul className="tiles-grid car-track" ref={car.ref}>
        {tiles.map(({ c, count, cover }, n) => (
          <Rise as="li" key={c.id} delay={Math.min(n, 4) * 100}>
            <button type="button" className="tile" onClick={() => onPick(String(c.id))}>
              <img {...imageSet(cover.imageUrl, TILE_SIZES)} alt="" loading="lazy" width="400" height="500" />
              <span className="tile-label">
                <strong>{c.name.replace(/ Sarees$/i, '')}</strong>
                <em>{tagline(c.name)}</em>
                <small>{count} {count === 1 ? 'saree' : 'sarees'}</small>
                <b>Shop now</b>
              </span>
            </button>
          </Rise>
        ))}
      </ul>
    </section>
  );
}

/** Three editorial banners: new arrivals, the festive edit and current deals, each built from real products. */
export function PromoTrio({ products, onPick }) {
  const inStock = products.filter(p => p.stockQuantity > 0 && p.imageUrl);
  const newest = [...inStock].sort((a, b) => b.id - a.id)[0];
  const festive = inStock.find(p => p !== newest && /festive/i.test(p.occasion || '')) || inStock.find(p => p !== newest && /silk/i.test(p.category?.name || ''));
  const deals = inStock.filter(p => isOnDeal(p) && p !== newest && p !== festive);
  const best = deals.reduce((m, p) => Math.max(m, discountPercent(p)), 0);
  const cards = [
    newest && { key: 'new', tone: 'wine', eyebrow: 'New arrivals', title: 'Fresh drapes, just in', cta: 'Shop new', p: newest,
      pick: { sort: 'new', view: '', category: '', occasion: '' } },
    festive && { key: 'festive', tone: 'gold', eyebrow: 'Festive edit', title: 'Celebrate in silk', cta: 'Explore', p: festive,
      pick: /festive/i.test(festive.occasion || '') ? { occasion: festive.occasion, view: '', category: '' } : { category: String(festive.category.id), view: '' } },
    deals.length > 0 && { key: 'deals', tone: 'rani', eyebrow: 'Deals of the day', title: best ? `Up to ${best}% off` : 'Popular picks', cta: 'Shop deals',
      p: deals.find(p => discountPercent(p) === best) || deals[0], pick: { view: 'deals', category: '', occasion: '' } },
  ].filter(Boolean);
  if (cards.length < 2) return null;
  return (
    <section className="trio" aria-label="Collections">
      {cards.map((c, n) => (
        <Rise key={c.key} delay={n * 120} className={`trio-card trio-${c.tone}`}>
          <img {...imageSet(c.p.imageUrl, '(min-width: 900px) 30vw, 90vw')} alt="" loading="lazy" width="600" height="750" />
          <div className="trio-copy">
            <span>{c.eyebrow}</span>
            <strong>{c.title}</strong>
            <button type="button" onClick={() => onPick(c.pick)}>{c.cta} <i className="bi bi-arrow-right" aria-hidden="true"></i></button>
          </div>
        </Rise>
      ))}
    </section>
  );
}

/** Occasion medallions, the way shoppers actually decide: "something for a wedding". */
export function OccasionStrip({ products, active, onPick }) {
  const occasions = [...new Set(products.map(p => p.occasion).filter(Boolean))].sort();
  if (occasions.length < 2) return null;
  const cover = (o) => products.find(p => p.occasion === o && p.stockQuantity > 0) || products.find(p => p.occasion === o);
  return (
    <section className="occasions" aria-labelledby="occ-title">
      <ScriptHead id="occ-title" script="Shop by" title="Occasion" sub="Find the perfect drape for every moment" />
      <div className="occ-row">
        {occasions.map(o => {
          const c = cover(o);
          return (
            <button key={o} type="button" aria-pressed={active === o} onClick={() => onPick(active === o ? '' : o)}>
              <span className="occ-pic">{c?.imageUrl && <img {...imageSet(c.imageUrl, '120px')} alt="" loading="lazy" width="120" height="120" />}</span>
              <span className="occ-name">{o}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

/** Closing band: why shop here, and one last way back to the collection. */
export function StyleBand() {
  return (
    <section className="band full-bleed" aria-labelledby="band-title">
      <div className="band-inner">
        <h2 id="band-title"><span className="sh-script">Your style,</span> our priority</h2>
        <ul>
          <li><i className="bi bi-gem" aria-hidden="true"></i>Handpicked from weavers</li>
          <li><i className="bi bi-stars" aria-hidden="true"></i>New drapes every week</li>
          <li><i className="bi bi-flower1" aria-hidden="true"></i>Traditional and modern</li>
        </ul>
        <a className="band-cta" href="#products">Shop now <i className="bi bi-arrow-right" aria-hidden="true"></i></a>
      </div>
    </section>
  );
}

