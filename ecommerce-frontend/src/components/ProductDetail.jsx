import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { useStore } from '../contexts/StoreContext';
import { inr, inrCompact, isOnDeal, activePrice, discountPercent, LOW_STOCK, imageSrc } from '../lib/format';
import { NotFound } from './NotFound';
import { Reviews } from './Reviews';
import { StarRating } from './StarRating';
import { WhatsAppAsk, ProductShelves, rememberViewed } from './Growth';
import { useCartActions } from '../lib/useCartActions';
import { track } from '../lib/analytics';
import { useWishlist } from '../lib/useWishlist';
import { ProductGallery } from './Gallery';
import { NotifyMe } from './NotifyMe';
import { ColourSwatches } from './ColourSwatches';
import { WatchButton } from './Videos';
import { DeliveryCheck, OfferStrip, PriceBreakup, DemandLine, VideoCallLink } from './Shopper';

// Search engines that run JavaScript read this to show price and availability in results.
function setProductMeta(p) {
  const price = activePrice(p);
  const description = (p.description || '').replace(/\s+/g, ' ').trim().slice(0, 300);
  let meta = document.querySelector('meta[name="description"]');
  if (meta && description) meta.setAttribute('content', description);
  let tag = document.getElementById('product-jsonld');
  if (!tag) {
    tag = document.createElement('script');
    tag.id = 'product-jsonld';
    tag.type = 'application/ld+json';
    document.head.appendChild(tag);
  }
  tag.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: p.name,
    description,
    image: p.imageUrl ? new URL(imageSrc(p.imageUrl), window.location.origin).href : undefined,
    offers: {
      '@type': 'Offer',
      priceCurrency: 'INR',
      price: price.toFixed(2),
      availability: p.stockQuantity > 0 ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
      url: window.location.href,
    },
  });
}

export function ProductDetail() {
  const { id } = useParams();
  const [product, setProduct] = useState(null);
  const [status, setStatus] = useState('loading'); // loading | ready | missing | error
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const { isAdmin } = useAuth();
  const { info, showAdded } = useStore();
  const { isWished, toggle: toggleWish } = useWishlist();
  const cartActions = useCartActions();
  const toast = useToast();
  const navigate = useNavigate();

  const refreshProduct = () => ApiService.product(id).then(setProduct).catch(() => {});

  useEffect(() => {
    setStatus('loading');
    setQty(1);
    ApiService.product(id)
      .then(p => {
        setProduct(p);
        setStatus('ready');
        document.title = `${p.name} | InfinityStore`;
        setProductMeta(p);
        rememberViewed(p.id);
        track.viewItem(p);
      })
      .catch(err => setStatus(err.response?.status === 404 ? 'missing' : 'error'));
    return () => document.getElementById('product-jsonld')?.remove();
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

  const add = async (thenCheckout) => {
    setBusy(true);
    const ok = await cartActions.addToCart(product, qty, { quiet: true });
    setBusy(false);
    if (!ok) return;
    if (thenCheckout) cartActions.goToCheckout();
    else showAdded(product, qty);
  };

  const share = async () => {
    const data = { title: product.name, text: `${product.name} at InfinityStore`, url: window.location.href };
    try {
      if (navigator.share) await navigator.share(data);
      else {
        await navigator.clipboard.writeText(data.url);
        toast.info('Link copied');
      }
    } catch {
      // the share sheet was dismissed
    }
  };

  const copyCode = () => navigator.clipboard?.writeText(info.welcomeOffer.code).then(() => toast.info('Code copied')).catch(() => {});

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
        <ProductGallery product={product} />

        <div className="pdp-info">
          <span className="eyebrow">{product.category.name}</span>
          <h1>{product.name}</h1>
          <DemandLine product={product} />
          <WatchButton productId={product.id} />
          {product.reviewCount > 0 && (
            <a className="rating-summary" href="#reviews" aria-label={`Rated ${product.averageRating} out of 5 from ${product.reviewCount} ${product.reviewCount === 1 ? 'review' : 'reviews'}`}>
              <StarRating rating={product.averageRating} />
              <span>{product.averageRating} · {product.reviewCount} {product.reviewCount === 1 ? 'review' : 'reviews'}</span>
            </a>
          )}

          <ColourSwatches product={product} />

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

          {info?.welcomeOffer && (
            <div className="offer-chip">
              <i className="bi bi-tag" aria-hidden="true"></i>
              <span>New here? <strong>{info.welcomeOffer.summary}</strong> with code <code>{info.welcomeOffer.code}</code></span>
              <button type="button" onClick={copyCode}>Copy</button>
            </div>
          )}

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
                <button type="button" className="btn btn-outline" aria-pressed={isWished(product.id)} onClick={() => toggleWish(product)}>
                  <i className={`bi ${isWished(product.id) ? 'bi-heart-fill' : 'bi-heart'}`} aria-hidden="true"></i> {isWished(product.id) ? 'Saved to wishlist' : 'Save to wishlist'}
                </button>
              </div>
              {soldOut && <NotifyMe product={product} />}
              <div className="pdp-actions-row">
                <WhatsAppAsk product={product} />
                <VideoCallLink product={product} />
                <button type="button" className="btn btn-outline share-btn" onClick={share}>
                  <i className="bi bi-share" aria-hidden="true"></i> Share
                </button>
              </div>
            </>
          )}

          {!soldOut && <DeliveryCheck />}
          <OfferStrip />
          <PriceBreakup product={product} />

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

          <div className="acc">
            <details open>
              <summary>Details</summary>
              <div className="acc-body">
                <p>{product.description}</p>
                {specs.length > 0 && (
                  <dl className="specs">
                    {specs.map(([label, value]) => (
                      <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
                    ))}
                  </dl>
                )}
              </div>
            </details>
            <details>
              <summary>Shipping &amp; returns</summary>
              <div className="acc-body">
                <ul>
                  {info && <li>Free shipping above {inrCompact(info.freeShippingThreshold)}; otherwise {inrCompact(info.shippingFee)}.</li>}
                  <li>Cash on delivery available.</li>
                  <li>Returns are accepted for damaged or incorrect products. <Link to="/shipping">Read the full policy</Link>.</li>
                </ul>
              </div>
            </details>
            <details>
              <summary>Need help choosing?</summary>
              <div className="acc-body">
                <p>We’re happy to help with colour, drape and occasion. Visit our <Link to="/contact">contact page</Link>{info?.whatsappNumber ? ' or tap the WhatsApp button' : ''}.</p>
              </div>
            </details>
          </div>
        </div>
      </div>

      <Reviews productId={product.id} onChange={refreshProduct} />

      <ProductShelves product={product} />

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
