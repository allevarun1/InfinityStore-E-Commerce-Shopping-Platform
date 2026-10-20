import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Link, NavLink, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider } from './contexts/ToastContext';
import { StoreProvider, useStore } from './contexts/StoreContext';
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
import { NotFound } from './components/NotFound';
import { WhatsAppFab, Newsletter, ConsentBanner } from './components/Growth';
import { pageView } from './lib/analytics';
import { SearchBox, CategoryNav, BottomNav } from './components/Navigation';
import { MiniCart } from './components/MiniCart';
import { ApiService } from './services/api';
import { inrCompact } from './lib/format';
import './styles.css';
import './App.css';
import './theme.css';
import './ux.css';
import 'bootstrap-icons/font/bootstrap-icons.css';

function AnnouncementBar() {
  const { info } = useStore();
  if (!info) return null;
  return (
    <div className="announce">
      <span>Free shipping above {inrCompact(info.freeShippingThreshold)}</span>
      <span>Cash on delivery available</span>
      {info.welcomeOffer && <span>New here? Use code <strong>{info.welcomeOffer.code}</strong> for {info.welcomeOffer.summary.split(' on orders')[0]}</span>}
    </div>
  );
}

function Header() {
  const [hasNewOrders, setHasNewOrders] = useState(false);
  const { isCustomer, isAdmin, isLoggedIn, logout } = useAuth();
  const { cartCount, cartPulse } = useStore();
  const navigate = useNavigate();
  const admin = isAdmin();

  useEffect(() => {
    if (!admin) {
      setHasNewOrders(false);
      return;
    }
    const checkOrders = () => {
      ApiService.adminOrders()
        .then(orders => setHasNewOrders(orders.some(o => o.status === 'PENDING')))
        .catch(() => {});
    };
    checkOrders();
    const interval = setInterval(checkOrders, 30000);
    return () => clearInterval(interval);
  }, [admin]);

  return (
    <header className="site-header">
      <div className="header-inner">
        <Link className="brand" to="/" aria-label="InfinityStore home">
          <span className="brand-mark" aria-hidden="true">∞</span>
          <span className="brand-name">InfinityStore</span>
        </Link>

        <SearchBox />

        <nav className="header-actions" aria-label="Account and cart">
          {!admin && !isLoggedIn() && (
            <NavLink to="/cart" aria-label={`Cart, ${cartCount} ${cartCount === 1 ? 'item' : 'items'}`}>
              <i key={cartPulse} className={`bi bi-bag ${cartPulse ? 'bump' : ''}`} aria-hidden="true"></i><span className="label">Cart</span>
              {cartCount > 0 && <span className="badge-count" aria-hidden="true">{cartCount > 99 ? '99+' : cartCount}</span>}
            </NavLink>
          )}
          {isCustomer() && (
            <>
              <NavLink to="/wishlist" aria-label="Wishlist"><i className="bi bi-heart" aria-hidden="true"></i><span className="label">Wishlist</span></NavLink>
              <NavLink to="/orders" aria-label="Orders" className="hide-sm"><i className="bi bi-box-seam" aria-hidden="true"></i><span className="label">Orders</span></NavLink>
              <NavLink to="/profile" aria-label="Account"><i className="bi bi-person" aria-hidden="true"></i><span className="label">Account</span></NavLink>
              <NavLink to="/cart" aria-label={`Cart, ${cartCount} ${cartCount === 1 ? 'item' : 'items'}`}>
                <i key={cartPulse} className={`bi bi-bag ${cartPulse ? 'bump' : ''}`} aria-hidden="true"></i><span className="label">Cart</span>
                {cartCount > 0 && <span className="badge-count" aria-hidden="true">{cartCount > 99 ? '99+' : cartCount}</span>}
              </NavLink>
            </>
          )}
          {admin && (
            <NavLink to="/admin" aria-label={hasNewOrders ? 'Admin, new orders waiting' : 'Admin'}>
              <i className="bi bi-speedometer2" aria-hidden="true"></i><span className="label">Admin</span>
              {hasNewOrders && <span className="dot-alert" aria-hidden="true"></span>}
            </NavLink>
          )}
          {isLoggedIn() ? (
            <button type="button" className={isCustomer() ? 'hide-sm' : ''} onClick={() => { logout(); navigate('/'); }} aria-label="Sign out">
              <i className="bi bi-box-arrow-right" aria-hidden="true"></i><span className="label">Sign out</span>
            </button>
          ) : (
            <NavLink to="/login"><i className="bi bi-person" aria-hidden="true"></i><span className="label">Sign in</span></NavLink>
          )}
        </nav>
      </div>
      <CategoryNav />
    </header>
  );
}

function Footer() {
  const { info } = useStore();
  const support = import.meta.env.VITE_SUPPORT_EMAIL;
  return (
    <footer className="footer">
      <Newsletter />
      <div className="footer-inner">
        <div>
          <Link className="brand" to="/"><span className="brand-mark" aria-hidden="true">∞</span>InfinityStore</Link>
          <p>Handwoven silk, cotton, designer and bridal sarees, sourced directly from weavers.</p>
        </div>
        <div>
          <h3>Shop</h3>
          <ul>
            <li><Link to="/#products">All sarees</Link></li>
            <li><Link to="/?view=deals#products">Deals</Link></li>
          </ul>
        </div>
        <div>
          <h3>Help</h3>
          <ul>
            <li><Link to="/orders">Your orders</Link></li>
            <li><Link to="/shipping">Shipping &amp; returns</Link></li>
            <li><Link to="/contact">Contact us</Link></li>
            <li><Link to="/privacy">Privacy policy</Link></li>
            <li><Link to="/terms">Terms</Link></li>
            {(info?.supportEmail || support) && <li><a href={`mailto:${info?.supportEmail || support}`}>Email us</a></li>}
          </ul>
        </div>
        <div>
          <h3>Delivery &amp; payment</h3>
          <ul>
            {info && <li>Free shipping above {inrCompact(info.freeShippingThreshold)}</li>}
            <li>Cash on delivery</li>
            <li>GST itemised on every order</li>
          </ul>
        </div>
      </div>
      <div className="footer-base">
        <span>© {new Date().getFullYear()} InfinityStore. All rights reserved.</span>
        <span>Prices in Indian rupees (₹).</span>
      </div>
    </footer>
  );
}

/** New pages start at the top unless the link targets a section. */
function ScrollToTop() {
  const { pathname, hash } = useLocation();
  useEffect(() => { pageView(); }, [pathname]);
  useEffect(() => {
    if (!hash) window.scrollTo(0, 0);
  }, [pathname, hash]);
  return null;
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

/** Visitors may keep a cart; signed-in staff are sent away from the customer cart. */
function CartRoute() {
  const { isLoggedIn } = useAuth();
  return isLoggedIn() ? <ProtectedRoute role="USER"><Cart /></ProtectedRoute> : <Cart />;
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <StoreProvider>
          <BrowserRouter>
            <a className="skip-link" href="#main">Skip to content</a>
            <ScrollToTop />
            <AnnouncementBar />
            <Header />
            <main id="main" className="page" tabIndex={-1}>
              <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/products/:id" element={<ProductDetail />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route path="/reset-password" element={<ResetPassword />} />
                <Route path="/cart" element={<CartRoute />} />
                <Route path="/wishlist" element={<ProtectedRoute role="USER"><Wishlist /></ProtectedRoute>} />
                <Route path="/checkout" element={<ProtectedRoute role="USER"><Checkout /></ProtectedRoute>} />
                <Route path="/orders" element={<ProtectedRoute role="USER"><Orders /></ProtectedRoute>} />
                <Route path="/profile" element={<ProtectedRoute role="USER"><Profile /></ProtectedRoute>} />
                <Route path="/admin" element={<ProtectedRoute role="ADMIN"><Admin /></ProtectedRoute>} />
                <Route path="/privacy" element={<StaticPage page="privacy" />} />
                <Route path="/shipping" element={<StaticPage page="shipping" />} />
                <Route path="/contact" element={<StaticPage page="contact" />} />
                <Route path="/terms" element={<StaticPage page="terms" />} />
                <Route path="*" element={<NotFound />} />
              </Routes>
            </main>
            <Footer />
            <WhatsAppFab />
            <MiniCart />
            <BottomNav />
            <ConsentBanner />
          </BrowserRouter>
        </StoreProvider>
      </ToastProvider>
    </AuthProvider>
  );
}
