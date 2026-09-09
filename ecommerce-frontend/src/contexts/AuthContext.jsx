import React, { createContext, useContext, useState } from 'react';
import axios from 'axios';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(() => {
    const user = localStorage.getItem('snapbuy_user');
    return user ? JSON.parse(user) : null;
  });

  // Same origin as the rest of the API; set VITE_API_URL when deploying.
  const apiUrl = `${import.meta.env.VITE_API_URL || 'http://localhost:8080/api'}/auth`;

  const login = async (username, password) => {
    const res = await axios.post(`${apiUrl}/signin`, { username, password });
    const response = res.data;
    localStorage.setItem('snapbuy_token', response.token || response.accessToken);
    localStorage.setItem('snapbuy_user', JSON.stringify(response));
    setCurrentUser(response);
    return response;
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
    localStorage.removeItem('snapbuy_token');
    localStorage.removeItem('snapbuy_user');
    setCurrentUser(null);
  };

  const token = () => localStorage.getItem('snapbuy_token');

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
