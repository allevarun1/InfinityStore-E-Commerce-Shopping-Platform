import React from 'react';
import { Link } from 'react-router-dom';
import { inrCompact, isOnDeal, activePrice, discountPercent, LOW_STOCK } from '../lib/format';

export function ProductCard({ product, mode, busy, onAdd, onWish, priority = false }) {
  const soldOut = !product.stockQuantity || product.stockQuantity <= 0;
  const onDeal = isOnDeal(product);
  const href = `/products/${product.id}`;

  return (
    <article className="pcard">
      <Link to={href} className="pcard-media" tabIndex={-1} aria-hidden="true">
        <img src={product.imageUrl} alt="" loading={priority ? 'eager' : 'lazy'} decoding="async" width="600" height="800" />
        {onDeal && <span className="pcard-badge">{discountPercent(product)}% off</span>}
        {soldOut && <span className="pcard-soldout">Sold out</span>}
      </Link>

      {mode !== 'admin' && (
        <button
          type="button"
          className="pcard-wish"
          aria-label={`Save ${product.name} to wishlist`}
          onClick={() => onWish(product)}
        >
          <i className="bi bi-heart" aria-hidden="true"></i>
        </button>
      )}

      <div className="pcard-body">
        <span className="pcard-cat">{product.category?.name}</span>
        <h3 className="pcard-title"><Link to={href}>{product.name}</Link></h3>
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
            <button
              type="button"
              className="btn btn-primary"
              disabled={soldOut || busy}
              onClick={() => onAdd(product)}
            >
              {soldOut ? 'Sold out' : busy ? 'Adding…' : 'Add to cart'}
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
