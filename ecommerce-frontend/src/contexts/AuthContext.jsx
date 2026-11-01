import React, { createContext, useContext, useState } from 'react';
import axios from 'axios';
import { ApiService } from '../services/api';
import { guestCart } from '../lib/guestCart';

const AuthContext = createContext(null);

/** Moves items a visitor added before signing in into their server cart. Failures never block sign-in. */
async function mergeGuestCart(user) {
  const items = guestCart.items();
  if (!items.length || (user.roles || []).includes('ROLE_ADMIN')) return;
  await Promise.allSettled(items.map(i => ApiService.addToCart(i.productId, i.quantity)));
  guestCart.clear();
}

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(() => {
    const user = localStorage.getItem('infinity_user');
    return user ? JSON.parse(user) : null;
  });

  // Same origin as the rest of the API; set VITE_API_URL when deploying.
  const apiUrl = `${import.meta.env.VITE_API_URL || 'http://localhost:8080/api'}/auth`;

  const startSession = async (response) => {
    localStorage.setItem('infinity_token', response.token || response.accessToken);
    localStorage.setItem('infinity_user', JSON.stringify(response));
    setCurrentUser(response);
    await mergeGuestCart(response);
    return response;
  };

  const login = async (username, password) => {
    const res = await axios.post(`${apiUrl}/signin`, { username, password });
    return startSession(res.data);
  };

  /** Swaps the ID token Firebase gives after a correct OTP for our own session. */
  const loginWithPhone = async (idToken) => {
    const res = await axios.post(`${apiUrl}/phone`, { idToken });
    return startSession(res.data);
  };

  const signup = async (payload) => {
    const res = await axios.post(`${apiUrl}/signup`, payload);
    return res.data;
  };

  const forgotPassword = async (email) => {
    const res = await axios.post(`${apiUrl}/forgot-password`, { email });
    return res.data;
  };

  const resetPassword = async (payload) => {
    const res = await axios.post(`${apiUrl}/reset-password`, payload);
    return res.data;
  };

  const logout = () => {
    localStorage.removeItem('infinity_token');
    localStorage.removeItem('infinity_user');
    setCurrentUser(null);
  };

  const token = () => localStorage.getItem('infinity_token');

  const isLoggedIn = () => !!token();

  const isAdmin = () => {
    const roles = currentUser?.roles || [];
    return roles.includes('ROLE_ADMIN');
  };

  const isCustomer = () => {
    return isLoggedIn() && !isAdmin();
  };

  return (
    <AuthContext.Provider value={{
      currentUser,
      login,
      loginWithPhone,
      signup,
      forgotPassword,
      resetPassword,
      logout,
      token,
      isLoggedIn,
      isAdmin,
      isCustomer
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
