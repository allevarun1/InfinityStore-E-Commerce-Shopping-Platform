import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';
import { EmptyState } from './EmptyState';

export function Cart() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  const load = () => {
    setLoading(true);
    ApiService.cart()
      .then(setItems)
      .catch(() => {
        toast.error('Failed to load cart');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    load();
  }, []);

  const getActivePrice = (product) => (product.deal && product.dealPrice) ? product.dealPrice : product.price;

  const total = () => items.reduce((sum, item) => sum + getActivePrice(item.product) * item.quantity, 0);

  const setQty = (item, quantity) => {
    if (quantity < 1) {
      remove(item);
      return;
    }
    ApiService.updateCart(item.id, quantity)
      .then(() => load())
      .catch((err) => {
        toast.error(err.response?.data?.message || 'Could not update quantity');
      });
  };

  const remove = (item) => {
    ApiService.removeCart(item.id)
      .then(() => {
        toast.success('Removed from cart');
        load();
      })
      .catch(() => {
        toast.error('Could not remove item');
      });
  };

  if (loading) {
    return <Loader message="Loading your cart..." />;
  }

  return (
    <section className="panel">
      <h1>Cart</h1>
      {!items.length ? (
        <EmptyState 
          icon="bi-cart-x" 
          title="Your cart is empty" 
          message="Looks like you haven't added anything to your cart yet." 
          actionText="Start shopping" 
          actionLink="/" 
        />
      ) : (
        <>
          {items.map(item => {
            const activePrice = getActivePrice(item.product);
            return (
              <div className="list-row" key={item.id}>
                <img src={item.product.imageUrl} alt={item.product.name} />
                <div>
                  <strong>{item.product.name}</strong>
                  {item.product.deal && item.product.dealPrice ? (
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginTop: '4px' }}>
                      <span className="deal-current-price" style={{ fontSize: '1rem' }}>₹{item.product.dealPrice.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                      <span className="deal-original-price">₹{item.product.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                      <span className="deal-percent-off">{Math.round(((item.product.price - item.product.dealPrice) / item.product.price) * 100)}% off</span>
                    </div>
                  ) : (
                    <p>₹{activePrice.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
                  )}
                </div>
                <button className="cart-qty-btn" onClick={() => setQty(item, item.quantity - 1)}>-</button>
                <span className="cart-qty-val">{item.quantity}</span>
                <button className="cart-qty-btn" onClick={() => setQty(item, item.quantity + 1)}>+</button>
                <button className="ghost" onClick={() => remove(item)}>Remove</button>
              </div>
            );
          })}
          
          <h2>Subtotal: ₹{total().toLocaleString('en-IN', { minimumFractionDigits: 2 })}</h2>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>GST and shipping are calculated at checkout.</p>
          <Link className="primary link-button" to="/checkout">Checkout</Link>
        </>
      )}
    </section>
  );
}
