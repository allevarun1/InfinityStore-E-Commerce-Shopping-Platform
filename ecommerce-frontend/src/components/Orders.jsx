import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { Loader } from './Loader';
import { EmptyState } from './EmptyState';
import { useToast } from '../contexts/ToastContext';
import { useStore } from '../contexts/StoreContext';
import { OrderTimeline, OrderItems, OrderTotals } from './OrderViews';
import { inr } from '../lib/format';

const dateFmt = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export function Orders() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [cancelling, setCancelling] = useState(null);
  const [confirming, setConfirming] = useState(null);
  const toast = useToast();
  const { info } = useStore();

  useEffect(() => {
    document.title = 'Your orders | InfinityStore';
    ApiService.orders()
      .then(setOrders)
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  }, []);

  const cancel = (order) => {
    setCancelling(order.id);
    ApiService.cancelOrder(order.id)
      .then((updated) => {
        setOrders(prev => prev.map(o => (o.id === updated.id ? updated : o)));
        toast.success('Order cancelled');
      })
      .catch((err) => toast.error(err.response?.data?.message || 'Could not cancel the order'))
      .finally(() => { setCancelling(null); setConfirming(null); });
  };

  if (loading) return <Loader message="Loading your orders..." />;

  return (
    <section className="orders-page">
      <header className="orders-head">
        <h1>Your orders</h1>
        {orders.length > 0 && <p>{orders.length} {orders.length === 1 ? 'order' : 'orders'}</p>}
      </header>

      {failed ? (
        <div className="notice" role="alert"><span>We couldn’t load your orders. Please refresh to try again.</span></div>
      ) : !orders.length ? (
        <EmptyState icon="bi-receipt" title="No orders yet"
          message="When you place an order it will appear here with live status updates."
          actionText="Shop the collection" actionLink="/#products" />
      ) : (
        <div className="orders-list">
          {orders.map(order => {
            const cancellable = order.status === 'PENDING' && order.paymentStatus !== 'PAID';
            return (
              <article className="order" key={order.id} aria-labelledby={`o-${order.id}`}>
                <header className="order-top">
                  <div>
                    <h2 id={`o-${order.id}`}>Order {order.trackingNumber}</h2>
                    <p>Placed {dateFmt.format(new Date(order.orderDate))} · {order.orderItems?.length || 0} {order.orderItems?.length === 1 ? 'item' : 'items'}</p>
                  </div>
                  <div className="order-amount">
                    <strong>{inr(order.totalPrice)}</strong>
                    <span className={`pill ${order.paymentStatus === 'PAID' ? 'ok' : ''}`}>
                      {order.paymentMethod === 'COD' ? (order.paymentStatus === 'PAID' ? 'Paid · COD' : 'Pay on delivery') : order.paymentStatus}
                    </span>
                    {order.testPayment && <span className="pill">Test</span>}
                  </div>
                </header>

                <OrderTimeline status={order.status} />
                <OrderItems order={order} />

                <details className="order-more">
                  <summary>Price details &amp; delivery address</summary>
                  <div className="order-more-grid">
                    <div>
                      <h3>Delivering to</h3>
                      <p className="order-addr">{order.shippingAddress}</p>
                      {order.phoneNumber && <p className="muted"><i className="bi bi-telephone" aria-hidden="true"></i> {order.phoneNumber}</p>}
                    </div>
                    <OrderTotals order={order} />
                  </div>
                </details>

                <footer className="order-actions">
                  {info?.whatsappNumber && (
                    <a className="btn btn-outline" target="_blank" rel="noopener noreferrer"
                      href={`https://wa.me/${info.whatsappNumber}?text=${encodeURIComponent(`Hi InfinityStore, about my order ${order.trackingNumber}`)}`}>
                      <i className="bi bi-whatsapp" aria-hidden="true"></i> Get help
                    </a>
                  )}
                  {cancellable && (confirming === order.id ? (
                    <div className="confirm-inline" role="group" aria-label="Confirm cancellation">
                      <span>Cancel this order?</span>
                      <button type="button" className="btn btn-outline" onClick={() => setConfirming(null)} disabled={cancelling === order.id}>Keep it</button>
                      <button type="button" className="btn btn-danger" onClick={() => cancel(order)} disabled={cancelling === order.id}>
                        {cancelling === order.id ? 'Cancelling…' : 'Yes, cancel'}
                      </button>
                    </div>
                  ) : (
                    <button type="button" className="btn btn-outline" onClick={() => setConfirming(order.id)}>Cancel order</button>
                  ))}
                </footer>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
