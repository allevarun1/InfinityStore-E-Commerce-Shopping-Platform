import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { useStore } from '../contexts/StoreContext';
import { EmptyState } from './EmptyState';
import { ProductCard, ProductCardSkeleton } from './ProductCard';
import { inrCompact, isOnDeal, activePrice, discountPercent } from '../lib/format';

const SORTS = {
  featured: { label: 'Featured', fn: (a, b) => (b.stockQuantity > 0) - (a.stockQuantity > 0) || isOnDeal(b) - isOnDeal(a) || a.id - b.id },
  'price-asc': { label: 'Price: low to high', fn: (a, b) => activePrice(a) - activePrice(b) },
  'price-desc': { label: 'Price: high to low', fn: (a, b) => activePrice(b) - activePrice(a) },
  discount: { label: 'Biggest discount', fn: (a, b) => discountPercent(b) - discountPercent(a) },
  rated: { label: 'Top rated', fn: (a, b) => (b.averageRating || 0) - (a.averageRating || 0) || (b.reviewCount || 0) - (a.reviewCount || 0) },
  name: { label: 'Name A–Z', fn: (a, b) => a.name.localeCompare(b.name) },
};

export function Home() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [busyId, setBusyId] = useState(null);

  const [params, setParams] = useSearchParams();
  const query = params.get('q') || '';
  const categoryId = params.get('category') || '';
  const view = params.get('view') || '';
  const fabric = params.get('fabric') || '';
  const occasion = params.get('occasion') || '';
  const sort = SORTS[params.get('sort')] ? params.get('sort') : 'featured';

  const { isAdmin, isLoggedIn } = useAuth();
  const { info, refreshCart } = useStore();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const load = useCallback(() => {
    setLoading(true);
    setFailed(false);
    Promise.all([ApiService.products(), ApiService.categories()])
      .then(([p, c]) => {
        setProducts(p);
        setCategories(c);
      })
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    document.title = 'InfinityStore — Handwoven Saree Collection';
  }, []);

  useEffect(() => {
    if (location.hash === '#products' && !loading) {
      document.getElementById('products')?.scrollIntoView({ block: 'start' });
    }
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
      .sort(SORTS[sort].fn);
  }, [products, query, categoryId, view, sort, fabric, occasion]);

  const options = (key) => [...new Set(products.map(p => p[key]).filter(Boolean))].sort();
  const fabrics = useMemo(() => options('fabric'), [products]); // eslint-disable-line react-hooks/exhaustive-deps
  const occasions = useMemo(() => options('occasion'), [products]); // eslint-disable-line react-hooks/exhaustive-deps

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
  };

  const requireCustomer = () => {
    if (!isLoggedIn()) {
      toast.info('Sign in to continue');
      navigate('/login', { state: { from: location } });
      return false;
    }
    return true;
  };

  const addToCart = (product) => {
    if (!requireCustomer()) return;
    setBusyId(product.id);
    ApiService.addToCart(product.id)
      .then(() => {
        toast.success(`${product.name} added to your cart`);
        refreshCart();
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not add this item'))
      .finally(() => setBusyId(null));
  };

  const addToWishlist = (product) => {
    if (!requireCustomer()) return;
    ApiService.addWishlist(product.id)
      .then(() => toast.success('Saved to your wishlist'))
      .catch(err => toast.error(err.response?.data?.message || 'Could not save to wishlist'));
  };

  const activeCategory = categories.find(c => String(c.id) === categoryId);
  const heading = query ? `Results for “${query}”` : view === 'deals' ? 'Current deals' : activeCategory ? activeCategory.name : 'The collection';
  const filtersActive = query || categoryId || view || fabric || occasion;

  return (
    <>
      <section className="hero-x full-bleed" aria-labelledby="hero-title">
        <div className="hero-inner">
          <div>
            <span className="eyebrow">Handwoven saree collection</span>
            <h1 id="hero-title">Drapes woven to be <em>treasured</em> for years.</h1>
            <p>
              Explore Kanchipuram silks, Bengal handlooms, Kerala kasavu and contemporary designer drapes,
              sourced directly from weavers.
            </p>
            <div className="hero-ctas">
              <a className="btn btn-light" href="#products">Shop the collection</a>
              <button type="button" className="btn btn-ghost-light"
                onClick={() => { update({ view: 'deals', category: '', q: '' }); document.getElementById('products')?.scrollIntoView(); }}>
                View deals
              </button>
            </div>
          </div>
          <div className="hero-media">
            <img src="/images/sarees/hero-sarees.jpg" alt="A selection of folded handwoven silk sarees" width="900" height="1125" fetchpriority="high" />
          </div>
        </div>
      </section>

      {info && (
        <section className="trust full-bleed" aria-label="Shopping with InfinityStore">
          <div className="trust-item">
            <i className="bi bi-truck" aria-hidden="true"></i>
            <div><strong>Free shipping above {inrCompact(info.freeShippingThreshold)}</strong><span>Otherwise a flat {inrCompact(info.shippingFee)}</span></div>
          </div>
          <div className="trust-item">
            <i className="bi bi-cash-coin" aria-hidden="true"></i>
            <div><strong>Cash on delivery</strong><span>Pay when your order arrives</span></div>
          </div>
          <div className="trust-item">
            <i className="bi bi-receipt" aria-hidden="true"></i>
            <div><strong>Clear, upfront pricing</strong><span>Full GST and shipping breakdown before you order</span></div>
          </div>
        </section>
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

        <div className="catalog-controls">
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
        </div>

        {failed ? (
          <div className="notice" role="alert">
            <span>We couldn’t load the collection. Check your connection and try again.</span>
            <button type="button" className="btn btn-outline" onClick={load}>Retry</button>
          </div>
        ) : loading ? (
          <div className="pgrid" aria-busy="true" aria-label="Loading products">
            {Array.from({ length: 8 }, (_, i) => <ProductCardSkeleton key={i} />)}
          </div>
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
            {visible.map((p, i) => (
              <ProductCard
                key={p.id}
                priority={i < 4}
                product={p}
                mode={isAdmin() ? 'admin' : 'shop'}
                busy={busyId === p.id}
                onAdd={addToCart}
                onWish={addToWishlist}
              />
            ))}
          </div>
        )}
      </section>
    </>
  );
}
