import React, { useEffect, useState } from 'react';
import { imageSrc } from '../lib/format';
import { useSearchParams } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';

const BLANK_PRODUCT = { stockQuantity: 10, deal: false, dealPrice: '', categoryId: '', status: 'DRAFT', source: 'MANUAL' };

const STATUS_LABEL = { DRAFT: 'Draft', READY_FOR_REVIEW: 'Ready for review', APPROVED: 'Approved', PUBLISHED: 'Published' };

/** Lifecycle buttons for a product. The server enforces the same rules and is the authority. */
function nextSteps(p) {
  const manual = p.source !== 'AI_ASSISTED';
  switch (p.status) {
    case 'DRAFT': return [['READY_FOR_REVIEW', 'Send for review'], ...(manual ? [['PUBLISHED', 'Publish']] : [])];
    case 'READY_FOR_REVIEW': return [['APPROVED', 'Approve'], ...(manual ? [['PUBLISHED', 'Publish']] : []), ['DRAFT', 'Back to draft']];
    case 'APPROVED': return [['PUBLISHED', 'Publish'], ['DRAFT', 'Back to draft']];
    case 'PUBLISHED': return [['DRAFT', 'Unpublish']];
    default: return [];
  }
}

const margin = (p) => {
  const sell = p.deal && p.dealPrice ? Number(p.dealPrice) : Number(p.price);
  if (!p.costPrice || !sell) return null;
  return ((sell - Number(p.costPrice)) / sell) * 100;
};

export function Admin() {
  const [currentTab, setCurrentTab] = useState('management');
  
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [product, setProduct] = useState(BLANK_PRODUCT);
  const [category, setCategory] = useState({ name: '', description: '' });
  
  const [searchParams] = useSearchParams();
  const toast = useToast();

  const load = () => {
    setLoading(true);
    Promise.all([
      ApiService.categories(),
      ApiService.adminUsers(),
      ApiService.adminOrders(),
      ApiService.adminProducts()
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

  const saveProduct = (e, publish = false) => {
    e.preventDefault();
    const numeric = (v) => (v === '' || v === undefined || v === null ? null : Number(v));
    const { previewImage, readinessIssues, marginPercent, category, ...rest } = product; // eslint-disable-line no-unused-vars
    const payload = {
      ...rest,
      categoryId: Number(product.categoryId),
      price: numeric(product.price),
      dealPrice: product.deal ? numeric(product.dealPrice) : null,
      stockQuantity: numeric(product.stockQuantity),
      costPrice: numeric(product.costPrice),
      lengthMeters: numeric(product.lengthMeters),
      gstPercent: numeric(product.gstPercent),
      status: !product.id && publish ? 'PUBLISHED' : 'DRAFT',
    };
    ApiService.saveProduct(payload)
      .then(() => {
        toast.success(publish && !product.id ? 'Product published' : 'Product saved');
        setProduct(BLANK_PRODUCT);
        load();
      })
      .catch(err => toast.error(err.response?.data?.message || 'Failed to save product'));
  };

  const step = (p, to) => {
    ApiService.setProductStatus(p.id, to)
      .then(() => {
        toast.success(`${p.name}: ${STATUS_LABEL[to].toLowerCase()}`);
        load();
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not change the status'));
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setProduct({...product, imageUrl: reader.result, previewImage: null});
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
            <i className="bi bi-plus-circle"></i> New product / category
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
              <h2>{product.id ? 'Edit product' : 'New product'}</h2>
              {product.id && (
                <p className="admin-hint">
                  Status: <strong>{STATUS_LABEL[product.status] || product.status}</strong>. Use the Products tab to publish or unpublish.
                  {' '}<button type="button" className="linklike" onClick={() => setProduct(BLANK_PRODUCT)}>Start a new product instead</button>
                </p>
              )}
              <div className="field-group">
                <label htmlFor="pname">Product name</label>
                <input id="pname" name="pname" placeholder="e.g., Kanchipuram Pure Silk Saree" required
                  value={product.name || ''} onChange={e => setProduct({...product, name: e.target.value})} />
              </div>
              <div className="field-group">
                <label htmlFor="pdesc">Description <span className="admin-hint">(at least 30 characters)</span></label>
                <textarea id="pdesc" name="pdesc" rows="4" placeholder="Fabric, weave, border, pallu, what is included…" required
                  value={product.description || ''} onChange={e => setProduct({...product, description: e.target.value})} />
              </div>
              <div className="field-group">
                <label htmlFor="price">Price (₹, before GST)</label>
                <input id="price" name="price" type="number" step="0.01" min="0.01" placeholder="0.00" required
                  value={product.price ?? ''} onChange={e => setProduct({...product, price: e.target.value})} />
              </div>
              <div className="field-group">
                <label htmlFor="image">Product image</label>
                <input id="image" name="image" type="file" accept="image/png,image/jpeg,image/webp,image/gif" onChange={handleImageUpload} />
                {(product.previewImage || product.imageUrl) && (
                  <img className="admin-preview" src={product.previewImage || imageSrc(product.imageUrl)} alt="Product preview" />
                )}
              </div>
              <div className="field-group">
                <label htmlFor="stock">Stock quantity</label>
                <input id="stock" name="stock" type="number" min="0" placeholder="0" required
                  value={product.stockQuantity ?? ''} onChange={e => setProduct({...product, stockQuantity: e.target.value})} />
              </div>
              <div className="field-group">
                <label htmlFor="cat">Category</label>
                <select id="cat" name="cat" required value={product.categoryId} onChange={e => setProduct({...product, categoryId: e.target.value})}>
                  <option value="" disabled>Select category</option>
                  {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>

              <fieldset className="admin-fieldset">
                <legend>Saree details (shown to customers)</legend>
                <div className="admin-two">
                  <div className="field-group"><label htmlFor="fabric">Fabric</label>
                    <input id="fabric" list="fabrics" value={product.fabric || ''} onChange={e => setProduct({...product, fabric: e.target.value})} />
                    <datalist id="fabrics"><option>Silk</option><option>Cotton</option><option>Silk cotton</option><option>Georgette</option><option>Organza</option><option>Linen</option></datalist></div>
                  <div className="field-group"><label htmlFor="weave">Weave</label>
                    <input id="weave" placeholder="Kanchipuram, Banarasi…" value={product.weave || ''} onChange={e => setProduct({...product, weave: e.target.value})} /></div>
                  <div className="field-group"><label htmlFor="occasion">Occasion</label>
                    <input id="occasion" list="occasions" value={product.occasion || ''} onChange={e => setProduct({...product, occasion: e.target.value})} />
                    <datalist id="occasions"><option>Wedding</option><option>Festive</option><option>Party</option><option>Everyday</option><option>Office</option></datalist></div>
                  <div className="field-group"><label htmlFor="colour">Colour</label>
                    <input id="colour" value={product.colour || ''} onChange={e => setProduct({...product, colour: e.target.value})} /></div>
                  <div className="field-group"><label htmlFor="region">Region of origin</label>
                    <input id="region" placeholder="Tamil Nadu" value={product.region || ''} onChange={e => setProduct({...product, region: e.target.value})} /></div>
                  <div className="field-group"><label htmlFor="len">Length (metres)</label>
                    <input id="len" type="number" step="0.01" min="0" max="20" value={product.lengthMeters ?? ''} onChange={e => setProduct({...product, lengthMeters: e.target.value})} /></div>
                </div>
                <label className="admin-check"><input type="checkbox" checked={!!product.blouseIncluded} onChange={e => setProduct({...product, blouseIncluded: e.target.checked})} /> Blouse piece included</label>
                <div className="field-group"><label htmlFor="care">Care instructions</label>
                  <input id="care" maxLength="500" placeholder="Dry clean only" value={product.careInstructions || ''} onChange={e => setProduct({...product, careInstructions: e.target.value})} /></div>
              </fieldset>

              <fieldset className="admin-fieldset">
                <legend>Business details (private, never shown to customers)</legend>
                <div className="admin-two">
                  <div className="field-group"><label htmlFor="cost">Your cost per piece (₹)</label>
                    <input id="cost" type="number" step="0.01" min="0" value={product.costPrice ?? ''} onChange={e => setProduct({...product, costPrice: e.target.value})} /></div>
                  <div className="field-group"><label htmlFor="supplier">Supplier</label>
                    <input id="supplier" maxLength="120" value={product.supplierName || ''} onChange={e => setProduct({...product, supplierName: e.target.value})} /></div>
                </div>
                {margin(product) !== null && (
                  <p className={`admin-hint ${margin(product) < 0 ? 'admin-warn' : ''}`}>
                    Gross margin before GST and shipping: <strong>{margin(product).toFixed(1)}%</strong>
                    {margin(product) < 0 && ' — you would sell below cost'}
                  </p>
                )}
              </fieldset>

              <div className="field-group" style={{marginTop: '12px'}}>
                <label className="admin-check">
                  <input name="deal" type="checkbox" checked={product.deal || false} onChange={e => setProduct({...product, deal: e.target.checked})} /> Special deal
                </label>
              </div>
              {product.deal && (
                <div className="field-group">
                  <label htmlFor="dealPrice">Deal price (₹)</label>
                  <input id="dealPrice" name="dealPrice" type="number" step="0.01" min="0.01" placeholder="Discounted price" required
                    value={product.dealPrice ?? ''} onChange={e => setProduct({...product, dealPrice: e.target.value})} />
                </div>
              )}
              <div className="admin-actions">
                <button className="primary" type="submit">{product.id ? 'Save changes' : 'Save as draft'}</button>
                {!product.id && (
                  <button type="button" className="secondary" onClick={e => { const f = e.currentTarget.form; if (f.reportValidity()) saveProduct(e, true); }}>Save and publish</button>
                )}
              </div>
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
            <h2>Products</h2>
            <p className="admin-hint">Only <strong>Published</strong> products are visible to customers. Cost and supplier are never sent to the storefront.</p>
            {products.length === 0 && <p>No products yet. Add your first one under New product.</p>}
            {products.map(p => (
              <div className="list-row admin-product" key={p.id}>
                <div>
                  <strong>{p.name}</strong>
                  <p>
                    <span className={`status-pill status-${p.status}`}>{STATUS_LABEL[p.status] || p.status}</span>
                    {p.source === 'AI_ASSISTED' && <span className="status-pill status-ai">AI-assisted</span>}
                    {' '}{p.category?.name} · Stock {p.stockQuantity}
                    {p.marginPercent != null && <> · Margin {p.marginPercent}%</>}
                  </p>
                  {p.status !== 'PUBLISHED' && p.readinessIssues?.length > 0 && (
                    <p className="admin-warn">To publish: {p.readinessIssues.join('; ')}</p>
                  )}
                </div>
                <div className="admin-row-actions">
                  {nextSteps(p).map(([to, label]) => (
                    <button key={to} className={to === 'PUBLISHED' ? 'primary' : ''} onClick={() => step(p, to)}>{label}</button>
                  ))}
                  <button onClick={() => editProduct(p)}>Edit</button>
                  <button onClick={() => toggleStock(p)}>{p.stockQuantity === 0 ? 'Mark in-stock' : 'Mark out-of-stock'}</button>
                  <button className="danger-btn" onClick={() => deleteProduct(p)}>Delete</button>
                </div>
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
