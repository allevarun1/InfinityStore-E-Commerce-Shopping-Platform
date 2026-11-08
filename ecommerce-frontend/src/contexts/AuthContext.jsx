import React, { createContext, useContext, useEffect, useState } from 'react';
import axios from 'axios';
import { ApiService, withWakeRetry } from '../services/api';
import { guestCart } from '../lib/guestCart';
import { warmGoogle } from '../lib/quickLogin';

// Sessions renew themselves: a returning visitor swaps the token for a fresh one once it is half a day old.
const RENEW_AFTER_MS = 12 * 60 * 60 * 1000;

function tokenIssuedAt(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.iat ? payload.iat * 1000 : 0;
  } catch { return 0; }
}

const whenIdle = (fn) => (window.requestIdleCallback ? window.requestIdleCallback(fn, { timeout: 4000 }) : setTimeout(fn, 2500));

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

  useEffect(() => {
    const renew = () => {
      const current = localStorage.getItem('infinity_token');
      if (!current) return;
      if (Date.now() - tokenIssuedAt(current) < RENEW_AFTER_MS) return;
      ApiService.refreshSession().then((res) => {
        if (localStorage.getItem('infinity_token') !== current) return; // signed out or in again meanwhile
        localStorage.setItem('infinity_token', res.token || res.accessToken);
        localStorage.setItem('infinity_user', JSON.stringify(res));
        setCurrentUser(res);
      }).catch(() => {}); // asleep or offline: try again on the next visit
    };
    whenIdle(renew);
    const onVisible = () => { if (document.visibilityState === 'visible') renew(); };
    document.addEventListener('visibilitychange', onVisible);
    // Signed-out visitors get Google's sign-in script early, so the button appears instantly on the login page.
    if (!localStorage.getItem('infinity_token')) whenIdle(warmGoogle);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, []);

  // Same origin as the rest of the API; set VITE_API_URL when deploying.
  const apiUrl = `${import.meta.env.VITE_API_URL || 'http://localhost:8080/api'}/auth`;

  const startSession = async (response) => {
    localStorage.setItem('infinity_token', response.token || response.accessToken);
    localStorage.setItem('infinity_user', JSON.stringify(response));
    setCurrentUser(response);
    // Carry the guest basket over in the background: the shopper should not wait for it to reach the sign-in result.
    mergeGuestCart(response).catch(() => {});
    return response;
  };

  const login = async (username, password) => {
    const res = await withWakeRetry(() => axios.post(`${apiUrl}/signin`, { username, password }));
    return startSession(res.data);
  };

  /** Swaps the ID token Firebase gives after a correct OTP for our own session. */
  const loginWithPhone = async (idToken) => {
    const res = await withWakeRetry(() => axios.post(`${apiUrl}/phone`, { idToken }));
    return startSession(res.data);
  };

  /** Continue with Google: swaps Google's ID token for our own session. */
  const loginWithGoogle = async (credential) => {
    const res = await withWakeRetry(() => axios.post(`${apiUrl}/google`, { credential }));
    return startSession(res.data);
  };

  const sendWhatsAppOtp = (phone) => axios.post(`${apiUrl}/otp/send`, { phone });

  const loginWithWhatsAppOtp = async (phone, code) => {
    const res = await axios.post(`${apiUrl}/otp/verify`, { phone, code });
    return startSession(res.data);
  };

  /** Truecaller one-tap: start a request, then ask until Truecaller has vouched for the number. */
  const startTruecaller = async () => (await axios.post(`${apiUrl}/truecaller/start`)).data.nonce;

  const pollTruecaller = async (nonce) => {
    const res = await axios.get(`${apiUrl}/truecaller/status/${encodeURIComponent(nonce)}`);
    if (res.data.status === 'VERIFIED') await startSession(res.data.session);
    return res.data.status;
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
      loginWithGoogle,
      sendWhatsAppOtp,
      loginWithWhatsAppOtp,
      startTruecaller,
      pollTruecaller,
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
