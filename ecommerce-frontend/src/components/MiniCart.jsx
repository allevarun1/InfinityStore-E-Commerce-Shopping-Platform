import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useStore } from '../contexts/StoreContext';
import { guestCart } from '../lib/guestCart';
import { inr, inrCompact, activePrice, imageSrc } from '../lib/format';

/**
 * Slide-in confirmation after every Add to cart: what was added, progress to free shipping, one-tap
 * checkout, and a few matching sarees. Amounts are indicative; checkout shows the exact total.
 */
export function MiniCart() {
  const { added, closeAdded, info, cartCount } = useStore();
  const { isLoggedIn } = useAuth();
  const navigate = useNavigate();
  const closeRef = useRef(null);
  const openerRef = useRef(null);
  const [subtotal, setSubtotal] = useState(null);
  const [suggestions, setSuggestions] = useState([]);

  const open = Boolean(added);

  useEffect(() => {
    if (!open) return undefined;
    openerRef.current = document.activeElement;
    closeRef.current?.focus();
    const onKey = (e) => { if (e.key === 'Escape') closeAdded(); };
    document.addEventListener('keydown', onKey);
    document.body.classList.add('no-scroll');
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('no-scroll');
      openerRef.current?.focus?.();
    };
  }, [open, closeAdded]);

  useEffect(() => {
    if (!open) return;
    setSubtotal(null);
    const product = added.product;
    ApiService.products().then(all => {
      setSuggestions(all
        .filter(p => p.id !== product.id && p.stockQuantity > 0)
        .map(p => ({ p, s: (p.category?.id === product.category?.id ? 2 : 0) + (p.occasion && p.occasion === product.occasion ? 1 : 0) }))
        .sort((a, b) => b.s - a.s || a.p.id - b.p.id).slice(0, 3).map(x => x.p));
      if (!isLoggedIn()) {
        setSubtotal(guestCart.items().reduce((sum, i) => {
          const p = all.find(x => x.id === i.productId);
          return sum + (p ? activePrice(p) * i.quantity : 0);
        }, 0));
      }
    }).catch(() => {});
    if (isLoggedIn()) {
      ApiService.cartQuote().then(q => setSubtotal(Number(q.taxableAmount))).catch(() => {});
    }
  }, [open, added, isLoggedIn]);

  if (!open) return null;
  const { product, quantity } = added;
  const threshold = info ? Number(info.freeShippingThreshold) : null;
  const remaining = threshold != null && subtotal != null ? Math.floor(threshold - subtotal) + 1 : null;
  const unlocked = remaining != null && remaining <= 0;

  const checkout = () => {
    closeAdded();
    navigate(isLoggedIn() ? '/checkout' : '/login', isLoggedIn() ? undefined : { state: { from: { pathname: '/checkout' } } });
  };

  return (
    <div className="drawer-root">
      <div className="drawer-backdrop" onClick={closeAdded} aria-hidden="true"></div>
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
        <header className="drawer-head">
          <h2 id="drawer-title"><i className="bi bi-check-circle-fill" aria-hidden="true"></i> Added to your cart</h2>
          <button ref={closeRef} type="button" className="drawer-close" onClick={closeAdded} aria-label="Close">
            <i className="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        </header>

        <div className="drawer-item">
          <img src={imageSrc(product.imageUrl)} alt="" width="72" height="96" />
          <div>
            <strong>{product.name}</strong>
            <span>Qty {quantity} · {inr(activePrice(product) * quantity)}</span>
          </div>
        </div>

        {remaining != null && (
          <div className="ship-progress">
            {unlocked
              ? <>You’ve unlocked <strong>free shipping</strong>.</>
              : <>Add <strong>{inrCompact(remaining)}</strong> more for free shipping.</>}
            <div className="bar" aria-hidden="true">
              <span style={{ width: `${Math.min(100, (subtotal / threshold) * 100)}%` }}></span>
            </div>
          </div>
        )}

        <div className="drawer-cta">
          <Link className="btn btn-outline" to="/cart" onClick={closeAdded}>View cart ({cartCount})</Link>
          <button type="button" className="btn btn-primary" onClick={checkout}>
            Check out
          </button>
        </div>

        {suggestions.length > 0 && (
          <section aria-labelledby="drawer-sugg">
            <h3 id="drawer-sugg">Complete the look</h3>
            <ul className="drawer-sugg">
              {suggestions.map(p => (
                <li key={p.id}>
                  <Link to={`/products/${p.id}`} onClick={closeAdded}>
                    <img src={imageSrc(p.imageUrl)} alt="" loading="lazy" width="72" height="96" />
                    <span>{p.name}</span>
                    <b>{inrCompact(activePrice(p))}</b>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}
        <button type="button" className="text-link drawer-continue" onClick={closeAdded}>Continue shopping</button>
      </aside>
    </div>
  );
}
