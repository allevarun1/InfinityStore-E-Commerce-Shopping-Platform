import React, { useEffect, useState } from 'react';
import { imageSrc } from '../lib/format';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';
import { EmptyState } from './EmptyState';

export function Wishlist() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  const load = () => {
    setLoading(true);
    ApiService.wishlist()
      .then(setItems)
      .catch(() => {
        toast.error('Failed to load wishlist');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    load();
  }, []);

  const addToCart = (item) => {
    ApiService.addToCart(item.product.id)
      .then(() => {
        toast.success('Product added to cart');
      })
      .catch((err) => {
        toast.error(err.response?.data?.message || 'Could not add item');
      });
  };

  const remove = (item) => {
    ApiService.removeWishlist(item.product.id)
      .then(() => {
        toast.success('Removed from wishlist');
        load();
      })
      .catch(() => {
        toast.error('Could not remove item');
      });
  };

  if (loading) {
    return <Loader message="Loading wishlist..." />;
  }

  return (
    <section className="panel">
      <h1>Wishlist</h1>
      {!items.length ? (
        <EmptyState 
          icon="bi-heart" 
          title="Your wishlist is empty" 
          message="Save items you like to view or purchase them later." 
          actionText="Explore Products" 
          actionLink="/" 
        />
      ) : (
        items.map(item => (
          <div className="list-row" key={item.id}>
            <img src={imageSrc(item.product.imageUrl)} alt={item.product.name} />
            <div className="wishlist-row-div">
              <strong>{item.product.name}</strong>
              {item.product.deal && item.product.dealPrice ? (
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginTop: '4px' }}>
                  <span className="deal-current-price" style={{ fontSize: '1rem' }}>₹{item.product.dealPrice.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  <span className="deal-original-price">₹{item.product.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  <span className="deal-percent-off">{Math.round(((item.product.price - item.product.dealPrice) / item.product.price) * 100)}% off</span>
                </div>
              ) : (
                <p>₹{item.product.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
              )}
            </div>
            <button className="wishlist-btn-add" onClick={() => addToCart(item)}>Add to cart</button>
            <button className="ghost" onClick={() => remove(item)}>Remove</button>
          </div>
        ))
      )}
    </section>
  );
}
