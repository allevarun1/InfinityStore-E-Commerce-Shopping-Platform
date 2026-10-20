import { useNavigate, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useStore } from '../contexts/StoreContext';
import { useToast } from '../contexts/ToastContext';

/** Save / unsave a product. Visitors are sent to sign in and brought back to the page they were on. */
export function useWishlist() {
  const { isLoggedIn } = useAuth();
  const { wishIds, setWishIds } = useStore();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const isWished = (id) => wishIds.includes(id);

  const toggle = async (product) => {
    if (!isLoggedIn()) {
      toast.info('Sign in to save sarees to your wishlist');
      navigate('/login', { state: { from: location } });
      return;
    }
    const saved = isWished(product.id);
    // optimistic: the heart responds instantly, and rolls back if the server refuses
    setWishIds(ids => (saved ? ids.filter(x => x !== product.id) : [...ids, product.id]));
    try {
      if (saved) await ApiService.removeWishlist(product.id);
      else await ApiService.addWishlist(product.id);
      toast.success(saved ? 'Removed from your wishlist' : 'Saved to your wishlist');
    } catch (err) {
      setWishIds(ids => (saved ? [...ids, product.id] : ids.filter(x => x !== product.id)));
      toast.error(err.response?.data?.message || 'Could not update your wishlist');
    }
  };

  return { isWished, toggle };
}
