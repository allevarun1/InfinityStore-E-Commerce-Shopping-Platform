import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { useStore } from '../contexts/StoreContext';
import { Loader } from './Loader';
import { GuestCart } from './GuestCart';
import { useAuth } from '../contexts/AuthContext';
import { EmptyState } from './EmptyState';
import { inr, inrCompact, isOnDeal, imageSrc } from '../lib/format';
import { DeliveryCheck, OfferProgress, CartCrossSell } from './Shopper';

export function Cart() {
  const { isLoggedIn } = useAuth();
  return isLoggedIn() ? <ServerCart /> : <GuestCart />;
}

function ServerCart() {
  const [items, setItems] = useState([]);
  const [quote, setQuote] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const toast = useToast();
  const { info, refreshCart } = useStore();

  // The summary always comes from the server quote, the same calculation checkout charges.
  const load = useCallback(() => {
    return Promise.all([ApiService.cart(), ApiService.cartQuote()])
      .then(([c, q]) => {
        setItems(c);
        setQuote(q);
      })
      .catch(() => toast.error('Failed to load your cart'))
      .finally(() => setLoading(false));
  }, [toast]);

  useEffect(() => {
    document.title = 'Your cart | Rekha Shopping Mall';
    load();
  }, [load]);

  const mutate = (item, request, successMessage) => {
    setBusyId(item.id);
    request
      .then(() => {
        if (successMessage) toast.success(successMessage);
        refreshCart();
        return load();
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not update your cart'))
      .finally(() => setBusyId(null));
  };

  const setQty = (item, quantity) => mutate(item, ApiService.updateCart(item.id, quantity));
  const remove = (item) => mutate(item, ApiService.removeCart(item.id), `${item.product.name} removed`);

  if (loading) {
    return <Loader message="Loading your cart..." />;
  }

  if (!items.length) {
    return (
      <section className="panel">
        <EmptyState
          icon="bi-bag"
          title="Your cart is empty"
          message="Browse the collection and add the drapes you love."
          actionText="Shop the collection"
          actionLink="/#products"
        />
      </section>
    );
  }

  const lineFor = (item) => quote?.lines.find(l => l.cartItemId === item.id);
  const threshold = info ? Number(info.freeShippingThreshold) : null;
  const taxable = quote ? Number(quote.taxableAmount) : 0;
  // Free shipping applies strictly above the threshold; the server's shipping figure is the authority.
  const freeShipping = quote && Number(quote.shipping) === 0;
  const remaining = threshold != null ? Math.max(1, Math.floor(threshold - taxable) + 1) : null;
  const units = items.reduce((n, i) => n + i.quantity, 0);

  return (
    <div className="cart-page">
      <h1>Your cart</h1>
      <p>{units} {units === 1 ? 'item' : 'items'}</p>

      <div className="cart-grid">
        <ul className="cart-lines">
          {items.map(item => {
            const line = lineFor(item);
            const maxQty = Math.min(10, item.product.stockQuantity || 0);
            const busy = busyId === item.id;
            return (
              <li className="cart-line" key={item.id} aria-busy={busy}>
                <Link to={`/products/${item.product.id}`} tabIndex={-1} aria-hidden="true">
                  <img src={imageSrc(item.product.imageUrl)} alt="" loading="lazy" />
                </Link>
                <div>
                  <h3><Link to={`/products/${item.product.id}`}>{item.product.name}</Link></h3>
                  <div className="price">
                    <span>{inr(line ? line.unitPrice : item.product.price)}</span>
                    {isOnDeal(item.product) && <span className="price-was">{inr(item.product.price)}</span>}
                  </div>
                  <div className="cart-line-controls">
                    <div className="stepper sm" role="group" aria-label={`Quantity of ${item.product.name}`}>
                      <button type="button" aria-label="Decrease quantity" disabled={busy || item.quantity <= 1}
                        onClick={() => setQty(item, item.quantity - 1)}>−</button>
                      <output aria-live="polite">{item.quantity}</output>
                      <button type="button" aria-label="Increase quantity" disabled={busy || item.quantity >= maxQty}
                        onClick={() => setQty(item, item.quantity + 1)}>+</button>
                    </div>
                    <button type="button" className="text-link" disabled={busy} onClick={() => remove(item)}>Remove</button>
                  </div>
                </div>
                <div className="cart-line-total">{line ? inr(line.lineNet) : ''}</div>
              </li>
            );
          })}
        </ul>

        {quote && (
          <aside className="summary" aria-labelledby="summary-title">
            <h2 id="summary-title">Order summary</h2>
            {remaining != null && (
              <div className="ship-progress">
                {freeShipping
                  ? <>You’ve unlocked <strong>free shipping</strong>.</>
                  : <>Add <strong>{inrCompact(remaining)}</strong> more for free shipping.</>}
                <div className="bar" aria-hidden="true">
                  <span style={{ width: `${Math.min(100, (taxable / threshold) * 100)}%` }}></span>
                </div>
              </div>
            )}
            <OfferProgress units={units} saving={Number(quote.offerDiscount) || null} />
            <div className="summary-row"><span>Subtotal</span><span>{inr(quote.subtotal)}</span></div>
            {Number(quote.discount) > 0 && (
              <div className="summary-row" style={{ color: 'var(--success)' }}><span>Savings</span><span>−{inr(quote.discount)}</span></div>
            )}
            {Number(quote.offerDiscount) > 0 && (
              <div className="summary-row" style={{ color: 'var(--success)' }}><span>{quote.offerLabel}</span><span>−{inr(quote.offerDiscount)}</span></div>
            )}
            <div className="summary-row"><span>GST</span><span>{inr(quote.tax)}</span></div>
            <div className="summary-row"><span>Shipping</span><span>{Number(quote.shipping) === 0 ? 'Free' : inr(quote.shipping)}</span></div>
            <div className="summary-row total"><span>Total</span><span>{inr(quote.total)}</span></div>
            <DeliveryCheck compact />
            <Link className="btn btn-primary" to="/checkout">Proceed to checkout</Link>
            <p className="secure-note"><i className="bi bi-cash-coin" aria-hidden="true"></i> Cash on delivery available</p>
            <p style={{ textAlign: 'center', marginTop: 8 }}><Link to="/#products">Continue shopping</Link></p>
          </aside>
        )}
      </div>
      <CartCrossSell inCartIds={items.map(i => i.product.id)} categoryIds={items.map(i => i.product.category?.id).filter(Boolean)} onAdded={() => { refreshCart(); load(); }} />
    </div>
  );
}
