import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { useStore } from '../contexts/StoreContext';
import { inr, inrCompact, isOnDeal, activePrice, discountPercent, LOW_STOCK, imageSrc } from '../lib/format';
import { NotFound } from './NotFound';
import { Reviews } from './Reviews';
import { StarRating } from './StarRating';

export function ProductDetail() {
  const { id } = useParams();
  const [product, setProduct] = useState(null);
  const [status, setStatus] = useState('loading'); // loading | ready | missing | error
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [zoom, setZoom] = useState(null);
  const { isAdmin, isLoggedIn } = useAuth();
  const { info, refreshCart } = useStore();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const refreshProduct = () => ApiService.product(id).then(setProduct).catch(() => {});

  useEffect(() => {
    setStatus('loading');
    setQty(1);
    ApiService.product(id)
      .then(p => {
        setProduct(p);
        setStatus('ready');
        document.title = `${p.name} | InfinityStore`;
      })
      .catch(err => setStatus(err.response?.status === 404 ? 'missing' : 'error'));
  }, [id]);

  if (status === 'missing') {
    return <NotFound title="This saree isn’t available" message="It may have sold out or been removed from the collection." />;
  }
  if (status === 'error') {
    return (
      <div className="pdp">
        <div className="notice" role="alert">
          <span>We couldn’t load this product. Please try again.</span>
          <button type="button" className="btn btn-outline" onClick={() => navigate(0)}>Retry</button>
        </div>
      </div>
    );
  }
  if (status === 'loading') {
    return (
      <div className="pdp" aria-busy="true">
        <div className="pdp-grid">
          <div className="skel" style={{ aspectRatio: '4 / 5', borderRadius: 16 }}></div>
          <div>
            <div className="skel skel-line" style={{ width: '30%' }}></div>
            <div className="skel skel-line" style={{ width: '80%', height: 36, marginTop: 16 }}></div>
            <div className="skel skel-line" style={{ width: '40%', height: 28, marginTop: 24 }}></div>
            <div className="skel skel-line" style={{ height: 46, marginTop: 32 }}></div>
          </div>
        </div>
      </div>
    );
  }

  const soldOut = !product.stockQuantity || product.stockQuantity <= 0;
  const maxQty = Math.min(10, product.stockQuantity || 0);
  const onDeal = isOnDeal(product);
  const saving = onDeal ? product.price - product.dealPrice : 0;

  const add = (thenCheckout) => {
    if (!isLoggedIn()) {
      navigate('/login', { state: { from: location } });
      return;
    }
    setBusy(true);
    ApiService.addToCart(product.id, qty)
      .then(() => {
        refreshCart();
        if (thenCheckout) {
          navigate('/checkout');
        } else {
          toast.success(`Added ${qty} × ${product.name} to your cart`);
        }
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not add this item'))
      .finally(() => setBusy(false));
  };

  const wish = () => {
    if (!isLoggedIn()) {
      navigate('/login', { state: { from: location } });
      return;
    }
    ApiService.addWishlist(product.id)
      .then(() => toast.success('Saved to your wishlist'))
      .catch(err => toast.error(err.response?.data?.message || 'Could not save to wishlist'));
  };

  const moveZoom = (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    setZoom(`${((e.clientX - r.left) / r.width) * 100}% ${((e.clientY - r.top) / r.height) * 100}%`);
  };

  const specs = [
    ['Fabric', product.fabric],
    ['Weave', product.weave],
    ['Occasion', product.occasion],
    ['Colour', product.colour],
    ['Made in', product.region],
    ['Length', product.lengthMeters ? `${Number(product.lengthMeters)} m` : null],
    ['Blouse piece', product.blouseIncluded == null ? null : product.blouseIncluded ? 'Included' : 'Not included'],
    ['Care', product.careInstructions],
  ].filter(([, v]) => v);

  const stockClass = soldOut ? 'out' : product.stockQuantity < LOW_STOCK ? 'low' : '';
  const stockText = soldOut ? 'Sold out' : product.stockQuantity < LOW_STOCK
    ? `In stock — only ${product.stockQuantity} left` : 'In stock, ready to ship';

  return (
    <div className="pdp">
      <nav className="crumbs" aria-label="Breadcrumb">
        <Link to="/">Home</Link><span aria-hidden="true">/</span>
        <Link to={`/?category=${product.category.id}#products`}>{product.category.name}</Link><span aria-hidden="true">/</span>
        <span aria-current="page">{product.name}</span>
      </nav>

      <div className="pdp-grid">
        <div
          className={`pdp-media ${zoom ? 'zoomed' : ''}`}
          onMouseMove={moveZoom}
          onMouseLeave={() => setZoom(null)}
        >
          <img src={imageSrc(product.imageUrl)} alt={product.name} width="900" height="1125"
            style={zoom ? { transformOrigin: zoom } : undefined} />
        </div>

        <div className="pdp-info">
          <span className="eyebrow">{product.category.name}</span>
          <h1>{product.name}</h1>
          {product.reviewCount > 0 && (
            <a className="rating-summary" href="#reviews" aria-label={`Rated ${product.averageRating} out of 5 from ${product.reviewCount} ${product.reviewCount === 1 ? 'review' : 'reviews'}`}>
              <StarRating rating={product.averageRating} />
              <span>{product.averageRating} · {product.reviewCount} {product.reviewCount === 1 ? 'review' : 'reviews'}</span>
            </a>
          )}

          <div className="pdp-price">
            <div className="price">
              <span className="price-now">{inr(activePrice(product))}</span>
              {onDeal && (
                <>
                  <span className="price-was"><span className="sr-only">Was </span>{inr(product.price)}</span>
                  <span className="price-off">Save {inrCompact(saving)} ({discountPercent(product)}%)</span>
                </>
              )}
            </div>
            <small>Price excludes GST. Tax and shipping are shown in full before you place your order.</small>
          </div>

          <p className={`stock-line ${stockClass}`}><span className="dot" aria-hidden="true"></span>{stockText}</p>

          {isAdmin() ? (
            <Link className="btn btn-primary" to={`/admin?editId=${product.id}`}>Manage in admin</Link>
          ) : (
            <>
              <div className="buy-row">
                {!soldOut && (
                  <div className="stepper" role="group" aria-label="Quantity">
                    <button type="button" aria-label="Decrease quantity" disabled={qty <= 1} onClick={() => setQty(q => q - 1)}>−</button>
                    <output aria-live="polite">{qty}</output>
                    <button type="button" aria-label="Increase quantity" disabled={qty >= maxQty} onClick={() => setQty(q => q + 1)}>+</button>
                  </div>
                )}
                <button type="button" className="btn btn-primary" disabled={soldOut || busy} onClick={() => add(false)}>
                  <i className="bi bi-bag" aria-hidden="true"></i> {soldOut ? 'Sold out' : busy ? 'Adding…' : 'Add to cart'}
                </button>
              </div>
              <div className="buy-row">
                {!soldOut && (
                  <button type="button" className="btn btn-outline" disabled={busy} onClick={() => add(true)}>Buy now</button>
                )}
                <button type="button" className="btn btn-outline" onClick={wish}>
                  <i className="bi bi-heart" aria-hidden="true"></i> Save to wishlist
                </button>
              </div>
            </>
          )}

          {info && (
            <ul className="facts">
              <li><i className="bi bi-truck" aria-hidden="true"></i>
                <span>{activePrice(product) > info.freeShippingThreshold
                  ? 'Ships free with this item.'
                  : `Free shipping on orders above ${inrCompact(info.freeShippingThreshold)}; otherwise ${inrCompact(info.shippingFee)}.`}</span></li>
              <li><i className="bi bi-cash-coin" aria-hidden="true"></i><span>Cash on delivery available.</span></li>
              <li><i className="bi bi-receipt" aria-hidden="true"></i><span>GST is itemised on your order.</span></li>
            </ul>
          )}

          <section className="pdp-details" aria-labelledby="details-title">
            <h2 id="details-title">Details</h2>
            <p>{product.description}</p>
            {specs.length > 0 && (
              <dl className="specs">
                {specs.map(([label, value]) => (
                  <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
                ))}
              </dl>
            )}
          </section>
        </div>
      </div>

      <Reviews productId={product.id} onChange={refreshProduct} />

      {!isAdmin() && !soldOut && (
        <div className="mobile-buy">
          <div>
            <div className="price-now">{inrCompact(activePrice(product))}</div>
            {onDeal && <div className="price-was" style={{ fontSize: '0.8rem' }}>{inrCompact(product.price)}</div>}
          </div>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => add(false)}>
            {busy ? 'Adding…' : 'Add to cart'}
          </button>
        </div>
      )}
    </div>
  );
}
