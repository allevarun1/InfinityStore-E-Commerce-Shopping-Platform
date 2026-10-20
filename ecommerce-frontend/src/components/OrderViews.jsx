import React, { useEffect } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { inr, imageSrc } from '../lib/format';
import { useStore } from '../contexts/StoreContext';

const STEPS = [
  ['PENDING', 'Placed', 'bi-bag-check'],
  ['CONFIRMED', 'Confirmed', 'bi-patch-check'],
  ['SHIPPED', 'Shipped', 'bi-truck'],
  ['DELIVERED', 'Delivered', 'bi-house-heart'],
];

/** Progress line for an order. Cancelled orders show a single clear state instead. */
export function OrderTimeline({ status }) {
  if (status === 'CANCELLED') {
    return <p className="otl-cancelled"><i className="bi bi-x-circle" aria-hidden="true"></i> This order was cancelled</p>;
  }
  const at = Math.max(0, STEPS.findIndex(([s]) => s === status));
  return (
    <ol className="otl" aria-label={`Order status: ${STEPS[at][1]}`}>
      {STEPS.map(([key, label, icon], i) => (
        <li key={key} className={i < at ? 'done' : i === at ? 'current' : ''} aria-current={i === at ? 'step' : undefined}>
          <span className="otl-dot"><i className={`bi ${i < at ? 'bi-check-lg' : icon}`} aria-hidden="true"></i></span>
          <span className="otl-label">{label}</span>
        </li>
      ))}
    </ol>
  );
}

export function OrderTotals({ order }) {
  return (
    <dl className="ck-totals">
      <div><dt>Subtotal</dt><dd>{inr(order.subtotalAmount ?? order.totalPrice)}</dd></div>
      {Number(order.discountAmount) > 0 && <div className="save"><dt>Sale savings</dt><dd>−{inr(order.discountAmount)}</dd></div>}
      {Number(order.couponDiscountAmount) > 0 && <div className="save"><dt>Coupon {order.couponCode}</dt><dd>−{inr(order.couponDiscountAmount)}</dd></div>}
      {order.taxAmount != null && <div><dt>GST</dt><dd>{inr(order.taxAmount)}</dd></div>}
      {order.shippingAmount != null && <div><dt>Shipping</dt><dd>{Number(order.shippingAmount) === 0 ? 'Free' : inr(order.shippingAmount)}</dd></div>}
      <div className="grand"><dt>{order.paymentMethod === 'COD' && order.paymentStatus !== 'PAID' ? 'Pay on delivery' : 'Total'}</dt><dd>{inr(order.totalPrice)}</dd></div>
    </dl>
  );
}

export function OrderItems({ order }) {
  return (
    <ul className="ck-lines">
      {(order.orderItems || []).map(item => (
        <li key={item.id}>
          <span className="ck-thumb"><img src={imageSrc(item.product?.imageUrl)} alt="" width="56" height="70" /><b>{item.quantity}</b></span>
          <span className="ck-name">{item.product?.id ? <Link to={`/products/${item.product.id}`}>{item.product.name}</Link> : item.product?.name}</span>
          <span>{inr(Number(item.price) * item.quantity)}</span>
        </li>
      ))}
    </ul>
  );
}

/** Shown right after checkout: reassurance, what happens next, and where to go. */
export function OrderSuccess() {
  const order = useLocation().state?.order;
  const { info, refreshCart } = useStore();
  useEffect(() => {
    document.title = 'Order placed | InfinityStore';
    refreshCart();
    window.scrollTo(0, 0);
  }, [refreshCart]);
  if (!order) return <Navigate to="/orders" replace />;
  const cod = order.paymentMethod === 'COD' && order.paymentStatus !== 'PAID';

  return (
    <div className="success">
      <ol className="ck-steps" aria-label="Checkout progress">
        <li className="done">Cart</li>
        <li className="done">Delivery &amp; payment</li>
        <li className="current" aria-current="step">Confirmation</li>
      </ol>

      <section className="success-hero" aria-labelledby="ok-title">
        <span className="success-check" aria-hidden="true"><i className="bi bi-check-lg"></i></span>
        <h1 id="ok-title">Thank you! Your order is placed.</h1>
        <p>Order number <strong className="success-no">{order.trackingNumber}</strong></p>
        <p className="muted">We’ve emailed your confirmation. Keep the order number handy if you contact us.</p>
      </section>

      <div className="success-grid">
        <section className="ck-card" aria-labelledby="next-title">
          <h2 id="next-title">What happens next</h2>
          <OrderTimeline status={order.status} />
          <ul className="success-next">
            <li><i className="bi bi-patch-check" aria-hidden="true"></i><span><strong>We confirm your order</strong> and carefully pack your saree.</span></li>
            <li><i className="bi bi-truck" aria-hidden="true"></i><span><strong>It ships to you.</strong> Track progress any time under Your orders.</span></li>
            <li><i className={`bi ${cod ? 'bi-cash-coin' : 'bi-check2-circle'}`} aria-hidden="true"></i>
              <span>{cod ? <><strong>Pay {inr(order.totalPrice)} on delivery</strong> in cash or UPI to the courier.</> : <><strong>Payment received.</strong> Nothing more to pay.</>}</span></li>
          </ul>
          <div className="success-ship">
            <h3>Delivering to</h3>
            <p>{order.shippingAddress}</p>
            <p className="muted"><i className="bi bi-telephone" aria-hidden="true"></i> {order.phoneNumber}</p>
          </div>
        </section>

        <aside className="ck-summary" aria-labelledby="ok-sum">
          <h2 id="ok-sum">Order summary</h2>
          <OrderItems order={order} />
          <OrderTotals order={order} />
          <div className="success-cta">
            <Link className="btn btn-primary" to="/orders">Track your order</Link>
            <Link className="btn btn-outline" to="/#products">Continue shopping</Link>
          </div>
          {info?.whatsappNumber && (
            <a className="text-link" href={`https://wa.me/${info.whatsappNumber}?text=${encodeURIComponent(`Hi InfinityStore, about my order ${order.trackingNumber}`)}`}
              target="_blank" rel="noopener noreferrer"><i className="bi bi-whatsapp" aria-hidden="true"></i> Questions? Chat with us</a>
          )}
        </aside>
      </div>
    </div>
  );
}
