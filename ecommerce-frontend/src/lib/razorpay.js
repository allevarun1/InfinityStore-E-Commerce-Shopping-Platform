// Online payment through Razorpay Checkout (UPI, cards, netbanking, wallets). The server creates the Razorpay
// order and decides the amount; this only opens Razorpay's window and hands the signed result back to the
// server, which verifies it before the order counts as paid. The script is loaded on first use.
import { ApiService } from '../services/api';

let script = null;

function loadCheckout() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (!script) {
    script = new Promise((resolve, reject) => {
      const tag = document.createElement('script');
      tag.src = 'https://checkout.razorpay.com/v1/checkout.js';
      tag.async = true;
      tag.onload = () => resolve(window.Razorpay);
      tag.onerror = () => { script = null; reject(new Error('Could not load the payment window. Please check your internet and try again.')); };
      document.body.appendChild(tag);
    });
  }
  return script;
}

/** Fetches Razorpay's script while the shopper is still on the checkout form, so the window opens at once. */
export function preloadCheckout() {
  loadCheckout().catch(() => {});
}

/** Thrown when the customer closes the payment window without paying. */
export class PaymentDismissed extends Error {}

/**
 * Takes payment for an online order that is waiting to be paid. Resolves with the updated (paid) order;
 * rejects with PaymentDismissed if the customer closes the window, or an Error with a readable message.
 */
export async function payForOrder(order, prefill = {}) {
  // Checkout already returns the payment details; only "Pay now" on an older order has to ask for them.
  const [Razorpay, session] = await Promise.all([loadCheckout(), order.payment || ApiService.payOrder(order.id)]);
  const response = await new Promise((resolve, reject) => {
    const rzp = new Razorpay({
      key: session.keyId,
      order_id: session.providerOrderId,
      amount: session.amountPaise,
      currency: session.currency,
      name: 'Rekha Shopping Mall',
      description: `Order ${order.trackingNumber}`,
      prefill: { name: prefill.name || '', email: prefill.email || '', contact: prefill.phone ? `+91${prefill.phone}` : '' },
      notes: { order: order.trackingNumber },
      theme: { color: '#7b1e2b' },
      handler: resolve,
      modal: { ondismiss: () => reject(new PaymentDismissed('Payment was not completed')), confirm_close: true },
    });
    // A declined card or failed UPI keeps the window open so the customer can retry; nothing to do here.
    rzp.open();
  });
  return ApiService.verifyPayment(order.id, {
    razorpayOrderId: response.razorpay_order_id,
    razorpayPaymentId: response.razorpay_payment_id,
    razorpaySignature: response.razorpay_signature,
  });
}
