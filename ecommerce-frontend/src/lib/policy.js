// Delivery and payment rules come from the server (store info), so the site never promises what the shop doesn't offer.

/** Orders above this ship free; null when the shop has no free shipping. */
export const freeShippingFrom = (info) => {
  const t = Number(info?.freeShippingThreshold);
  return t > 0 ? t : null;
};

/** True when customers can pay cash to the courier. */
export const codOffered = (info) => !!info?.cashOnDelivery;

/** The shop's sale policy for every saree: once an order is paid it is final. Shown on saree pages, checkout and the policy page. */
export const NO_RETURNS_SHORT = 'All sales are final: no returns, no exchanges and no refunds once your order is paid.';
export const NO_RETURNS_POINTS = [
  'All sales are final. Once an order is paid, we do not accept returns or exchanges, and we do not give refunds.',
  'This applies to every saree in our shop, including sale and coupon prices.',
  'Please check the photos, colour, fabric and price carefully before you pay. Message us before ordering if you have any question.',
  'Handwoven sarees may show small variations in weave or colour. These are marks of craftsmanship, not defects.',
  'You can cancel an order only while it is still waiting for payment.',
];
