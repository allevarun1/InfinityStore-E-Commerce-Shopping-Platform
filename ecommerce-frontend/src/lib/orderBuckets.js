// Which orders the owner should act on, and which were only checkout attempts.
//
// A real order = the customer paid online (or chose cash on delivery and did not cancel). A paid order that the
// shop later cancelled stays a real order, so it is never lost from the list.
// Everything else is an attempt: the customer opened the payment window but did not pay, the payment failed,
// it timed out after 45 minutes, or they cancelled before paying. Attempts are shown only in the analytics view.

const PAID = new Set(['PAID', 'REFUNDED']);

export const isPlacedOrder = (o) => (o.paymentMethod === 'COD' ? o.status !== 'CANCELLED' : PAID.has(o.paymentStatus));

/** Why an attempt did not become an order. */
export function attemptReason(o) {
  if (o.status === 'CANCELLED' && o.paymentStatus === 'FAILED') return 'TIMED_OUT';
  if (o.status === 'CANCELLED') return 'CANCELLED';
  if (o.paymentStatus === 'FAILED') return 'FAILED';
  return 'PAYING';
}

export const REASONS = {
  PAYING: { label: 'Payment window open', hint: 'Started paying in the last 45 minutes. It becomes an order the moment they pay.', tone: 'wait' },
  TIMED_OUT: { label: 'Left without paying', hint: 'Closed the payment window or never finished. Cancelled automatically after 45 minutes.', tone: 'off' },
  CANCELLED: { label: 'Cancelled before paying', hint: 'The customer cancelled the order from their account.', tone: 'off' },
  FAILED: { label: 'Payment failed', hint: 'The bank or UPI app declined the payment.', tone: 'off' },
};

export function splitOrders(orders) {
  const placed = [];
  const attempts = [];
  for (const o of orders || []) (isPlacedOrder(o) ? placed : attempts).push(o);
  return { placed, attempts };
}
