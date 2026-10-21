import React, { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { guestCart, MAX_PER_LINE } from '../lib/guestCart';
import { DeliveryCheck, OfferProgress } from './Shopper';
import { inr, inrCompact, isOnDeal, activePrice, imageSrc } from '../lib/format';
import { useStore } from '../contexts/StoreContext';
import { Loader } from './Loader';
import { EmptyState } from './EmptyState';

/** The cart for visitors who have not signed in. Prices are indicative; checkout shows the exact total. */
export function GuestCart() {
  const [lines, setLines] = useState([]);
  const [loading, setLoading] = useState(true);
  const { info } = useStore();
  const navigate = useNavigate();

  const load = useCallback(() => {
    const stored = guestCart.items();
    return Promise.all(stored.map(i =>
      ApiService.product(i.productId)
        .then(product => ({ product, quantity: Math.min(i.quantity, product.stockQuantity || 0) }))
        .catch(err => {
          // Deleted or unpublished products leave the cart quietly.
          if (err.response?.status === 404) guestCart.remove(i.productId);
          return null;
        })))
      .then(result => setLines(result.filter(l => l && l.quantity > 0)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    document.title = 'Your cart | Rekha Shopping Mall';
    load();
  }, [load]);

  if (loading) return <Loader message="Loading your cart..." />;

  if (!lines.length) {
    return (
      <section className="panel">
        <EmptyState icon="bi-bag" title="Your cart is empty"
          message="Browse the collection and add the drapes you love."
          actionText="Shop the collection" actionLink="/#products" />
      </section>
    );
  }

  const setQty = (line, quantity) => {
    guestCart.setQuantity(line.product.id, quantity);
    setLines(prev => prev.map(l => (l.product.id === line.product.id ? { ...l, quantity } : l)));
  };
  const remove = (line) => {
    guestCart.remove(line.product.id);
    setLines(prev => prev.filter(l => l.product.id !== line.product.id));
  };

  const subtotal = lines.reduce((sum, l) => sum + activePrice(l.product) * l.quantity, 0);
  const units = lines.reduce((n, l) => n + l.quantity, 0);
  const threshold = info ? Number(info.freeShippingThreshold) : null;
  const remaining = threshold != null ? Math.max(1, Math.floor(threshold - subtotal) + 1) : null;

  return (
    <div className="cart-page">
      <h1>Your cart</h1>
      <p>{units} {units === 1 ? 'item' : 'items'}</p>

      <div className="cart-grid">
        <ul className="cart-lines">
          {lines.map(line => {
            const { product } = line;
            const maxQty = Math.min(MAX_PER_LINE, product.stockQuantity || 0);
            return (
              <li className="cart-line" key={product.id}>
                <Link to={`/products/${product.id}`} tabIndex={-1} aria-hidden="true">
                  <img src={imageSrc(product.imageUrl)} alt="" loading="lazy" />
                </Link>
                <div>
                  <h3><Link to={`/products/${product.id}`}>{product.name}</Link></h3>
                  <div className="price">
                    <span>{inr(activePrice(product))}</span>
                    {isOnDeal(product) && <span className="price-was">{inr(product.price)}</span>}
                  </div>
                  <div className="cart-line-controls">
                    <div className="stepper sm" role="group" aria-label={`Quantity of ${product.name}`}>
                      <button type="button" aria-label="Decrease quantity" disabled={line.quantity <= 1}
                        onClick={() => setQty(line, line.quantity - 1)}>−</button>
                      <output aria-live="polite">{line.quantity}</output>
                      <button type="button" aria-label="Increase quantity" disabled={line.quantity >= maxQty}
                        onClick={() => setQty(line, line.quantity + 1)}>+</button>
                    </div>
                    <button type="button" className="text-link" onClick={() => remove(line)}>Remove</button>
                  </div>
                </div>
                <div className="cart-line-total">{inr(activePrice(product) * line.quantity)}</div>
              </li>
            );
          })}
        </ul>

        <aside className="summary" aria-labelledby="summary-title">
          <h2 id="summary-title">Order summary</h2>
          {remaining != null && (
            <div className="ship-progress">
              {subtotal > threshold
                ? <>You’ve unlocked <strong>free shipping</strong>.</>
                : <>Add <strong>{inrCompact(remaining)}</strong> more for free shipping.</>}
              <div className="bar" aria-hidden="true">
                <span style={{ width: `${Math.min(100, (subtotal / threshold) * 100)}%` }}></span>
              </div>
            </div>
          )}
          <OfferProgress units={lines.reduce((n, l) => n + l.quantity, 0)} />
          <div className="summary-row total"><span>Subtotal</span><span>{inr(subtotal)}</span></div>
          <p className="secure-note">GST and shipping are added at checkout, where you can also apply a coupon.</p>
          <DeliveryCheck compact />
          <button type="button" className="btn btn-primary"
            onClick={() => navigate('/login', { state: { from: { pathname: '/checkout' } } })}>
            Sign in to check out
          </button>
          <p className="secure-note" style={{ textAlign: 'center' }}>
            New here? <Link to="/register" state={{ from: { pathname: '/checkout' } }}>Create an account</Link> — your cart is kept.
          </p>
          <p className="secure-note"><i className="bi bi-cash-coin" aria-hidden="true"></i> Cash on delivery available</p>
          <p style={{ textAlign: 'center', marginTop: 8 }}><Link to="/#products">Continue shopping</Link></p>
        </aside>
      </div>
    </div>
  );
}
