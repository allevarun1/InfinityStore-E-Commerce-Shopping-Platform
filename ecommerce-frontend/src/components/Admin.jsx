import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { imageSrc, inr } from '../lib/format';
import { Loader } from './Loader';
import '../admin2.css';
import '../admin3.css';
import { AdminCoupons } from './AdminCoupons';
import { AdminAttempts } from './AdminAttempts';
import { NotifyCard } from './NotifyCard';
import { splitOrders } from '../lib/orderBuckets';
import { AdminAutopilot } from './AdminAutopilot';
import { AdminLeads } from './AdminLeads';
import { AdminCatalogue } from './AdminCatalogue';
import { AdminVideos } from './AdminVideos';
import { AdminTestimonials } from './AdminTestimonials';
import { AdminInsights } from './AdminInsights';
import { AdminDashboard } from './AdminDashboard';
import { AdminProductList } from './AdminProductList';
import { AdminProductEditor } from './AdminProductEditor';
import { AdminStudio } from './AdminStudio';
import { AdminMedia } from './AdminMedia';
import { BrandMark } from './BrandMark';
import { AdminPopups } from './AdminPopups';
import { AdminCustomers } from './AdminCustomers';
import { ShareSheet } from './ShareSheet';
import { orderMessages, waLink } from '../lib/share';

const BLANK_PRODUCT = { stockQuantity: 10, deal: false, dealPrice: '', categoryId: '', status: 'DRAFT', source: 'MANUAL' };
const STATUS_LABEL = { DRAFT: 'Draft', READY_FOR_REVIEW: 'Ready for review', APPROVED: 'Approved', PUBLISHED: 'Published' };

/** Sidebar sections. The keys are the ?tab= values, so existing links keep working. */
const NAV = [
  { group: 'Home', items: [['autopilot', 'bi-speedometer2', 'Today']] },
  { group: 'Catalogue', items: [['products', 'bi-box-seam', 'Sarees'], ['management', 'bi-plus-circle', 'Add a saree'], ['studio', 'bi-stars', 'Photo & video studio'], ['media', 'bi-images', 'Media library'], ['catalogue', 'bi-file-earmark-spreadsheet', 'Bulk & feeds']] },
  { group: 'Sales', items: [['orders', 'bi-receipt', 'Orders'], ['coupons', 'bi-tag', 'Coupons & offers'], ['popups', 'bi-megaphone', 'Offer pop-ups']] },
  { group: 'Grow', items: [['videos', 'bi-camera-video', 'Videos & quotes'], ['leads', 'bi-person-lines-fill', 'Leads'], ['insights', 'bi-graph-up-arrow', 'Insights']] },
  { group: 'People', items: [['users', 'bi-people', 'Customers']] },
];
const TITLES = Object.fromEntries(NAV.flatMap(g => g.items.map(([k, , l]) => [k, l])));

const ORDER_FILTERS = [['ALL', 'All'], ['PENDING', 'New'], ['CONFIRMED', 'To ship'], ['SHIPPED', 'On the way'], ['DELIVERED', 'Delivered'], ['CANCELLED', 'Cancelled']];

const ADMIN_TABS = ['autopilot', 'catalogue', 'coupons', 'insights', 'leads', 'management', 'media', 'orders', 'popups',
  'products', 'studio', 'users', 'videos'];

export function Admin() {
  const [searchParams] = useSearchParams();
  // The owner lands on Today; links such as /admin?tab=leads open a tab directly.
  const [currentTab, setCurrentTab] = useState(() => {
    const asked = searchParams.get('tab');
    // An unknown or old link opens Today instead of a blank page.
    if (asked && ADMIN_TABS.includes(asked)) return asked;
    return searchParams.get('editId') ? 'management' : 'autopilot';
  });

  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  const [product, setProduct] = useState(BLANK_PRODUCT);
  const [colourFor, setColourFor] = useState(null);
  const [studioPhoto, setStudioPhoto] = useState(null);
  const [studioProductId, setStudioProductId] = useState('');
  const [orderFilter, setOrderFilter] = useState('ALL');
  const [orderView, setOrderView] = useState('orders'); // 'orders' (paid) | 'attempts' (did not pay)
  const [orderQ, setOrderQ] = useState('');
  const [share, setShare] = useState(null);          // { products, template } while the WhatsApp share sheet is open
  const [justPublished, setJustPublished] = useState(null);

  const toast = useToast();

  const load = () => {
    setLoading(true);
    Promise.all([ApiService.categories(), ApiService.adminUsers(), ApiService.adminOrders(), ApiService.adminProducts()])
      .then(([cats, usr, ords, prods]) => {
        setCategories(cats);
        setUsers(usr);
        setOrders(ords);
        setProducts(prods);
        const editId = searchParams.get('editId');
        if (editId) {
          const target = prods.find(p => p.id === Number(editId));
          if (target) editProduct(target);
        }
      })
      .catch(() => toast.error('Failed to load administration data'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    document.title = 'Admin | Rekha Shopping Mall';
    load();
    // eslint-disable-next-line
  }, [searchParams]);

  const go = (tab) => { setCurrentTab(tab); window.scrollTo({ top: 0 }); };
  const openStudio = (p) => { setStudioPhoto(null); setStudioProductId(p?.id ? String(p.id) : ''); go('studio'); };

  const saveProduct = (e, publish = false) => {
    e.preventDefault();
    const numeric = (v) => (v === '' || v === undefined || v === null ? null : Number(v));
    const { previewImage, previewGallery, readinessIssues, marginPercent, category: _c, categoryName, ...rest } = product; // eslint-disable-line no-unused-vars
    const payload = {
      ...rest,
      categoryId: Number(product.categoryId),
      price: numeric(product.price),
      dealPrice: product.deal ? numeric(product.dealPrice) : null,
      stockQuantity: numeric(product.stockQuantity),
      costPrice: numeric(product.costPrice),
      // Removed from the form: cleared so old values no longer show to shoppers. No GST: the price is final.
      fabric: null, weave: null, occasion: null, region: null, lengthMeters: null, blouseIncluded: null, gstPercent: null,
      status: !product.id && publish ? 'PUBLISHED' : 'DRAFT',
    };
    ApiService.saveProduct(payload)
      .then((saved) => {
        toast.success(publish && !product.id ? 'Saree published' : 'Saree saved');
        if (publish && !product.id && saved?.id) setJustPublished(saved);
        setProduct(BLANK_PRODUCT);
        setCurrentTab('products');
        load();
      })
      .catch(err => toast.error(err.response?.data?.message || 'Failed to save the saree'));
  };

  const step = (p, to) => {
    ApiService.setProductStatus(p.id, to)
      .then(() => { toast.success(`${p.name}: ${STATUS_LABEL[to].toLowerCase()}`); if (to === 'PUBLISHED') setJustPublished(p); load(); })
      .catch(err => toast.error(err.response?.data?.message || 'Could not change the status'));
  };

  const editProduct = (p) => {
    // Drafts' uploaded photos are not publicly served yet, so edit the stored copies directly.
    setProduct({ ...p, categoryId: p.category.id, gallery: p.previewGallery || p.gallery || [] });
    setCurrentTab('management');
  };

  const addColour = (e) => {
    e.preventDefault();
    const colour = colourFor.colour.trim();
    if (!colour) return;
    ApiService.addColour(colourFor.id, colour)
      .then(copy => {
        toast.success(`${colour} added as a draft. Add its photos and stock, then publish.`);
        setColourFor(null);
        load();
        editProduct(copy);
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not add the colour'));
  };

  /** Creates a category from inside the saree form and resolves with it, so the form can select it straight away. */
  const createCategory = (name) => ApiService.saveCategory({ name: name.trim(), description: '' })
    .then((cat) => {
      toast.success(`Category "${cat.name}" created`);
      setCategories(list => [...list.filter(c => c.id !== cat.id), cat].sort((a, b) => a.name.localeCompare(b.name)));
      return cat;
    })
    .catch((err) => { toast.error(err.response?.data?.message || 'Could not create the category'); throw err; });

  const toggleStock = (p) => {
    ApiService.markStock(p.id, p.stockQuantity === 0)
      .then(() => { toast.success('Stock status updated'); load(); })
      .catch(() => toast.error('Failed to toggle stock status'));
  };

  const deleteProduct = (p) => {
    ApiService.deleteProduct(p.id)
      .then(() => { toast.success('Saree deleted'); load(); })
      .catch(() => toast.error('Failed to delete the saree'));
  };

  const deleteUser = (u) => ApiService.deleteUser(u.id)
    .then(() => { toast.success('Customer deleted'); load(); })
    .catch((err) => toast.error(err.response?.data?.message || 'Failed to delete the customer'));

  const openShare = (list, template) => setShare({ products: Array.isArray(list) ? list : [list], template });

  // Mirrors the server's payment state machine; the server remains the authority.
  const paymentOptionsFor = (order) => {
    const next = { PENDING: ['PAID', 'FAILED'], PAID: ['REFUNDED'], FAILED: [], REFUNDED: [] }[order.paymentStatus] || [];
    const allowed = next.filter(st => st !== 'PAID' || (order.paymentMethod === 'COD' && order.status !== 'CANCELLED'));
    return [order.paymentStatus, ...allowed];
  };

  const delivery = (order, status) => {
    ApiService.updateDeliveryStatus(order.id, status)
      .then(() => { toast.success('Order status updated'); load(); })
      .catch(() => toast.error('Failed to update the order status'));
  };

  const payment = (order, status) => {
    ApiService.updatePaymentStatus(order.id, status)
      .then(() => { toast.success('Payment status updated'); load(); })
      .catch(() => toast.error('Failed to update the payment status'));
  };

  /** Studio hands back a saved photo; open the editor with it in place and let the owner review before saving. */
  const useOnProduct = (productId, url, mode) => {
    const p = products.find(x => x.id === productId);
    if (!p) return;
    const gallery = p.previewGallery || p.gallery || [];
    setProduct({
      ...p, categoryId: p.category.id, previewImage: null, previewGallery: null,
      imageUrl: mode === 'cover' ? url : (p.previewImage || p.imageUrl),
      gallery: mode === 'cover' ? gallery : [...gallery, url].slice(0, 6),
    });
    setCurrentTab('management');
    window.scrollTo({ top: 0 });
    toast.success(mode === 'cover' ? 'New cover photo is in the editor. Press Save changes to keep it.' : 'Photo added to the gallery. Press Save changes to keep it.');
  };

  // Only paid orders (or cash on delivery) are real orders; unpaid checkout attempts live in their own view.
  const { placed: placedOrders, attempts } = useMemo(() => splitOrders(orders), [orders]);
  const shownOrders = useMemo(() => placedOrders.filter(o => {
    if (orderFilter !== 'ALL' && o.status !== orderFilter) return false;
    const q = orderQ.trim().toLowerCase();
    if (!q) return true;
    return `${o.trackingNumber} ${o.user?.username || ''} ${o.user?.firstName || ''} ${o.user?.lastName || ''} ${o.phoneNumber || ''}`.toLowerCase().includes(q);
  }), [placedOrders, orderFilter, orderQ]);

  if (loading && products.length === 0) return <Loader message="Loading admin board..." />;

  const counts = { products: products.length, orders: placedOrders.filter(o => o.status === 'PENDING').length, users: users.length };

  return (
    <section className="adm admin-panel-container">
      <aside className="adm-side">
        <div className="adm-brand"><BrandMark /><div><strong>Rekha Shopping Mall</strong><small>Store admin</small></div></div>
        <nav className="admin-tabs" aria-label="Admin sections">
          {NAV.map(g => (
            <div className="adm-group" key={g.group}>
              <span className="adm-group-label">{g.group}</span>
              {g.items.map(([key, icon, label]) => (
                <button key={key} type="button" className={currentTab === key ? 'active' : ''} aria-current={currentTab === key ? 'page' : undefined} onClick={() => go(key)}>
                  <i className={`bi ${icon}`} aria-hidden="true"></i><span>{label}</span>
                  {key === 'products' && <em>{counts.products}</em>}
                  {key === 'orders' && counts.orders > 0 && <em className="hot">{counts.orders} new</em>}
                  {key === 'users' && <em>{counts.users}</em>}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <Link className="adm-view" to="/"><i className="bi bi-box-arrow-up-right" aria-hidden="true"></i> View store</Link>
      </aside>

      <div className="adm-main">
        <header className="adm-top">
          <h1>{TITLES[currentTab] || 'Admin'}</h1>
          <div>
            <button type="button" className="btn btn-outline" onClick={() => openStudio(null)}><i className="bi bi-stars" aria-hidden="true"></i> Studio</button>
            <button type="button" className="btn btn-primary" onClick={() => { setProduct(BLANK_PRODUCT); go('management'); }}><i className="bi bi-plus-lg" aria-hidden="true"></i> Add a saree</button>
          </div>
        </header>

        {justPublished && (
          <div className="pub-nudge" role="status">
            <i className="bi bi-whatsapp" aria-hidden="true"></i>
            <span><b>{justPublished.name}</b> is live. Tell your customers now: new arrivals sell best in their first two days.</span>
            <button type="button" className="btn btn-wa sm" onClick={() => { openShare(products.find(x => x.id === justPublished.id) || justPublished, 'new'); setJustPublished(null); }}>Share on WhatsApp</button>
            <button type="button" className="pub-x" onClick={() => setJustPublished(null)} aria-label="Dismiss"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
          </div>
        )}

        {currentTab === 'autopilot' && (
          <>
            <NotifyCard className="dash-notify" />
            <AdminDashboard orders={placedOrders} products={products} onNavigate={go} onShare={openShare} />
            <AdminAutopilot onNavigate={go} />
          </>
        )}

        {currentTab === 'management' && (
          <AdminProductEditor product={product} setProduct={setProduct} categories={categories} onSave={saveProduct}
            onNew={() => setProduct(BLANK_PRODUCT)} onCreateCategory={createCategory}
            onStudio={openStudio} />
        )}

        {currentTab === 'products' && (
          <AdminProductList products={products} onStep={step} onEdit={editProduct} onStock={toggleStock} onDelete={deleteProduct}
            colourFor={colourFor} setColourFor={setColourFor} onAddColour={addColour} onNew={() => { setProduct(BLANK_PRODUCT); go('management'); }}
            onStudio={openStudio} onShare={(p) => openShare(p)} />
        )}

        {currentTab === 'studio' && <AdminStudio products={products} initial={studioPhoto} initialProductId={studioProductId} onUseOnProduct={useOnProduct} onVideosChanged={() => {}} />}
        {currentTab === 'media' && <AdminMedia onStudio={(a) => { setStudioPhoto(a); go('studio'); }} />}
        {currentTab === 'popups' && <AdminPopups />}

        {currentTab === 'insights' && (
          <div className="tab-content">
            <AdminInsights
              onOpenProduct={(id) => { const p = products.find(x => x.id === id); if (p) editProduct(p); }}
              onStartDraft={(prefill) => { setProduct({ ...BLANK_PRODUCT, ...prefill }); setCurrentTab('management'); }}
            />
          </div>
        )}

        {currentTab === 'users' && <AdminCustomers onDelete={deleteUser} />}

        {currentTab === 'coupons' && <AdminCoupons />}
        {currentTab === 'leads' && <AdminLeads />}
        {currentTab === 'catalogue' && <AdminCatalogue onDone={load} />}
        {currentTab === 'videos' && (<><AdminVideos products={products} /><AdminTestimonials products={products} /></>)}

        {currentTab === 'orders' && (
          <div className="tab-content">
            <h2>Customer orders</h2>
            <NotifyCard />
            <div className="ord-views" role="tablist" aria-label="Orders or unpaid attempts">
              <button type="button" role="tab" aria-selected={orderView === 'orders'} className={orderView === 'orders' ? 'on' : ''} onClick={() => setOrderView('orders')}>
                <i className="bi bi-bag-check" aria-hidden="true"></i> Paid orders <small>{placedOrders.length}</small>
              </button>
              <button type="button" role="tab" aria-selected={orderView === 'attempts'} className={orderView === 'attempts' ? 'on' : ''} onClick={() => setOrderView('attempts')}>
                <i className="bi bi-x-circle" aria-hidden="true"></i> Did not pay <small>{attempts.filter(o => !o.testPayment).length}</small>
              </button>
            </div>
            {orderView === 'attempts' ? <AdminAttempts attempts={attempts} placed={placedOrders} /> : (<>
            <p className="admin-hint">Only orders the customer has paid for. Pack and ship these.</p>
            <div className="toolbar">
              <div className="chips" role="group" aria-label="Show orders">
                {ORDER_FILTERS.map(([k, l]) => (
                  <button key={k} type="button" className={orderFilter === k ? 'on' : ''} aria-pressed={orderFilter === k} onClick={() => setOrderFilter(k)}>
                    {l} <small>{k === 'ALL' ? placedOrders.length : placedOrders.filter(o => o.status === k).length}</small>
                  </button>
                ))}
              </div>
              <input type="search" placeholder="Search order, name or phone" value={orderQ} onChange={e => setOrderQ(e.target.value)} aria-label="Search orders" />
            </div>
            {shownOrders.length === 0 && <p className="ap-empty">No orders here.</p>}
            {shownOrders.map(order => (
              <article className={`ord ${order.status === 'PENDING' ? 'is-new' : ''}`} key={order.id}>
                <header>
                  <div>
                    <strong>{order.trackingNumber}</strong>
                    {order.status === 'PENDING' && <span className="chip-status wait">New order</span>}
                    {order.testPayment && <span className="chip-status off">Test payment</span>}
                    <small>{order.orderDate ? new Date(order.orderDate).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : ''}</small>
                  </div>
                  <b>{inr(order.totalPrice)}</b>
                </header>
                <div className="ord-grid">
                  <div className="ord-info">
                    <p><i className="bi bi-person" aria-hidden="true"></i> {order.user ? `${order.user.firstName} ${order.user.lastName} (@${order.user.username})` : 'Unknown customer'}</p>
                    <p><i className="bi bi-telephone" aria-hidden="true"></i> {order.phoneNumber || 'N/A'}</p>
                    <p><i className="bi bi-geo-alt" aria-hidden="true"></i> {order.shippingAddress}</p>
                    {order.paymentMethod === 'ONLINE' && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (
                      <p className="ord-unpaid" role="note"><i className="bi bi-hourglass-split" aria-hidden="true"></i> <strong>Awaiting payment: do not pack or ship.</strong> It is cancelled automatically if not paid within 45 minutes.</p>
                    )}
                    {order.whatsapp && (order.whatsapp.owner !== 'OFF' || order.whatsapp.customer !== 'OFF') && (
                      <p className={`ord-wa-status ${order.whatsapp.error ? 'bad' : ''}`}>
                        <i className="bi bi-whatsapp" aria-hidden="true"></i>
                        <span>WhatsApp: {[['owner', 'you'], ['customer', 'customer']].filter(([k]) => order.whatsapp[k] !== 'OFF')
                          .map(([k, who]) => `${who} ${order.whatsapp[k] === 'SENT' ? '✓ sent' : 'not sent yet'}`).join(' · ')}
                          {order.whatsapp.error && <small>{order.whatsapp.error}</small>}</span>
                        {(order.whatsapp.owner === 'WAITING' || order.whatsapp.customer === 'WAITING') && (
                          <button type="button" className="btn btn-outline sm" onClick={() => ApiService.resendWhatsapp(order.id).then(r => { toast.success(r.message); setTimeout(load, 8000); }).catch(err => toast.error(err.response?.data?.message || 'Could not resend'))}>Resend</button>
                        )}
                      </p>
                    )}
                    <p><i className="bi bi-credit-card" aria-hidden="true"></i> {order.paymentMethod} ({order.paymentStatus}){order.couponCode && <> · coupon {order.couponCode} (−{inr(order.couponDiscountAmount || 0, { decimals: 0 })})</>}</p>
                  </div>
                  <ul className="ord-items">
                    {order.orderItems?.map(item => (
                      <li key={item.id}>
                        {item.product?.imageUrl ? <img src={imageSrc(item.product.imageUrl)} alt="" loading="lazy" /> : <span className="ph" aria-hidden="true"></span>}
                        <span>{item.product?.name || 'Deleted product'}<small>× {item.quantity} · {inr(item.price, { decimals: 0 })}</small></span>
                      </li>
                    ))}
                  </ul>
                </div>
                <div className="ord-do">
                  {order.phoneNumber && (
                    <div className="ord-wa" role="group" aria-label="WhatsApp the customer">
                      <i className="bi bi-whatsapp" aria-hidden="true"></i>
                      {orderMessages(order).map(m => (
                        <a key={m.key} className={m.key === 'confirm' || m.key === 'shipped' ? 'hot' : ''} href={waLink(order.phoneNumber, m.text)} target="_blank" rel="noopener noreferrer">{m.label}</a>
                      ))}
                    </div>
                  )}
                  <div className="ord-next">
                    {order.nextStatuses?.includes('CONFIRMED') && <button type="button" className="btn btn-outline sm" onClick={() => delivery(order, 'CONFIRMED')}>Mark confirmed</button>}
                    {order.nextStatuses?.includes('SHIPPED') && <button type="button" className="btn btn-primary sm" onClick={() => delivery(order, 'SHIPPED')}>Mark shipped</button>}
                    {order.nextStatuses?.includes('DELIVERED') && <button type="button" className="btn btn-primary sm" onClick={() => delivery(order, 'DELIVERED')}>Mark delivered</button>}
                    {order.paymentMethod === 'COD' && order.paymentStatus === 'PENDING' && order.status === 'DELIVERED' && <button type="button" className="btn btn-outline sm" onClick={() => payment(order, 'PAID')}>Cash received</button>}
                  </div>
                </div>
                <footer>
                  <div className="field-group"><label>Order status</label>
                    <select value={order.status} onChange={e => delivery(order, e.target.value)}>
                      {[order.status, ...(order.nextStatuses || [])].map(st => <option key={st}>{st}</option>)}
                    </select></div>
                  <div className="field-group"><label>Payment status</label>
                    <select value={order.paymentStatus} onChange={e => payment(order, e.target.value)}>
                      {paymentOptionsFor(order).map(st => <option key={st}>{st}</option>)}
                    </select></div>
                </footer>
              </article>
            ))}
            </>)}
          </div>
        )}
      </div>
      {share && <ShareSheet products={share.products} startTemplate={share.template} onClose={() => setShare(null)} />}
    </section>
  );
}
