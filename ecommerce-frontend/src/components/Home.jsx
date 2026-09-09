import React, { useEffect, useState } from 'react';
import { useSearchParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';
import { EmptyState } from './EmptyState';
import { StarRating } from './StarRating';

export function Home() {
  const [products, setProducts] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [categories, setCategories] = useState([]);
  const [masterProductCatalog, setMasterProductCatalog] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [categoryId, setCategoryId] = useState('');
  const [sort, setSort] = useState('name');
  const [searchParams] = useSearchParams();
  const query = searchParams.get('q') || '';

  const { isCustomer, isAdmin, isLoggedIn } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    ApiService.categories().then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    ApiService.products()
      .then((data) => {
        setMasterProductCatalog(data);
        setProducts(data);
        if (!query) {
          setCategoryId('');
          setSort('name');
        }
      })
      .catch((err) => {
        toast.error('Failed to load products');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [searchParams, query, toast]);

  useEffect(() => {
    if (location.hash === '#products') {
      setTimeout(() => {
        document.getElementById('products')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 100);
    }
  }, [location]);

  useEffect(() => {
    let term = query.toLowerCase().trim();
    let result = products
      .filter((p) => !term || p.name.toLowerCase().includes(term) || p.description.toLowerCase().includes(term))
      .filter((p) => !categoryId || p.category.id === Number(categoryId))
      .sort((a, b) => sort === 'low' ? a.price - b.price : sort === 'high' ? b.price - a.price : a.name.localeCompare(b.name));
    setFiltered(result);
  }, [products, query, categoryId, sort]);

  const handleCategoryChange = (e) => {
    const val = e.target.value;
    setCategoryId(val);
    if (products.length !== masterProductCatalog.length && masterProductCatalog.length > 0) {
      setProducts([...masterProductCatalog]);
    }
  };

  const handleSortChange = (e) => {
    const val = e.target.value;
    setSort(val);
    if (products.length !== masterProductCatalog.length && masterProductCatalog.length > 0) {
      setProducts([...masterProductCatalog]);
    }
  };

  const loadDeals = () => {
    setLoading(true);
    ApiService.deals()
      .then((data) => {
        setProducts(data);
        setCategoryId('');
        navigate('?');
        setTimeout(() => {
          document.getElementById('products')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 100);
      })
      .catch(() => {
        toast.error('Failed to load deals');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const addToCart = (product) => {
    if (!isLoggedIn()) {
      navigate('/login');
      return;
    }
    ApiService.addToCart(product.id).then(() => {
      toast.success('Product added to cart');
    }).catch(err => {
      toast.error(err.response?.data?.message || 'Could not add item');
    });
  };

  const wishlist = (product) => {
    if (!isLoggedIn()) {
      navigate('/login');
      return;
    }
    ApiService.addWishlist(product.id)
      .then(() => toast.success('Added to wishlist'))
      .catch(err => toast.error(err.response?.data?.message || 'Could not add to wishlist'));
  };

  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">InfinityStore deals</p>
          <h1>Handwoven sarees, woven to be worn for years.</h1>
          <p>Explore Kanchipuram silks, Bengal handlooms, Kerala kasavu and contemporary designer drapes, sourced directly from weavers.</p>
          <button className="primary" onClick={loadDeals}>View deals</button>
        </div>
        <img src="/images/sarees/hero-sarees.jpg" alt="Handwoven saree collection" />
      </section>

      <section id="products" className="toolbar">
        <select value={categoryId} onChange={handleCategoryChange}>
          <option value="">All categories</option>
          {categories.map(c => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <select value={sort} onChange={handleSortChange}>
          <option value="name">Sort by name</option>
          <option value="low">Price low to high</option>
          <option value="high">Price high to low</option>
        </select>
        {!loading && filtered.length > 0 && (
          <div className="catalog-stats">
            Showing {filtered.length} of {products.length} products
          </div>
        )}
      </section>

      {loading ? (
        <Loader message="Loading catalog..." />
      ) : filtered.length === 0 ? (
        <EmptyState 
          icon="bi-search" 
          title="No Products Found" 
          message="We couldn't find any products matching your criteria."
          actionText="Clear Filters"
          onAction={() => {
            setCategoryId('');
            setSort('name');
            navigate('/');
          }}
        />
      ) : (
        <section className="grid">
          {filtered.map(product => (
            <article className="product-card" key={product.id}>
              <div className="deal-badge-container">
                {product.deal && <span className="deal-badge">Deal</span>}
                <Link to={`/products/${product.id}`}><img src={product.imageUrl} alt={product.name} /></Link>
              </div>
              <div className="product-body">
                <span className="tag">{product.category.name}</span>
                <h3>{product.name}</h3>
                <div style={{ marginBottom: '8px' }}>
                  <StarRating rating={product.rating} />
                </div>
                <p>{product.description}</p>
                <div className="row-line" style={{ flexWrap: 'wrap' }}>
                  {product.deal && product.dealPrice ? (
                    <div className="deal-price-block">
                      <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
                        <span className="deal-current-price">₹{product.dealPrice.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                        <span className="deal-original-price">₹{product.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                        <span className="deal-percent-off">{Math.round(((product.price - product.dealPrice) / product.price) * 100)}% off</span>
                      </div>
                    </div>
                  ) : (
                    <strong style={{ fontSize: '1.15rem', color: 'var(--primary-color)' }}>₹{product.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong>
                  )}
                  <span className={product.stockQuantity === 0 ? 'danger' : ''} style={{ fontSize: '0.85rem' }}>
                    {product.stockQuantity > 0 ? `${product.stockQuantity} in stock` : 'Out of stock'}
                  </span>
                </div>
                {isCustomer() && (
                  <div className="actions">
                    <button onClick={() => addToCart(product)} disabled={product.stockQuantity === 0}>
                      <i className="bi bi-cart2"></i>
                    </button>
                    <button className="ghost" onClick={() => wishlist(product)}>
                      <i className="bi bi-heart"></i>
                    </button>
                  </div>
                )}
                {isAdmin() && (
                  <Link className="primary link-button" to={`/admin?editId=${product.id}`}>Manage in admin</Link>
                )}
              </div>
            </article>
          ))}
        </section>
      )}
    </>
  );
}
