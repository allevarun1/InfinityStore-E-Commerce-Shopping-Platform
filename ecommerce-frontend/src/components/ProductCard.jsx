import React from 'react';
import { Link } from 'react-router-dom';
import { inrCompact, isOnDeal, activePrice, discountPercent, LOW_STOCK, imageSrc } from '../lib/format';
import { useWishlist } from '../lib/useWishlist';

function WishHeart({ product }) {
  const { isWished, toggle } = useWishlist();
  const saved = isWished(product.id);
  return (
    <button
      type="button"
      className={`pcard-wish ${saved ? 'saved' : ''}`}
      aria-pressed={saved}
      aria-label={saved ? `Remove ${product.name} from wishlist` : `Save ${product.name} to wishlist`}
      onClick={() => toggle(product)}
    >
      <i className={`bi ${saved ? 'bi-heart-fill' : 'bi-heart'}`} aria-hidden="true"></i>
    </button>
  );
}

export function ProductCard({ product, mode, busy, onAdd, priority = false }) {
  const soldOut = !product.stockQuantity || product.stockQuantity <= 0;
  const onDeal = isOnDeal(product);
  const href = `/products/${product.id}`;
  const addLabel = soldOut ? 'Sold out' : busy ? 'Adding…' : 'Add to cart';

  return (
    <article className="pcard">
      <div className="pcard-mediawrap">
        <Link to={href} className="pcard-media" tabIndex={-1} aria-hidden="true">
          <img src={imageSrc(product.imageUrl)} alt="" loading={priority ? 'eager' : 'lazy'} decoding="async" width="600" height="800" />
          {onDeal && <span className="pcard-badge">{discountPercent(product)}% off</span>}
          {soldOut && <span className="pcard-soldout">Sold out</span>}
        </Link>
        {mode !== 'admin' && <WishHeart product={product} />}
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
