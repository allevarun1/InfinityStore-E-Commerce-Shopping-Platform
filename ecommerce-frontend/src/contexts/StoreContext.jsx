import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useAuth } from './AuthContext';

const StoreContext = createContext(null);

/**
 * Store-wide facts (shipping and payment terms served by the backend) and the signed-in
 * customer's cart count for the header badge.
 */
export function StoreProvider({ children }) {
  const [info, setInfo] = useState(null);
  const [cartCount, setCartCount] = useState(0);
  const { currentUser } = useAuth();
  const customerId = currentUser && !(currentUser.roles || []).includes('ROLE_ADMIN') ? currentUser.id : null;

  useEffect(() => {
    ApiService.storeInfo().then(setInfo).catch(() => setInfo(null));
  }, []);

  const refreshCart = useCallback(() => {
    if (!customerId) {
      setCartCount(0);
      return;
    }
    ApiService.cart()
      .then(items => setCartCount(items.reduce((n, i) => n + i.quantity, 0)))
      .catch(() => {});
  }, [customerId]);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  return (
    <StoreContext.Provider value={{ info, cartCount, refreshCart }}>
      {children}
    </StoreContext.Provider>
  );
}

export function useStore() {
  return useContext(StoreContext);
}
