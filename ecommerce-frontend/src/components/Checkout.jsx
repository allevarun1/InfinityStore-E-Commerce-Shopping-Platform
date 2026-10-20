import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';
import { Loader } from './Loader';
import { track } from '../lib/analytics';
import { inr, imageSrc } from '../lib/format';

const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh', 'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jammu and Kashmir',
  'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya',
  'Mizoram', 'Nagaland', 'Odisha', 'Puducherry', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
  'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
];

const newKey = () =>
  (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

const BLANK = { fullName: '', phone: '', line1: '', line2: '', city: '', state: '', pincode: '' };
const addressKey = (user) => `infinity_address_${user?.id ?? 'me'}`;

/** One readable block for the courier label; the server stores it as the order's shipping address. */
const composeAddress = (a) =>
  [a.fullName.trim(), a.line1.trim(), a.line2.trim(), `${a.city.trim()}, ${a.state} - ${a.pincode.trim()}`]
    .filter(Boolean).join('\n');

export function Checkout() {
  const { currentUser } = useAuth();
  const [addr, setAddr] = useState(BLANK);
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [quote, setQuote] = useState(null);
  const [options, setOptions] = useState({ cashOnDelivery: true, online: false, testMode: false });
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState({});
  const [couponInput, setCouponInput] = useState('');
  const [coupon, setCoupon] = useState('');
  const [couponBusy, setCouponBusy] = useState(false);
  const [remember, setRemember] = useState(true);
  const [pinHint, setPinHint] = useState('');
  // One key per checkout attempt: a double click or retry can never create two orders.
  const idempotencyKey = useRef(newKey());
  const toast = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    document.title = 'Checkout | InfinityStore';
    setLoading(true);
    let saved = null;
    try { saved = JSON.parse(localStorage.getItem(addressKey(currentUser)) || 'null'); } catch { saved = null; }
    const name = [currentUser?.firstName, currentUser?.lastName].filter(Boolean).join(' ');
    setAddr({ ...BLANK, fullName: name, ...(saved || {}) });
    Promise.all([ApiService.cartQuote(), ApiService.paymentOptions(), ApiService.profile().catch(() => null)])
      .then(([q, o, profile]) => {
        setQuote(q);
        setOptions(o);
        if (profile?.phone && /^\d{10}$/.test(profile.phone)) setAddr(a => (a.phone ? a : { ...a, phone: profile.phone }));
        track.beginCheckout(q);
      })
      .catch(() => toast.error('Failed to load checkout details'))
      .finally(() => setLoading(false));
  }, [toast, currentUser]);

  const set = (key) => (e) => {
    const value = key === 'phone' || key === 'pincode' ? e.target.value.replace(/\D/g, '') : e.target.value;
    setAddr(a => ({ ...a, [key]: value }));
    if (errors[key]) setErrors(prev => ({ ...prev, [key]: '' }));
  };

  // PIN code -> city and state, from India Post's public directory. Only fills empty fields; failures are silent.
  useEffect(() => {
    if (!/^[1-9]\d{5}$/.test(addr.pincode)) { setPinHint(''); return undefined; }
    const ctl = new AbortController();
    fetch(`https://api.postalpincode.in/pincode/${addr.pincode}`, { signal: ctl.signal })
      .then(r => r.json())
      .then(([res]) => {
        const po = res?.Status === 'Success' ? res.PostOffice?.[0] : null;
        if (!po) { setPinHint(''); return; }
        const state = INDIAN_STATES.find(s => s.toLowerCase() === String(po.State).toLowerCase()) || '';
        setAddr(a => ({ ...a, city: a.city || po.District || '', state: a.state || state }));
        setPinHint(`${po.District}, ${po.State}`);
      })
      .catch(() => {});
    return () => ctl.abort();
  }, [addr.pincode]);

  const applyCoupon = (e) => {
    e.preventDefault();
    const code = couponInput.trim();
    if (!code) return;
    setCouponBusy(true);
    ApiService.cartQuote(code)
      .then(q => {
        setQuote(q);
        setCoupon(q.couponCode || '');
        toast.success(`Coupon ${q.couponCode} applied`);
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not apply this code'))
      .finally(() => setCouponBusy(false));
  };

  const removeCoupon = () => {
    setCoupon('');
    setCouponInput('');
    ApiService.cartQuote().then(setQuote).catch(() => {});
  };

  const validate = () => {
    const e = {};
    if (addr.fullName.trim().length < 2) e.fullName = 'Enter the recipient’s name';
    if (!/^[6-9]\d{9}$/.test(addr.phone)) e.phone = 'Enter a valid 10-digit mobile number';
    if (addr.line1.trim().length < 5) e.line1 = 'Enter house number and street';
    if (!/^[1-9]\d{5}$/.test(addr.pincode)) e.pincode = 'Enter a valid 6-digit PIN code';
    if (addr.city.trim().length < 2) e.city = 'Enter the city';
    if (!addr.state) e.state = 'Choose the state';
    if (composeAddress(addr).length > 500) e.line2 = 'Address is too long';
    setErrors(e);
    const first = Object.keys(e)[0];
    if (first) document.getElementById(`ck-${first}`)?.focus();
    return !first;
  };

  const placeOrder = (e) => {
    e?.preventDefault();
    if (isSubmitting || !validate()) return;
    setIsSubmitting(true);
    const payload = {
      shippingAddress: composeAddress(addr),
      phoneNumber: addr.phone,
      paymentMethod,
      couponCode: coupon || undefined,
      expectedTotal: quote?.total,
    };
    ApiService.checkout(payload, idempotencyKey.current)
      .then((order) => {
        try {
          if (remember) localStorage.setItem(addressKey(currentUser), JSON.stringify(addr));
          else localStorage.removeItem(addressKey(currentUser));
        } catch { /* storage unavailable */ }
        track.purchase(order);
        navigate('/order/success', { state: { order } });
      })
      .catch(err => {
        toast.error(err.response?.data?.message || 'Checkout failed');
        // Prices, stock or shipping may have changed: show the customer the current totals.
        ApiService.cartQuote(coupon || undefined).then(setQuote).catch(() => {
          setCoupon('');
          ApiService.cartQuote().then(setQuote).catch(() => {});
        });
        setIsSubmitting(false);
      });
  };

  if (loading) return <Loader message="Loading checkout details..." />;

  const hasItems = quote && quote.lines.length > 0;
  if (!hasItems) {
    return (
      <section className="panel" style={{ textAlign: 'center' }}>
        <h1>Your cart is empty</h1>
        <p>Add a saree to your cart to check out.</p>
        <Link className="btn btn-primary" to="/#products">Shop the collection</Link>
      </section>
    );
  }

  const field = (key, label, props = {}) => (
    <div className={`ck-field ${props.wide ? 'wide' : ''}`}>
      <label htmlFor={`ck-${key}`}>{label}{props.optional && <span className="opt"> (optional)</span>}</label>
      <input id={`ck-${key}`} value={addr[key]} onChange={set(key)} aria-invalid={!!errors[key]}
        aria-describedby={errors[key] ? `ck-${key}-err` : undefined} className={errors[key] ? 'input-invalid' : ''}
        {...props.input} />
      {errors[key] && <span className="field-error" id={`ck-${key}-err`} role="alert">{errors[key]}</span>}
      {key === 'pincode' && pinHint && !errors.pincode && <span className="ck-hint"><i className="bi bi-geo-alt" aria-hidden="true"></i> {pinHint}</span>}
    </div>
  );

  return (
    <div className="checkout">
      <ol className="ck-steps" aria-label="Checkout progress">
        <li className="done"><Link to="/cart">Cart</Link></li>
        <li className="current" aria-current="step">Delivery &amp; payment</li>
        <li>Confirmation</li>
      </ol>

      <form className="ck-grid" onSubmit={placeOrder} noValidate>
        <div className="ck-main">
          <section className="ck-card" aria-labelledby="ck-addr-title">
            <h2 id="ck-addr-title"><span className="ck-num">1</span> Delivery address</h2>
            <div className="ck-fields">
              {field('fullName', 'Full name', { input: { autoComplete: 'name', maxLength: 80 } })}
              {field('phone', 'Mobile number', { input: { autoComplete: 'tel-national', inputMode: 'numeric', maxLength: 10, placeholder: '10-digit mobile' } })}
              {field('line1', 'House no., building, street', { wide: true, input: { autoComplete: 'address-line1', maxLength: 160 } })}
              {field('line2', 'Area, landmark', { wide: true, optional: true, input: { autoComplete: 'address-line2', maxLength: 160 } })}
              {field('pincode', 'PIN code', { input: { autoComplete: 'postal-code', inputMode: 'numeric', maxLength: 6 } })}
              {field('city', 'City / district', { input: { autoComplete: 'address-level2', maxLength: 60 } })}
              <div className="ck-field wide">
                <label htmlFor="ck-state">State</label>
                <select id="ck-state" value={addr.state} onChange={set('state')} autoComplete="address-level1"
                  aria-invalid={!!errors.state} className={errors.state ? 'input-invalid' : ''}>
                  <option value="">Select state</option>
                  {INDIAN_STATES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                {errors.state && <span className="field-error" role="alert">{errors.state}</span>}
              </div>
            </div>
            <label className="ck-check">
              <input type="checkbox" checked={remember} onChange={e => setRemember(e.target.checked)} />
              Remember this address on this device
            </label>
          </section>

          <section className="ck-card" aria-labelledby="ck-pay-title">
            <h2 id="ck-pay-title"><span className="ck-num">2</span> Payment</h2>
            <div className="ck-pay" role="radiogroup" aria-label="Payment method">
              <label className={`ck-pay-opt ${paymentMethod === 'COD' ? 'on' : ''}`}>
                <input type="radio" name="pay" value="COD" checked={paymentMethod === 'COD'} onChange={() => setPaymentMethod('COD')} />
                <i className="bi bi-cash-coin" aria-hidden="true"></i>
                <span><strong>Cash on delivery</strong><small>Pay the courier when your saree arrives</small></span>
              </label>
              {options.online && (
                <label className={`ck-pay-opt ${paymentMethod === 'ONLINE' ? 'on' : ''}`}>
                  <input type="radio" name="pay" value="ONLINE" checked={paymentMethod === 'ONLINE'} onChange={() => setPaymentMethod('ONLINE')} />
                  <i className="bi bi-credit-card" aria-hidden="true"></i>
                  <span><strong>{options.testMode ? 'Online payment (test mode)' : 'Pay online'}</strong>
                    <small>{options.testMode ? 'No payment details are collected and no money moves' : 'UPI, cards and netbanking'}</small></span>
                </label>
              )}
            </div>
            {!options.online && <p className="ck-note">Online payment is coming soon.</p>}
          </section>
        </div>

        <aside className="ck-summary" aria-labelledby="ck-sum-title">
          <h2 id="ck-sum-title">Order summary</h2>
          <ul className="ck-lines">
            {quote.lines.map(line => (
              <li key={line.cartItemId}>
                <span className="ck-thumb"><img src={imageSrc(line.imageUrl)} alt="" width="56" height="70" /><b>{line.quantity}</b></span>
                <span className="ck-name">{line.name}</span>
                <span>{inr(line.lineNet)}</span>
              </li>
            ))}
          </ul>

          <div className="ck-coupon">
            {coupon ? (
              <div className="coupon-applied">
                <span><i className="bi bi-tag" aria-hidden="true"></i> {coupon} applied</span>
                <button type="button" className="text-link" onClick={removeCoupon}>Remove</button>
              </div>
            ) : (
              <div className="coupon-row">
                <label htmlFor="coupon" className="sr-only">Coupon code</label>
                <input id="coupon" value={couponInput} maxLength={40} autoCapitalize="characters" autoComplete="off"
                  placeholder="Coupon code" onChange={e => setCouponInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') applyCoupon(e); }} />
                <button type="button" className="btn btn-outline" disabled={couponBusy || !couponInput.trim()} onClick={applyCoupon}>
                  {couponBusy ? 'Checking…' : 'Apply'}
                </button>
              </div>
            )}
          </div>

          <dl className="ck-totals">
            <div><dt>Subtotal</dt><dd>{inr(quote.subtotal)}</dd></div>
            {Number(quote.discount) > 0 && <div className="save"><dt>Sale savings</dt><dd>−{inr(quote.discount)}</dd></div>}
            {Number(quote.couponDiscount) > 0 && <div className="save"><dt>Coupon {quote.couponCode}</dt><dd>−{inr(quote.couponDiscount)}</dd></div>}
            <div><dt>GST</dt><dd>{inr(quote.tax)}</dd></div>
            <div><dt>Shipping</dt><dd>{Number(quote.shipping) === 0 ? 'Free' : inr(quote.shipping)}</dd></div>
            <div className="grand"><dt>Total payable</dt><dd>{inr(quote.total)}</dd></div>
          </dl>

          <button type="submit" className="btn btn-primary ck-place" disabled={isSubmitting}>
            {isSubmitting ? 'Placing order…' : `Place order · ${inr(quote.total)}`}
          </button>
          <ul className="ck-trust">
            <li><i className="bi bi-shield-check" aria-hidden="true"></i> Prices and stock are confirmed by our server when you order</li>
            <li><i className="bi bi-arrow-repeat" aria-hidden="true"></i> Returns for damaged or incorrect items — <Link to="/shipping">policy</Link></li>
          </ul>
        </aside>

        <div className="ck-mobilebar">
          <div><small>Total</small><strong>{inr(quote.total)}</strong></div>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>{isSubmitting ? 'Placing…' : 'Place order'}</button>
        </div>
      </form>
    </div>
  );
}
