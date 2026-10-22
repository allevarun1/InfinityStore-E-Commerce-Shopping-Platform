import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useStore } from '../contexts/StoreContext';
import { EmptyState } from './EmptyState';
import { useCartActions } from '../lib/useCartActions';
import { Reveal, Spotlight, CategoryTiles, OccasionStrip, PromoTrio, StyleBand } from './HomeSections';
import { Rise, CountUp, Marquee, BannerScene, Lotus, ThreadDivider } from './Motion';
import { CraftStory, ReelsStrip } from './Videos';
import { Testimonials } from './Testimonials';
import { useTilt } from '../lib/motion';
import { ProductCard, ProductCardSkeleton } from './ProductCard';
import { QuickView } from './QuickView';
import { inrCompact, isOnDeal, activePrice, discountPercent } from '../lib/format';

const SORTS = {
  featured: { label: 'Featured', fn: (a, b) => (b.stockQuantity > 0) - (a.stockQuantity > 0) || isOnDeal(b) - isOnDeal(a) || a.id - b.id },
  new: { label: 'Newest first', fn: (a, b) => b.id - a.id },
  'price-asc': { label: 'Price: low to high', fn: (a, b) => activePrice(a) - activePrice(b) },
  'price-desc': { label: 'Price: high to low', fn: (a, b) => activePrice(b) - activePrice(a) },
  discount: { label: 'Biggest discount', fn: (a, b) => discountPercent(b) - discountPercent(a) },
  rated: { label: 'Top rated', fn: (a, b) => (b.averageRating || 0) - (a.averageRating || 0) || (b.reviewCount || 0) - (a.reviewCount || 0) },
  name: { label: 'Name A–Z', fn: (a, b) => a.name.localeCompare(b.name) },
};

const PRICE_BANDS = {
  u2: { label: 'Under ₹2,000', test: (v) => v < 2000 },
  '2to5': { label: '₹2,000 – ₹5,000', test: (v) => v >= 2000 && v <= 5000 },
  '5to10': { label: '₹5,000 – ₹10,000', test: (v) => v > 5000 && v <= 10000 },
  o10: { label: 'Above ₹10,000', test: (v) => v > 10000 },
};
const PAGE = 12;

export function Home() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [shown, setShown] = useState(PAGE);
  const [sheet, setSheet] = useState(false);
  const [quick, setQuick] = useState(null);

  const [params, setParams] = useSearchParams();
  const query = params.get('q') || '';
  const categoryId = params.get('category') || '';
  const view = params.get('view') || '';
  const fabric = params.get('fabric') || '';
  const occasion = params.get('occasion') || '';
  const sort = SORTS[params.get('sort')] ? params.get('sort') : 'featured';
  const price = PRICE_BANDS[params.get('price')] ? params.get('price') : '';

  const { isAdmin } = useAuth();
  const { info, videos } = useStore();
  const cartActions = useCartActions();
  const location = useLocation();
  const tiltRef = useTilt(6);
  // The banner's looping motion (photo push-in, light sweep, petals) pauses once it is scrolled away: measured on a
  // throttled phone, leaving it running cost about 15 ms of every scroll frame further down the page.
  const heroRef = useRef(null);
  const [heroOn, setHeroOn] = useState(true);
  useEffect(() => {
    const el = heroRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') return undefined;
    const io = new IntersectionObserver(([e]) => setHeroOn(e.isIntersecting));
    io.observe(el);
    return () => io.disconnect();
  }, []);

  // The free server sleeps when idle and can take up to a minute to wake; while it wakes it may answer with an error.
  // So: retry a few times before giving up, and tell the shopper what is happening instead of showing nothing.
  const [slow, setSlow] = useState(false);
  const load = useCallback(() => {
    setLoading(true);
    setFailed(false);
    setSlow(false);
    const slowTimer = setTimeout(() => setSlow(true), 3500);
    const attempt = (n) => Promise.all([ApiService.products(), ApiService.categories()])
      .catch((e) => (n < 4 ? new Promise(r => setTimeout(r, 4000)).then(() => attempt(n + 1)) : Promise.reject(e)));
    attempt(1)
      .then(([p, c]) => {
        setProducts(p);
        setCategories(c);
      })
      .catch(() => setFailed(true))
      .finally(() => { clearTimeout(slowTimer); setSlow(false); setLoading(false); });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    document.title = 'Rekha Shopping Mall — Handwoven Saree Collection';
  }, []);

  // Arriving at /#products: the sections above the grid (carousel, banners, films, occasions) appear only after the
  // sarees load and keep growing as their pictures and videos arrive, which pushed the grid off screen. Keep the grid
  // pinned to the top while that settles, and let go the moment the shopper scrolls, taps or presses a key.
  useEffect(() => {
    if (location.hash !== '#products' || loading) return undefined;
    const pin = () => document.getElementById('products')?.scrollIntoView({ block: 'start' });
    pin();
    if (typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(pin);
    ro.observe(document.body);
    const stop = () => { ro.disconnect(); clearTimeout(t); EVENTS.forEach(ev => window.removeEventListener(ev, stop)); };
    const EVENTS = ['wheel', 'touchstart', 'keydown', 'pointerdown'];
    EVENTS.forEach(ev => window.addEventListener(ev, stop, { passive: true }));
    const t = setTimeout(stop, 5000);
    return stop;
  }, [location, loading]);

  const visible = useMemo(() => {
    const term = query.toLowerCase().trim();
    return products
      .filter(p => !term || p.name.toLowerCase().includes(term) || p.description.toLowerCase().includes(term)
        || p.category?.name.toLowerCase().includes(term) || [p.fabric, p.weave, p.colour, p.occasion, p.region].some(v => v && v.toLowerCase().includes(term)))
      .filter(p => !fabric || p.fabric === fabric)
      .filter(p => !occasion || p.occasion === occasion)
      .filter(p => !categoryId || String(p.category?.id) === categoryId)
      .filter(p => view !== 'deals' || isOnDeal(p))
      .filter(p => !price || PRICE_BANDS[price].test(activePrice(p)))
      .sort(SORTS[sort].fn);
  }, [products, query, categoryId, view, sort, fabric, occasion, price]);

  // A new filter starts the list from the top again.
  useEffect(() => { setShown(PAGE); }, [query, categoryId, view, sort, fabric, occasion, price]);
  useEffect(() => {
    if (!sheet) return undefined;
    document.body.classList.add('no-scroll');
    const onKey = (e) => { if (e.key === 'Escape') setSheet(false); };
    document.addEventListener('keydown', onKey);
    return () => { document.body.classList.remove('no-scroll'); document.removeEventListener('keydown', onKey); };
  }, [sheet]);

  const options = (key) => [...new Set(products.map(p => p[key]).filter(Boolean))].sort();
  const fabrics = useMemo(() => options('fabric'), [products]); // eslint-disable-line react-hooks/exhaustive-deps
  const occasions = useMemo(() => options('occasion'), [products]); // eslint-disable-line react-hooks/exhaustive-deps

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
  };

  const addToCart = async (product) => {
    setBusyId(product.id);
    await cartActions.addToCart(product);
    setBusyId(null);
  };

  const heroProducts = useMemo(() => {
    const inStock = products.filter(p => p.stockQuantity > 0);
    const deals = inStock.filter(isOnDeal);
    return [...deals, ...inStock.filter(p => !isOnDeal(p))].slice(0, 4);
  }, [products]);

  const tickerItems = useMemo(() => {
    const names = categories.map(c => c.name.replace(/ Sarees$/i, ''));
    const lead = ['Handwoven by weavers', 'Cash on delivery'];
    if (info) lead.push(`Free shipping above ${inrCompact(info.freeShippingThreshold)}`);
    const seen = new Set();
    return [...lead, ...fabrics.slice(0, 4), ...names].filter(t => t && !seen.has(t.toLowerCase()) && seen.add(t.toLowerCase()));
  }, [categories, fabrics, info]);

  const pickAndScroll = (changes) => {
    update(changes);
    setTimeout(() => document.getElementById('products')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 60);
  };

  const activeFilters = [
    query && { key: 'q', label: `“${query}”` },
    categoryId && { key: 'category', label: categories.find(c => String(c.id) === categoryId)?.name || 'Category' },
    view === 'deals' && { key: 'view', label: 'Deals' },
    fabric && { key: 'fabric', label: fabric },
    occasion && { key: 'occasion', label: occasion },
    price && { key: 'price', label: PRICE_BANDS[price].label },
  ].filter(Boolean);
  const filterCount = activeFilters.filter(f => f.key !== 'q').length;

  const activeCategory = categories.find(c => String(c.id) === categoryId);
  const heading = query ? `Results for “${query}”` : view === 'deals' ? 'Current deals' : activeCategory ? activeCategory.name : 'The collection';
  const filtersActive = query || categoryId || view || fabric || occasion || price;

  return (
    <>
      <section ref={heroRef} className={`hero-x hero-banner full-bleed ${heroOn ? '' : 'is-off'}`} aria-labelledby="hero-title">
        <BannerScene />
        <div className="hero-inner">
          <div className="hb-copy">
            <span className="eyebrow hb-eyebrow"><Lotus />Handwoven saree destination</span>
            <h1 id="hero-title" className="hb-title">
              <span className="sr-only">Tradition meets trend.</span>
              <span className="hb-l1" aria-hidden="true"><span className="word" style={{ '--i': 0 }}>Tradition</span></span>
              <span className="hb-l2" aria-hidden="true">
                <span className="word" style={{ '--i': 1 }}>meets</span>
                <span className="word hb-script" style={{ '--i': 2 }}>Trend</span>
              </span>
            </h1>
            {categories.length > 0 && (
              <p className="hb-cats">
                {categories.slice(0, 4).map(c => (
                  <button key={c.id} type="button" onClick={() => pickAndScroll({ category: String(c.id), view: '', q: '' })}>
                    {c.name.replace(/ Sarees$/i, '')}
                  </button>
                ))}
              </p>
            )}
            <p className="hb-lede">
              Kanchipuram silks, Bengal handlooms, Kerala kasavu and designer drapes, sourced directly from weavers.
            </p>
            <div className="hero-ctas">
              <a className="btn btn-light" href="#products">Explore the collection</a>
              <button type="button" className="btn btn-ghost-light"
                onClick={() => { update({ view: 'deals', category: '', q: '' }); document.getElementById('products')?.scrollIntoView(); }}>
                View deals
              </button>
            </div>
            {!loading && products.length > 0 && (
              <div className="hero-stats">
                <div><strong><CountUp eager to={products.length} /></strong><small>Sarees</small></div>
                {fabrics.length > 1 && <div><strong><CountUp eager to={fabrics.length} /></strong><small>Fabrics</small></div>}
                <div><strong><CountUp eager to={categories.length} /></strong><small>Collections</small></div>
              </div>
            )}
          </div>
          <div className="hero-media" ref={tiltRef}>
            <span className="hb-pick">Today's pick</span>
            <Spotlight products={heroProducts} />
          </div>
        </div>
      </section>

      {info && (
        <section className="trust full-bleed" aria-label="Shopping with Rekha Shopping Mall">
          <Rise className="trust-item">
            <i className="bi bi-truck" aria-hidden="true"></i>
            <div><strong>Free shipping above {inrCompact(info.freeShippingThreshold)}</strong><span>Otherwise a flat {inrCompact(info.shippingFee)}</span></div>
          </Rise>
          <Rise className="trust-item" delay={110}>
            <i className="bi bi-cash-coin" aria-hidden="true"></i>
            <div><strong>Cash on delivery</strong><span>Pay when your order arrives</span></div>
          </Rise>
          <Rise className="trust-item" delay={220}>
            <i className="bi bi-receipt" aria-hidden="true"></i>
            <div><strong>Clear, upfront pricing</strong><span>Full GST and shipping breakdown before you order</span></div>
          </Rise>
        </section>
      )}

      <Marquee items={tickerItems} />

      {!loading && !failed && (
        <>
          <Reveal>
            <CategoryTiles categories={categories} products={products}
              onPick={(id) => pickAndScroll({ category: id, view: '', q: '' })} />
          </Reveal>
          <PromoTrio products={products} onPick={(changes) => pickAndScroll({ q: '', ...changes })} />
          <ThreadDivider />
          <CraftStory videos={videos} />
          <ReelsStrip videos={videos} />
          <Reveal>
            <OccasionStrip products={products} active={occasion}
              onPick={(o) => pickAndScroll({ occasion: o })} />
          </Reveal>
        </>
      )}

      <section id="products" className="catalog full-bleed" aria-labelledby="catalog-title">
        <div className="catalog-head">
          <div>
            <span className="eyebrow">Shop</span>
            <h2 id="catalog-title">{heading}</h2>
            {!loading && !failed && (
              <p aria-live="polite">{visible.length} {visible.length === 1 ? 'saree' : 'sarees'}</p>
            )}
          </div>
          {query && (
            <span className="search-pill">
              Searching “{query}”
              <button type="button" aria-label="Clear search" onClick={() => update({ q: '' })}>
                <i className="bi bi-x-lg" aria-hidden="true"></i>
              </button>
            </span>
          )}
        </div>

        <div className="mobile-tools">
          <button type="button" className="btn btn-outline" onClick={() => setSheet(true)} aria-haspopup="dialog">
            <i className="bi bi-sliders" aria-hidden="true"></i> Filters{filterCount > 0 && <b className="count">{filterCount}</b>}
          </button>
          <label className="sort-control">
            <span className="sr-only">Sort</span>
            <select value={sort} onChange={e => update({ sort: e.target.value === 'featured' ? '' : e.target.value })} aria-label="Sort">
              {Object.entries(SORTS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>
          </label>
        </div>

        {sheet && <div className="drawer-backdrop sheet-backdrop" onClick={() => setSheet(false)} aria-hidden="true"></div>}
        <div className={`catalog-controls ${sheet ? 'open' : ''}`} role={sheet ? 'dialog' : undefined} aria-modal={sheet || undefined} aria-label={sheet ? 'Filters' : undefined}>
          <div className="sheet-head">
            <h2>Filters</h2>
            <button type="button" className="drawer-close" onClick={() => setSheet(false)} aria-label="Close filters"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
          </div>
          <div className="chips" role="group" aria-label="Filter by category">
            <button type="button" className="chip" aria-pressed={!categoryId && view !== 'deals'}
              onClick={() => update({ category: '', view: '' })}>All</button>
            {categories.map(c => (
              <button key={c.id} type="button" className="chip" aria-pressed={categoryId === String(c.id)}
                onClick={() => update({ category: String(c.id), view: '' })}>{c.name}</button>
            ))}
            <button type="button" className="chip chip-deal" aria-pressed={view === 'deals'}
              onClick={() => update({ view: view === 'deals' ? '' : 'deals', category: '' })}>
              <i className="bi bi-tag" aria-hidden="true"></i> Deals
            </button>
          </div>
          <div className="chips price-chips" role="group" aria-label="Filter by price">
            {Object.entries(PRICE_BANDS).map(([k, b]) => (
              <button key={k} type="button" className="chip" aria-pressed={price === k}
                onClick={() => update({ price: price === k ? '' : k })}>{b.label}</button>
            ))}
          </div>
          <div className="filter-selects">
            {fabrics.length > 0 && (
              <label className="sort-control">
                Fabric
                <select value={fabric} onChange={e => update({ fabric: e.target.value })}>
                  <option value="">All fabrics</option>
                  {fabrics.map(f => <option key={f} value={f}>{f}</option>)}
                </select>
              </label>
            )}
            {occasions.length > 0 && (
              <label className="sort-control">
                Occasion
                <select value={occasion} onChange={e => update({ occasion: e.target.value })}>
                  <option value="">Any occasion</option>
                  {occasions.map(o => <option key={o} value={o}>{o}</option>)}
                </select>
              </label>
            )}
          <label className="sort-control">
            Sort
            <select value={sort} onChange={e => update({ sort: e.target.value === 'featured' ? '' : e.target.value })}>
              {Object.entries(SORTS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>
          </label>
          </div>
          <div className="sheet-foot">
            <button type="button" className="text-link" onClick={() => { setParams({}, { replace: true }); }}>Clear all</button>
            <button type="button" className="btn btn-primary" onClick={() => setSheet(false)}>Show {visible.length} {visible.length === 1 ? 'saree' : 'sarees'}</button>
          </div>
        </div>

        {activeFilters.length > 0 && (
          <div className="active-filters" aria-label="Active filters">
            {activeFilters.map(f => (
              <button key={f.key} type="button" onClick={() => update({ [f.key]: '' })} aria-label={`Remove filter ${f.label}`}>
                {f.label} <i className="bi bi-x" aria-hidden="true"></i>
              </button>
            ))}
            <button type="button" className="text-link" onClick={() => setParams({}, { replace: true })}>Clear all</button>
          </div>
        )}

        {failed ? (
          <div className="notice" role="alert">
            <span>We couldn’t load the collection. Check your connection and try again.</span>
            <button type="button" className="btn btn-outline" onClick={load}>Retry</button>
          </div>
        ) : loading ? (
          <>
          {slow && (
            <p className="waking" role="status">
              <span className="waking-dot" aria-hidden="true"></span>
              Opening the store for you. The first visit after a quiet spell can take up to a minute.
            </p>
          )}
          <div className="pgrid" aria-busy="true" aria-label="Loading products">
            {Array.from({ length: 8 }, (_, i) => <ProductCardSkeleton key={i} />)}
          </div>
          </>
        ) : visible.length === 0 ? (
          <EmptyState
            icon="bi-search"
            title="Nothing matches yet"
            message={filtersActive ? 'Try another category or a different search.' : 'New sarees are on their way.'}
            actionText={filtersActive ? 'Show all sarees' : null}
            onAction={() => setParams({}, { replace: true })}
          />
        ) : (
          <div className="pgrid">
            {visible.slice(0, shown).map((p, i) => (
              <ProductCard
                key={p.id}
                priority={i < 4}
                index={i}
                product={p}
                mode={isAdmin() ? 'admin' : 'shop'}
                busy={busyId === p.id}
                onAdd={addToCart}
                onQuickView={setQuick}
              />
            ))}
          </div>
        )}
        {!loading && !failed && visible.length > shown && (
          <div className="load-more">
            <p>Showing {shown} of {visible.length}</p>
            <div className="bar" aria-hidden="true"><span style={{ width: `${(shown / visible.length) * 100}%` }}></span></div>
            <button type="button" className="btn btn-outline" onClick={() => setShown(n => n + PAGE)}>Show more sarees</button>
          </div>
        )}
        {quick && <QuickView product={quick} onClose={() => setQuick(null)} />}
      </section>
      {!loading && !failed && <Testimonials />}
      <StyleBand />
    </>
  );
}
