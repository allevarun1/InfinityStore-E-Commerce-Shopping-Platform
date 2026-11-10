import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useStore } from '../contexts/StoreContext';
import { whatsappLink } from './Growth';
import { codOffered, NO_RETURNS_POINTS } from '../lib/policy';
import { setHead } from '../lib/head';
import { INFO_PAGES } from '../../seo/meta.js';

const PRIVACY = 'Rekha Shopping Mall respects your privacy and is committed to protecting your personal information. When you use our platform, we may collect basic details such as your name, email address, contact information, order history, and usage data to improve your shopping experience. This information is used to process orders, provide customer support, enhance website performance, and ensure account security. If you subscribe to our newsletter we use your email address to send offers and new-arrival updates, and you can unsubscribe at any time. We do not sell or share your personal data with unauthorized third parties. However, limited information may be shared with trusted partners such as payment gateways for secure transactions and delivery services for order fulfillment. We use essential cookies to maintain session data and, only if you accept, analytics and advertising cookies to understand how the store is used and to measure our advertising. By using Rekha Shopping Mall, you agree to the collection and use of your information as described. For any privacy-related concerns, you can contact us using the details on our Contact page.';

const TERMS = 'By using Rekha Shopping Mall, you agree to follow our terms and conditions. You are responsible for maintaining the confidentiality of your account and activities under it. While we aim to provide accurate product information, slight variations in descriptions or images may occur. Orders paid online are confirmed after successful payment; cash-on-delivery orders are confirmed when placed and paid on delivery. Rekha Shopping Mall reserves the right to cancel orders in cases of fraud, technical issues, or stock unavailability. Delivery times may vary depending on location and courier services. All sales are final: once an order is paid, it cannot be returned or exchanged and no refund is given. This applies to every product, including sale and coupon prices, and customers confirm it at checkout before paying. Coupon codes are subject to their stated conditions and may be withdrawn. Users must not misuse the platform, attempt unauthorized access, or engage in fraudulent activities. Rekha Shopping Mall may update these terms at any time without prior notice.';

function Shipping({ info }) {
  return (
    <>
      <h2>Shipping &amp; delivery</h2>
      <ul>
        <li>A delivery charge may apply to your order. The exact delivery charge is shown in your cart and at checkout, before you pay.</li>
        {codOffered(info)
          ? <li>Cash on delivery is available. Pay the courier when your saree arrives.</li>
          : <li>Orders are paid securely online at checkout. We don’t offer cash on delivery.</li>}
        <li>Delivery times vary by location and courier.</li>
        <li>Track the progress of every order under <Link to="/orders">Your orders</Link>; we also email you when it is placed.</li>
      </ul>
      <h2 id="returns">No returns, no refunds</h2>
      <ul>
        {NO_RETURNS_POINTS.map(t => <li key={t}>{t}</li>)}
      </ul>
      <p>Questions? See our <Link to="/contact">Contact</Link> page or the full <Link to="/terms">Terms</Link>.</p>
    </>
  );
}

function Contact({ info }) {
  const email = info?.supportEmail || import.meta.env.VITE_SUPPORT_EMAIL;
  const phone = info?.supportPhone;
  const wa = info?.whatsappNumber;
  return (
    <>
      <p>We’re happy to help you choose a saree or track an order. Please quote your order number if you have one.</p>
      <ul>
        {wa && <li><i className="bi bi-whatsapp" aria-hidden="true"></i> <a href={whatsappLink(wa, 'Hi Rekha Shopping Mall, I need help.')} target="_blank" rel="noopener noreferrer">Chat on WhatsApp</a></li>}
        {phone && <li><i className="bi bi-telephone" aria-hidden="true"></i> <a href={`tel:${phone.replace(/\s+/g, '')}`}>{phone}</a></li>}
        {email && <li><i className="bi bi-envelope" aria-hidden="true"></i> <a href={`mailto:${email}`}>{email}</a></li>}
        {!wa && !phone && !email && <li>Contact details will be published here shortly.</li>}
      </ul>
    </>
  );
}

const TITLES = {
  privacy: 'Privacy Policy',
  terms: 'Terms and Conditions',
  shipping: 'Shipping & No-Returns Policy',
  contact: 'Contact us',
};

export function StaticPage({ page }) {
  const { info } = useStore();
  const title = TITLES[page] || 'Rekha Shopping Mall';
  useEffect(() => {
    setHead({ title: `${title} | Rekha Shopping Mall`, description: INFO_PAGES[`/${page}`]?.description, path: `/${page}` });
  }, [title, page]);

  return (
    <section className="panel static-page">
      <h1>{title}</h1>
      {page === 'privacy' && <p>{PRIVACY}</p>}
      {page === 'terms' && <p>{TERMS}</p>}
      {page === 'shipping' && <Shipping info={info} />}
      {page === 'contact' && <Contact info={info} />}
    </section>
  );
}
