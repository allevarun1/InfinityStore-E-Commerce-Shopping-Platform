import { useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { useStore } from '../contexts/StoreContext';
import { guestCart } from './guestCart';
import { track } from './analytics';

/**
 * One place that adds to the cart for both visitors (browser cart) and signed-in customers (server
 * cart), so every "Add to cart" button behaves the same. Resolves true when the item was added.
 */
export function useCartActions() {
  const { isLoggedIn } = useAuth();
  const { refreshCart, showAdded } = useStore();
  const toast = useToast();
  const navigate = useNavigate();

  const addToCart = async (product, quantity = 1, { quiet = false } = {}) => {
    if (!isLoggedIn()) {
      guestCart.add(product.id, quantity, product.stockQuantity || 0);
      track.addToCart(product, quantity);
      refreshCart();
      if (!quiet) showAdded(product, quantity);
      return true;
    }
    try {
      await ApiService.addToCart(product.id, quantity);
      track.addToCart(product, quantity);
      refreshCart();
      if (!quiet) showAdded(product, quantity);
      return true;
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not add this item');
      return false;
    }
  };

  const goToCheckout = () => {
    if (isLoggedIn()) navigate('/checkout');
    else navigate('/login', { state: { from: { pathname: '/checkout' } } });
  };

  return { addToCart, goToCheckout };
}
