import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ApiService } from '../services/api';
import { useAuth } from './AuthContext';
import { guestCart } from '../lib/guestCart';
import { configure } from '../lib/analytics';

const StoreContext = createContext(null);

/**
 * Store-wide facts (shipping and payment terms served by the backend), categories for navigation,
 * the cart count for the header badge, the signed-in customer's saved items, and the "added to cart"
 * panel that every Add to cart button opens.
 */
export function StoreProvider({ children }) {
  const [info, setInfo] = useState(null);
  const [categories, setCategories] = useState([]);
  const [offers, setOffers] = useState([]);
  const [videos, setVideos] = useState([]);
  const [cartCount, setCartCount] = useState(0);
  const [cartPulse, setCartPulse] = useState(0);
  const [wishIds, setWishIds] = useState([]);
  const [added, setAdded] = useState(null); // { product, quantity } while the mini-cart is open
  const { currentUser } = useAuth();
  const customerId = currentUser && !(currentUser.roles || []).includes('ROLE_ADMIN') ? currentUser.id : null;

  useEffect(() => {
    ApiService.storeInfo().then((i) => { setInfo(i); configure(i); }).catch(() => setInfo(null));
    ApiService.categories().then(setCategories).catch(() => setCategories([]));
    ApiService.offers().then(setOffers).catch(() => setOffers([]));
    ApiService.storeVideos().then(setVideos).catch(() => setVideos([]));
  }, []);

  const refreshCart = useCallback(() => {
    if (!customerId) {
      setCartCount(currentUser ? 0 : guestCart.count());
      return;
    }
    ApiService.cart()
      .then(items => setCartCount(items.reduce((n, i) => n + i.quantity, 0)))
      .catch(() => {});
  }, [customerId, currentUser]);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  useEffect(() => guestCart.subscribe(refreshCart), [refreshCart]);

  useEffect(() => {
    if (!customerId) {
      setWishIds([]);
      return;
    }
    ApiService.wishlist()
      .then(items => setWishIds(items.map(i => i.product?.id ?? i.productId).filter(Boolean)))
      .catch(() => {});
  }, [customerId]);

  const showAdded = useCallback((product, quantity = 1) => {
    setAdded({ product, quantity });
    setCartPulse(n => n + 1);
  }, []);
  const closeAdded = useCallback(() => setAdded(null), []);

  const value = useMemo(() => ({
    info, categories, offers, videos, cartCount, cartPulse, refreshCart, wishIds, setWishIds, added, showAdded, closeAdded,
  }), [info, categories, offers, videos, cartCount, cartPulse, refreshCart, wishIds, added, showAdded, closeAdded]);

  return (
    <StoreContext.Provider value={value}>
      {children}
    </StoreContext.Provider>
  );
}

export function useStore() {
  return useContext(StoreContext);
}
