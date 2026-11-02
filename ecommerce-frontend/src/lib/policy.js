// Delivery and payment rules come from the server (store info), so the site never promises what the shop doesn't offer.

/** Orders above this ship free; null when the shop has no free shipping. */
export const freeShippingFrom = (info) => {
  const t = Number(info?.freeShippingThreshold);
  return t > 0 ? t : null;
};

/** True when customers can pay cash to the courier. */
export const codOffered = (info) => !!info?.cashOnDelivery;
