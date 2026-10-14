import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';

const inr = (value) =>
  `₹${Number(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const newKey = () =>
  (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export function Checkout() {
  const [form, setForm] = useState({ paymentMethod: 'COD', shippingAddress: '', phoneNumber: '' });
  const [quote, setQuote] = useState(null);
  const [options, setOptions] = useState({ cashOnDelivery: true, online: false, testMode: false });
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState({});
  // One key per checkout attempt: a double click or retry can never create two orders.
  const idempotencyKey = useRef(newKey());
  const toast = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    Promise.all([ApiService.cartQuote(), ApiService.paymentOptions()])
      .then(([q, o]) => {
        setQuote(q);
        setOptions(o);
      })
      .catch(() => toast.error('Failed to load checkout details'))
      .finally(() => setLoading(false));
  }, [toast]);

  const validate = () => {
    const tempErrors = {};
    if (form.shippingAddress.trim().length < 10) {
      tempErrors.shippingAddress = 'Address must be at least 10 characters long';
    }
    if (!/^\d{10}$/.test(form.phoneNumber)) {
      tempErrors.phoneNumber = 'Phone number must be exactly 10 digits';
    }
    setErrors(tempErrors);
    return Object.keys(tempErrors).length === 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  const placeOrder = (e) => {
    e.preventDefault();
    if (isSubmitting || !validate()) return;
    setIsSubmitting(true);
    ApiService.checkout(form, idempotencyKey.current)
      .then(() => {
        toast.success('Order placed successfully');
        navigate('/orders');
      })
      .catch(err => {
        toast.error(err.response?.data?.message || 'Checkout failed');
        setIsSubmitting(false);
      });
  };

  if (loading) {
    return <Loader message="Loading checkout details..." />;
  }

  const hasItems = quote && quote.lines.length > 0;

  return (
    <div className="form-card" style={{ maxWidth: '640px' }}>
      <form onSubmit={placeOrder} style={{ display: 'flex', flexDirection: 'column', gap: '16px', width: '100%' }}>
        <h1>Checkout</h1>

        {hasItems && (
          <div className="order-summary-box">
            <h3>Order Summary</h3>
            {quote.lines.map(line => (
              <div key={line.cartItemId} className="order-summary-row" style={{ fontSize: '0.85rem' }}>
                <span>{line.name} (x{line.quantity})</span>
                <strong>{inr(line.lineNet)}</strong>
              </div>
            ))}
            <hr style={{ border: '0', borderTop: '1px solid var(--border-color)', margin: '8px 0' }} />
            <div className="order-summary-row">
              <span>Subtotal:</span>
              <span>{inr(quote.subtotal)}</span>
            </div>
            {Number(quote.discount) > 0 && (
              <div className="order-summary-row">
                <span>Discount:</span>
                <span>−{inr(quote.discount)}</span>
              </div>
            )}
            <div className="order-summary-row">
              <span>GST:</span>
              <span>{inr(quote.tax)}</span>
            </div>
            <div className="order-summary-row">
              <span>Shipping:</span>
              <span>{Number(quote.shipping) === 0 ? 'FREE' : inr(quote.shipping)}</span>
            </div>
            <div className="order-summary-row" style={{ fontSize: '1.1rem', fontWeight: 'bold', color: 'var(--primary-color)' }}>
              <span>Total payable:</span>
              <span>{inr(quote.total)}</span>
            </div>
          </div>
        )}

        <div className="field-group">
          <label htmlFor="shippingAddress">Delivery Address</label>
          <textarea
            id="shippingAddress"
            name="shippingAddress"
            placeholder="Enter your complete delivery address"
            required
            maxLength={500}
            value={form.shippingAddress}
            className={errors.shippingAddress ? 'input-invalid' : form.shippingAddress.length >= 10 ? 'input-valid' : ''}
            onChange={handleChange}
          />
          {errors.shippingAddress && <span className="field-error" role="alert">{errors.shippingAddress}</span>}
        </div>

        <div className="field-group">
          <label htmlFor="phoneNumber">Contact Phone Number</label>
          <input
            id="phoneNumber"
            name="phoneNumber"
            inputMode="numeric"
            autoComplete="tel"
            placeholder="10-digit mobile number"
            required
            value={form.phoneNumber}
            className={errors.phoneNumber ? 'input-invalid' : /^\d{10}$/.test(form.phoneNumber) ? 'input-valid' : ''}
            onChange={handleChange}
          />
          {errors.phoneNumber && <span className="field-error" role="alert">{errors.phoneNumber}</span>}
        </div>

        <div className="field-group">
          <label htmlFor="paymentMethod">Payment Method</label>
          <select id="paymentMethod" name="paymentMethod" value={form.paymentMethod} onChange={handleChange}>
            <option value="COD">Cash on delivery (pay when you receive)</option>
            {options.online && (
              <option value="ONLINE">
                {options.testMode ? 'Online payment (TEST MODE - no real charge)' : 'Pay online (UPI / card / netbanking)'}
              </option>
            )}
          </select>
          {!options.online && (
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Online payment is coming soon. Cash on delivery is available now.
            </span>
          )}
          {options.online && options.testMode && (
            <span style={{ fontSize: '0.8rem', color: 'var(--danger-color)' }}>
              Test mode: no payment details are collected and no money moves.
            </span>
          )}
        </div>

        <button type="submit" className="primary" disabled={isSubmitting || !hasItems}>
          {isSubmitting ? 'Placing Order...' : 'Place Order'}
        </button>
      </form>
    </div>
  );
}
