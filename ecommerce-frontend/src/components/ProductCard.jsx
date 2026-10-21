import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { inrCompact, isOnDeal, activePrice, discountPercent, LOW_STOCK, imageSet } from '../lib/format';

const CARD_SIZES = '(min-width: 1100px) 280px, (min-width: 700px) 33vw, 50vw';
import { useWishlist } from '../lib/useWishlist';
import { DemandLine } from './Shopper';
import { useInView, reducedMotion } from '../lib/motion';
import { useStore } from '../contexts/StoreContext';

function WishHeart({ product }) {
  const { isWished, toggle } = useWishlist();
  const saved = isWished(product.id);
  const [burst, setBurst] = useState(0);
  return (
    <button
      type="button"
      className={`pcard-wish ${saved ? 'saved' : ''}`}
      aria-pressed={saved}
      aria-label={saved ? `Remove ${product.name} from wishlist` : `Save ${product.name} to wishlist`}
      onClick={() => { if (!saved) setBurst(n => n + 1); toggle(product); }}
    >
      <i className={`bi ${saved ? 'bi-heart-fill' : 'bi-heart'}`} aria-hidden="true"></i>
      {burst > 0 && saved && <span key={burst} className="burst" aria-hidden="true">{[0, 1, 2, 3, 4, 5].map(k => <i key={k} style={{ '--k': k }}></i>)}</span>}
    </button>
  );
}

export function ProductCard({ product, mode, busy, onAdd, onQuickView, priority = false, index = 0 }) {
  const [ref, seen] = useInView();
  const { videos } = useStore();
  const clip = videos.find(v => v.productId === product.id) || null;
  const [hover, setHover] = useState(false);
  const preview = hover && clip?.videoUrl && !reducedMotion() ? clip : null;
  const soldOut = !product.stockQuantity || product.stockQuantity <= 0;
  const onDeal = isOnDeal(product);
  const href = `/products/${product.id}`;
  const addLabel = soldOut ? 'Sold out' : busy ? 'Adding…' : 'Add to cart';

  return (
    <article ref={ref} className={`pcard rise ${seen ? 'in' : ''}`} style={{ '--d': `${(index % 4) * 90}ms` }}
      onPointerEnter={(e) => { if (e.pointerType === 'mouse') setHover(true); }} onPointerLeave={() => setHover(false)}>
      <div className="pcard-mediawrap">
        <Link to={href} className="pcard-media" tabIndex={-1} aria-hidden="true">
          <img {...imageSet(product.imageUrl, CARD_SIZES)} alt="" data-fly-src={product.id} loading={priority ? 'eager' : 'lazy'} decoding="async" width="600" height="800" />
          {product.gallery?.[0] && <img className="pcard-alt" {...imageSet(product.gallery[0], CARD_SIZES)} alt="" loading="lazy" decoding="async" width="600" height="800" />}
          {preview && <video className="pcard-vid" src={preview.videoUrl} poster={preview.posterUrl || undefined} autoPlay muted loop playsInline aria-hidden="true" />}
          {clip && <span className="pcard-play"><i className="bi bi-play-fill" aria-hidden="true"></i> Video</span>}
          {onDeal && <span className="pcard-badge">{discountPercent(product)}% off</span>}
          {soldOut && <span className="pcard-soldout">Sold out</span>}
        </Link>
        {mode !== 'admin' && <WishHeart product={product} />}
        {mode !== 'admin' && onQuickView && (
          <button type="button" className="pcard-qv" onClick={() => onQuickView(product)} aria-label={`Quick view ${product.name}`}>
            <i className="bi bi-eye" aria-hidden="true"></i><span>Quick view</span>
          </button>
        )}
        {mode !== 'admin' && !soldOut && (
          <button type="button" className="pcard-quick" disabled={busy} onClick={() => onAdd(product)}
            aria-label={`Quick add ${product.name} to cart`}>
            <i className="bi bi-bag-plus" aria-hidden="true"></i> {addLabel}
          </button>
        )}
      </div>

      <div className="pcard-body">
        <span className="pcard-cat">{product.category?.name}</span>
        <h3 className="pcard-title"><Link to={href}>{product.name}</Link></h3>
        {product.reviewCount > 0 && (
          <span className="pcard-rating" aria-label={`Rated ${product.averageRating} out of 5 from ${product.reviewCount} reviews`}>
            <i className="bi bi-star-fill" aria-hidden="true"></i> {product.averageRating} <small>({product.reviewCount})</small>
          </span>
        )}
        <DemandLine product={product} />
        <div className="price">
          <span className="price-now">{inrCompact(activePrice(product))}</span>
          {onDeal && <span className="price-was"><span className="sr-only">Was </span>{inrCompact(product.price)}</span>}
        </div>
        {!soldOut && product.stockQuantity < LOW_STOCK && (
          <span className="stock-note">Only {product.stockQuantity} left</span>
        )}

        <div className="pcard-actions">
          {mode === 'admin' ? (
            <Link className="btn btn-outline" to={`/admin?editId=${product.id}`}>Manage in admin</Link>
          ) : (
            <button type="button" className="btn btn-primary" disabled={soldOut || busy} onClick={() => onAdd(product)}>
              {addLabel}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}

export function ProductCardSkeleton() {
  return (
    <div className="pcard" aria-hidden="true">
      <div className="skel skel-media"></div>
      <div className="skel skel-line" style={{ width: '40%' }}></div>
      <div className="skel skel-line" style={{ width: '85%', height: 18 }}></div>
      <div className="skel skel-line" style={{ width: '30%' }}></div>
      <div className="skel skel-line" style={{ height: 42, marginTop: 14 }}></div>
    </div>
  );
}
