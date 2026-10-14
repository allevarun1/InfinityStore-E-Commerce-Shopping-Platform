import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';

export function Admin() {
  const [currentTab, setCurrentTab] = useState('management');
  
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [product, setProduct] = useState({ stockQuantity: 10, deal: false, dealPrice: '', rating: 4.5, categoryId: '' });
  const [category, setCategory] = useState({ name: '', description: '' });
  
  const [searchParams] = useSearchParams();
  const toast = useToast();

  const load = () => {
    setLoading(true);
    Promise.all([
      ApiService.categories(),
      ApiService.adminUsers(),
      ApiService.adminOrders(),
      ApiService.products()
    ])
      .then(([cats, usr, ords, prods]) => {
        setCategories(cats);
        setUsers(usr);
        setOrders(ords);
        setProducts(prods);
        
        const editId = searchParams.get('editId');
        if (editId) {
          const targetProduct = prods.find(p => p.id === Number(editId));
          if (targetProduct) {
            editProduct(targetProduct);
          }
        }
      })
      .catch(() => {
        toast.error('Failed to load administration data');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line
  }, [searchParams]);

  const saveProduct = (e) => {
    e.preventDefault();
    const payload = { ...product, categoryId: Number(product.categoryId) };
    ApiService.saveProduct(payload)
      .then(() => {
        toast.success('Product saved successfully');
        setProduct({ stockQuantity: 10, deal: false, dealPrice: '', rating: 4.5, categoryId: '' });
        load();
      })
      .catch(() => {
        toast.error('Failed to save product');
      });
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setProduct({...product, imageUrl: reader.result});
      };
      reader.readAsDataURL(file);
    }
  };

  const editProduct = (p) => {
    setProduct({ ...p, categoryId: p.category.id });
    setCurrentTab('management');
  };

  const saveCategory = (e) => {
    e.preventDefault();
    ApiService.saveCategory(category)
      .then(() => {
        toast.success('Category saved successfully');
        setCategory({ name: '', description: '' });
        load();
      })
      .catch(() => {
        toast.error('Failed to save category');
      });
  };

  const toggleStock = (p) => {
    ApiService.markStock(p.id, p.stockQuantity === 0)
      .then(() => {
        toast.success('Stock status updated');
        load();
      })
      .catch(() => {
        toast.error('Failed to toggle stock status');
      });
  };

  const deleteProduct = (p) => {
    if (window.confirm(`Are you sure you want to delete ${p.name}?`)) {
      ApiService.deleteProduct(p.id)
        .then(() => {
          toast.success('Product deleted successfully');
          load();
        })
        .catch(() => {
          toast.error('Failed to delete product');
        });
    }
  };

  const deleteUser = (u) => {
    if (window.confirm(`Are you sure you want to delete user ${u.username}?`)) {
      ApiService.deleteUser(u.id)
        .then(() => {
          toast.success('User deleted successfully');
          load();
        })
        .catch(() => {
          toast.error('Failed to delete user');
        });
    }
  };

  // Mirrors the server's payment state machine; the server remains the authority.
  const paymentOptionsFor = (order) => {
    const next = { PENDING: ['PAID', 'FAILED'], PAID: ['REFUNDED'], FAILED: [], REFUNDED: [] }[order.paymentStatus] || [];
    const allowed = next.filter(st => st !== 'PAID' || (order.paymentMethod === 'COD' && order.status !== 'CANCELLED'));
    return [order.paymentStatus, ...allowed];
  };

  const delivery = (order, status) => {
    ApiService.updateDeliveryStatus(order.id, status)
      .then(() => {
        toast.success('Delivery status updated');
        load();
      })
      .catch(() => {
        toast.error('Failed to update delivery status');
      });
  };

  const payment = (order, status) => {
    ApiService.updatePaymentStatus(order.id, status)
      .then(() => {
        toast.success('Payment status updated');
        load();
      })
      .catch(() => {
        toast.error('Failed to update payment status');
      });
  };

  if (loading) {
    return <Loader message="Loading admin board..." />;
  }

  return (
    <>
      <section className="admin-panel-container">
        <h1>Admin Panel</h1>
        
        <nav className="admin-tabs">
          <button className={currentTab === 'management' ? 'active' : ''} onClick={() => setCurrentTab('management')}>
            <i className="bi bi-plus-circle"></i> Management Forms
          </button>
          <button className={currentTab === 'products' ? 'active' : ''} onClick={() => setCurrentTab('products')}>
            <i className="bi bi-box-seam"></i> Products ({products.length})
          </button>
          <button className={currentTab === 'users' ? 'active' : ''} onClick={() => setCurrentTab('users')}>
            <i className="bi bi-people"></i> Users ({users.length})
          </button>
          <button className={currentTab === 'orders' ? 'active' : ''} onClick={() => setCurrentTab('orders')}>
            <i className="bi bi-receipt"></i> Orders ({orders.length})
            {orders.some(o => o.status === 'PENDING') && (
              <span className="admin-new-badge" style={{ backgroundColor: 'var(--danger-color)', color: 'white', padding: '2px 6px', borderRadius: '4px', fontSize: '0.75rem', marginLeft: '6px', fontWeight: 'bold' }}>🔴 New</span>
            )}
          </button>
        </nav>

        <hr className="tab-divider" />

        {currentTab === 'management' && (
          <div className="admin-grid">
            <form onSubmit={saveProduct}>
              <h2>Product management</h2>
              <div className="field-group">
                <label>Product Name</label>
                <input 
                  name="pname" 
                  placeholder="e.g., Wireless Headphones" 
                  required 
                  value={product.name || ''}
                  onChange={e => setProduct({...product, name: e.target.value})}
                />
              </div>
              <div className="field-group">
                <label>Description</label>
                <textarea 
                  name="pdesc" 
                  placeholder="Detailed description..." 
                  required 
                  value={product.description || ''}
                  onChange={e => setProduct({...product, description: e.target.value})}
                />
              </div>
              <div className="field-group">
                <label>Price (₹)</label>
                <input 
                  name="price" 
                  type="number" 
                  placeholder="0.00" 
                  required 
                  value={product.price || ''}
                  onChange={e => setProduct({...product, price: e.target.value})}
                />
              </div>
              <div className="field-group">
                <label>Product Image</label>
                <input 
                  name="image" 
                  type="file"
                  accept="image/*"
                  onChange={handleImageUpload}
                />
                {product.imageUrl && <div style={{marginTop: 8, fontSize: '0.8rem', color: 'var(--success-color)'}}>Image selected</div>}
              </div>
              <div className="field-group">
                <label>Stock Quantity</label>
                <input 
                  name="stock" 
                  type="number" 
                  placeholder="0" 
                  required 
                  value={product.stockQuantity || ''}
                  onChange={e => setProduct({...product, stockQuantity: e.target.value})}
                />
              </div>
              <div className="field-group">
                <label>Category</label>
                <select 
                  name="cat" 
                  required 
                  value={product.categoryId}
                  onChange={e => setProduct({...product, categoryId: e.target.value})}
                >
                  <option value="" disabled>Select category</option>
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>
              <div className="field-group" style={{marginTop: '12px'}}>
                <label>
                  <input 
                    name="deal" 
                    type="checkbox" 
                    checked={product.deal || false}
                    onChange={e => setProduct({...product, deal: e.target.checked})}
                  /> Special Deal Banner
                </label>
              </div>
              {product.deal && (
                <div className="field-group">
                  <label>Deal Price (₹)</label>
                  <input 
                    name="dealPrice" 
                    type="number" 
                    placeholder="Discounted price" 
                    required={product.deal}
                    value={product.dealPrice || ''}
                    onChange={e => setProduct({...product, dealPrice: e.target.value})}
                  />
                </div>
              )}
              <button className="primary" type="submit" style={{marginTop: '8px'}}>Save product</button>
            </form>
            
            <form onSubmit={saveCategory}>
              <h2>Category management</h2>
              <div className="field-group">
                <label>Category Name</label>
                <input 
                  name="cname" 
                  placeholder="e.g., Electronics" 
                  required 
                  value={category.name || ''}
                  onChange={e => setCategory({...category, name: e.target.value})}
                />
              </div>
              <div className="field-group">
                <label>Description</label>
                <input 
                  name="cdesc" 
                  placeholder="Short description..." 
                  value={category.description || ''}
                  onChange={e => setCategory({...category, description: e.target.value})}
                />
              </div>
              <button className="primary" type="submit" style={{marginTop: '16px'}}>Save category</button>
            </form>
          </div>
        )}

        {currentTab === 'products' && (
          <div className="tab-content">
            <h2>Products Directory</h2>
            {products.map(p => (
              <div className="list-row" key={p.id}>
                <div>
                  <strong>{p.name}</strong>
                  <p>{p.category.name} | Stock {p.stockQuantity}</p>
                </div>
                <button onClick={() => editProduct(p)}>Edit</button>
                <button onClick={() => toggleStock(p)}>
                  {p.stockQuantity === 0 ? 'Mark in-stock' : 'Mark out-of-stock'}
                </button>
                <button className="danger-btn" onClick={() => deleteProduct(p)}>Delete</button>
              </div>
            ))}
          </div>
        )}

        {currentTab === 'users' && (
          <div className="tab-content">
            <h2>Registered Users</h2>
            {users.map(u => (
              <div className="list-row" key={u.id}>
                <div>
                  <strong>{u.username}</strong>
                  <p>{u.email}</p>
                </div>
                <button className="danger-btn" onClick={() => deleteUser(u)}>Delete</button>
              </div>
            ))}
          </div>
        )}

        {currentTab === 'orders' && (
          <div className="tab-content">
            <h2>Customer Orders</h2>
            {orders.map(order => (
              <div className="order-card" key={order.id} style={{ flexWrap: 'wrap', borderLeft: order.status === 'PENDING' ? '4px solid var(--danger-color)' : 'none', paddingLeft: order.status === 'PENDING' ? '12px' : '' }}>
                <div className="row-line" style={{ width: '100%', marginBottom: '12px', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <strong>Order ID: {order.trackingNumber}</strong>
                    {order.status === 'PENDING' && (
                      <span style={{ backgroundColor: 'var(--danger-color)', color: 'white', padding: '1px 6px', borderRadius: '4px', fontSize: '0.7rem', fontWeight: 'bold' }}>NEW ORDER</span>
                    )}
                  </div>
                  <span>₹{Number(order.totalPrice).toLocaleString('en-IN', { minimumFractionDigits: 2 })}{order.testPayment ? ' (TEST PAYMENT)' : ''}</span>
                </div>

                <div style={{ width: '100%', fontSize: '0.85rem', color: 'var(--text-color)', marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <div><strong>Customer:</strong> {order.user ? `${order.user.firstName} ${order.user.lastName} (@${order.user.username})` : 'Unknown'}</div>
                  <div><strong>Phone:</strong> {order.phoneNumber || 'N/A'}</div>
                  <div><strong>Shipping Address:</strong> {order.shippingAddress}</div>
                  <div><strong>Payment Method:</strong> {order.paymentMethod} ({order.paymentStatus})</div>
                  <div style={{ marginTop: '8px', padding: '8px', backgroundColor: 'rgba(0,0,0,0.03)', borderRadius: '4px' }}>
                    <strong style={{ display: 'block', marginBottom: '4px' }}>Items Ordered:</strong>
                    <ul style={{ margin: 0, paddingLeft: '16px' }}>
                      {order.orderItems?.map(item => (
                        <li key={item.id}>
                          {item.product?.name || 'Deleted Product'} (x{item.quantity}) - ₹{Number(item.price).toLocaleString('en-IN')}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                <div className="field-group" style={{ flex: 1, minWidth: '150px' }}>
                  <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Order Status</label>
                  <select 
                    value={order.status} 
                    onChange={e => delivery(order, e.target.value)}
                  >
                    {[order.status, ...(order.nextStatuses || [])].map(st => <option key={st}>{st}</option>)}
                  </select>
                </div>
                <div className="field-group" style={{ flex: 1, minWidth: '150px' }}>
                  <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Payment Status</label>
                  <select 
                    value={order.paymentStatus} 
                    onChange={e => payment(order, e.target.value)}
                  >
                    {paymentOptionsFor(order).map(st => <option key={st}>{st}</option>)}
                  </select>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
