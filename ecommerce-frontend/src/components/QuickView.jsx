import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { inr, inrCompact, isOnDeal, activePrice, discountPercent, imageSrc, LOW_STOCK } from '../lib/format';
import { useCartActions } from '../lib/useCartActions';

/** A fast look at a saree without leaving the collection: photos, price, stock and add to cart. */
export function QuickView({ product, onClose }) {
  const photos = [product.imageUrl, ...(product.gallery || [])].filter(Boolean).map(imageSrc);
  const [i, setI] = useState(0);
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const { addToCart } = useCartActions();
  const closeRef = useRef(null);
  const soldOut = !product.stockQuantity || product.stockQuantity <= 0;
  const maxQty = Math.min(10, product.stockQuantity || 0);

  useEffect(() => {
    const opener = document.activeElement;
    closeRef.current?.focus();
    document.body.classList.add('no-scroll');
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('no-scroll');
      opener?.focus?.();
    };
  }, [onClose]);

  const add = async () => {
    setBusy(true);
    const ok = await addToCart(product, qty);
    setBusy(false);
    if (ok) onClose();
  };

  return (
    <div className="qv-root">
      <div className="drawer-backdrop" onClick={onClose} aria-hidden="true"></div>
      <div className="qv" role="dialog" aria-modal="true" aria-labelledby="qv-title">
        <button ref={closeRef} type="button" className="drawer-close qv-close" onClick={onClose} aria-label="Close"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
        <div className="qv-media">
          <img src={photos[i]} alt={product.name} data-fly-src={product.id} width="600" height="750" />
          {photos.length > 1 && (
            <div className="qv-thumbs">
              {photos.map((src, n) => (
                <button key={src + n} type="button" className={n === i ? 'on' : ''} onClick={() => setI(n)} aria-label={`Photo ${n + 1}`}>
                  <img src={src} alt="" width="56" height="70" />
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="qv-info">
          <span className="eyebrow">{product.category?.name}</span>
          <h2 id="qv-title">{product.name}</h2>
          {product.reviewCount > 0 && (
            <p className="pcard-rating"><i className="bi bi-star-fill" aria-hidden="true"></i> {product.averageRating} <small>({product.reviewCount} reviews)</small></p>
          )}
          <div className="price">
            <span className="price-now">{inr(activePrice(product))}</span>
            {isOnDeal(product) && <>
              <span className="price-was">{inrCompact(product.price)}</span>
              <span className="price-off">{discountPercent(product)}% off</span>
            </>}
          </div>
          <p className={`stock-line ${soldOut ? 'out' : product.stockQuantity < LOW_STOCK ? 'low' : ''}`}>
            <span className="dot" aria-hidden="true"></span>
            {soldOut ? 'Sold out' : product.stockQuantity < LOW_STOCK ? `Only ${product.stockQuantity} left` : 'In stock, ready to ship'}
          </p>
          <p className="qv-desc">{product.description}</p>
          {[product.fabric, product.weave, product.occasion].some(Boolean) && (
            <ul className="qv-tags">{[product.fabric, product.weave, product.occasion].filter(Boolean).map(t => <li key={t}>{t}</li>)}</ul>
          )}
          {!soldOut && (
            <div className="buy-row">
              <div className="stepper" role="group" aria-label="Quantity">
                <button type="button" aria-label="Decrease quantity" disabled={qty <= 1} onClick={() => setQty(q => q - 1)}>−</button>
                <output aria-live="polite">{qty}</output>
                <button type="button" aria-label="Increase quantity" disabled={qty >= maxQty} onClick={() => setQty(q => q + 1)}>+</button>
              </div>
              <button type="button" className="btn btn-primary" disabled={busy} onClick={add}>
                <i className="bi bi-bag" aria-hidden="true"></i> {busy ? 'Adding…' : 'Add to cart'}
              </button>
            </div>
          )}
          <Link className="text-link" to={`/products/${product.id}`} onClick={onClose}>View full details →</Link>
        </div>
      </div>
    </div>
  );
}
