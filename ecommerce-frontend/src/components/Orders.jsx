import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ApiService } from '../services/api';
import { Loader } from './Loader';
import { EmptyState } from './EmptyState';
import { useToast } from '../contexts/ToastContext';

const inr = (v) => `₹${Number(v).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export function Orders() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(null);
  const toast = useToast();
  const placed = useLocation().state?.placed;

  useEffect(() => {
    setLoading(true);
    ApiService.orders()
      .then(setOrders)
      .catch(() => {})
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const cancel = (order) => {
    if (!window.confirm('Cancel this order?')) return;
    setCancelling(order.id);
    ApiService.cancelOrder(order.id)
      .then((updated) => {
        setOrders(prev => prev.map(o => (o.id === updated.id ? updated : o)));
        toast.success('Order cancelled');
      })
      .catch((err) => toast.error(err.response?.data?.message || 'Could not cancel the order'))
      .finally(() => setCancelling(null));
  };

  if (loading) {
    return <Loader message="Loading your orders..." />;
  }

  return (
    <section className="panel">
      {placed && (
        <div className="notice" role="status" style={{ marginBottom: '16px' }}>
          <span><strong>Thank you! Your order {placed} has been placed.</strong> We’ve emailed a confirmation and will update you when it ships. Pay when it arrives (cash on delivery).</span>
        </div>
      )}
      <h1>Order history</h1>
      {!orders.length ? (
        <EmptyState 
          icon="bi-receipt" 
          title="No orders yet" 
          message="You haven't placed any orders yet. Discover our premium catalog and order today." 
          actionText="Browse Catalog" 
          actionLink="/" 
        />
      ) : (
        <div style={{ display: 'grid', gap: '20px' }}>
          {orders.map(order => (
            <article className="order-card" key={order.id} style={{ display: 'flex', flexDirection: 'column', gap: '16px', alignItems: 'flex-start' }}>
              <div className="row-line" style={{ width: '100%', borderBottom: '1px solid var(--border-color)', paddingBottom: '12px' }}>
                <div>
                  <strong style={{ fontSize: '1.15rem' }}>Order ID: {order.trackingNumber}</strong>
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                    Placed on {new Date(order.orderDate).toLocaleDateString()}
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <span className={`tag ${order.status === 'DELIVERED' ? 'success' : ''}`}>{order.status}</span>
                  <span className={`tag ${order.paymentStatus === 'PAID' ? 'success' : ''}`} style={{ marginLeft: '8px' }}>{order.paymentMethod === 'COD' ? 'COD · ' : ''}{order.paymentStatus}</span>
                  {order.testPayment && <span className="tag" style={{ marginLeft: '8px' }}>TEST</span>}
                </div>
              </div>
              
              <div style={{ width: '100%', fontSize: '0.95rem' }}>
                <strong style={{ display: 'block', marginBottom: '8px', color: 'var(--primary-color)' }}>Items Purchased:</strong>
                {order.orderItems && order.orderItems.map(item => (
                  <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px dashed #e2e8f0' }}>
                    <span>{item.product?.name} <span style={{color: 'var(--text-muted)'}}>x{item.quantity}</span></span>
                    <span>₹{(item.price * item.quantity).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>
                ))}
              </div>

              <div style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: '8px' }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', maxWidth: '60%' }}>
                  <strong>Shipping to:</strong><br/>
                  {order.shippingAddress}
                </div>
                <div style={{ textAlign: 'right', minWidth: '200px' }}>
                  {order.pricingVersion && (
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', display: 'grid', gap: '2px', marginBottom: '6px' }}>
                      <span>Subtotal {inr(order.subtotalAmount)}</span>
                      {Number(order.discountAmount) > 0 && <span>Discount −{inr(order.discountAmount)}</span>}
                      <span>GST {inr(order.taxAmount)}</span>
                      <span>Shipping {Number(order.shippingAmount) === 0 ? 'FREE' : inr(order.shippingAmount)}</span>
                    </div>
                  )}
                  <div style={{ fontSize: '1.2rem', fontWeight: '700', color: 'var(--primary-color)' }}>
                    {order.pricingVersion ? 'Total' : 'Subtotal'}: {inr(order.totalPrice)}
                  </div>
                  {order.status === 'PENDING' && order.paymentStatus !== 'PAID' && (
                    <button type="button" className="danger-btn" style={{ marginTop: '8px' }}
                      disabled={cancelling === order.id} onClick={() => cancel(order)}>
                      {cancelling === order.id ? 'Cancelling...' : 'Cancel order'}
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
