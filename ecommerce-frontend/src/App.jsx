import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Link, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider } from './contexts/ToastContext';
import { Home } from './components/Home';
import { ProductDetail } from './components/ProductDetail';
import { Login } from './components/Login';
import { Register } from './components/Register';
import { Cart } from './components/Cart';
import { Wishlist } from './components/Wishlist';
import { Checkout } from './components/Checkout';
import { Orders } from './components/Orders';
import { Profile } from './components/Profile';
import { Admin } from './components/Admin';
import { StaticPage } from './components/StaticPage';
import { ResetPassword } from './components/ResetPassword';
import { ApiService } from './services/api';
import './styles.css';
import './App.css';
import 'bootstrap-icons/font/bootstrap-icons.css';

function Header() {
  const [searchTerm, setSearchTerm] = useState('');
  const [hasNewOrders, setHasNewOrders] = useState(false);
  const { isCustomer, isAdmin, isLoggedIn, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!isAdmin()) {
      setHasNewOrders(false);
      return;
    }
    
    const checkOrders = () => {
      ApiService.adminOrders()
        .then(orders => {
          setHasNewOrders(orders.some(o => o.status === 'PENDING'));
        })
        .catch(() => {});
    };

    checkOrders();
    const interval = setInterval(checkOrders, 10000); // check every 10 seconds
    return () => clearInterval(interval);
  }, [isAdmin]);

  const searchProducts = (e) => {
    e.preventDefault();
    if (searchTerm.trim()) {
      navigate(`/?q=${encodeURIComponent(searchTerm)}#products`);
    } else {
      navigate(`/#products`);
    }
  };

  const goToProducts = () => {
    setSearchTerm('');
    navigate(`/#products`);
  };

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="site-header">
      <Link className="brand" to="/">
        <span><i className="bi bi-shop"></i></span>
        InfinityStore
      </Link>
      <form className="header-search" onSubmit={searchProducts}>
        <i className="bi bi-search"></i>
        <input 
          value={searchTerm}
          onChange={e => setSearchTerm(e.target.value)}
          placeholder="Search products"
        />
      </form>
      <nav>
        <button type="button" onClick={goToProducts}>Products</button>
        {isCustomer() && <Link to="/cart">Cart</Link>}
        {isCustomer() && <Link to="/wishlist">Wishlist</Link>}
        {isCustomer() && <Link to="/orders">Orders</Link>}
        {isCustomer() && <Link to="/profile"><i className="bi bi-person-circle"></i></Link>}
        {isAdmin() && (
          <Link to="/admin" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            Admin
            {hasNewOrders && (
              <span 
                className="admin-header-bubble" 
                style={{ 
                  width: '8px', 
                  height: '8px', 
                  backgroundColor: 'var(--danger-color)', 
                  borderRadius: '50%', 
                  display: 'inline-block' 
                }}
                title="New Orders Received!"
              ></span>
            )}
          </Link>
        )}
        {!isLoggedIn() && <Link to="/login">Login</Link>}
        {isLoggedIn() && <button type="button" onClick={handleLogout}>Logout</button>}
      </nav>
    </header>
  );
}

function Footer() {
  return (
    <footer className="footer">
      <div>
        <strong>© 2026 InfinityStore. All rights reserved.</strong>
        <p>Built with ❤️ for better shopping.</p>
      </div>
      <div className="footer-links">
        <a href="mailto:snapbuy1111@gmail.com">Email Us</a>
        <Link to="/privacy">Privacy Policy</Link>
        <Link to="/terms">Terms</Link>
      </div>
    </footer>
  );
}

function ProtectedRoute({ children, role }) {
  const { isLoggedIn, isAdmin, isCustomer } = useAuth();
  const location = useLocation();
  
  if (!isLoggedIn()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  
  if (role === 'ADMIN' && !isAdmin()) {
    return <Navigate to="/" replace />;
  }
  
  if (role === 'USER' && !isCustomer()) {
    return <Navigate to="/" replace />;
  }
  
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
          <Header />
          <main className="page">
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/products/:id" element={<ProductDetail />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/reset-password" element={<ResetPassword />} />
              
              <Route path="/cart" element={<ProtectedRoute role="USER"><Cart /></ProtectedRoute>} />
              <Route path="/wishlist" element={<ProtectedRoute role="USER"><Wishlist /></ProtectedRoute>} />
              <Route path="/checkout" element={<ProtectedRoute role="USER"><Checkout /></ProtectedRoute>} />
              <Route path="/orders" element={<ProtectedRoute role="USER"><Orders /></ProtectedRoute>} />
              <Route path="/profile" element={<ProtectedRoute role="USER"><Profile /></ProtectedRoute>} />
              
              <Route path="/admin" element={<ProtectedRoute role="ADMIN"><Admin /></ProtectedRoute>} />
              
              <Route path="/privacy" element={<StaticPage page="privacy" />} />
              <Route path="/terms" element={<StaticPage page="terms" />} />
              
              <Route path="*" element={<Home />} />
            </Routes>
          </main>
          <Footer />
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
