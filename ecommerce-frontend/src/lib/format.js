// Shared money and pricing helpers for display only. The server remains the authority
// for every amount that is charged (see PricingService on the backend).

export const inr = (value, { decimals = 2 } = {}) =>
  `₹${Number(value || 0).toLocaleString('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}`;

/** Whole rupees when the amount has no paise, otherwise two decimals. */
export const inrCompact = (value) => {
  const n = Number(value || 0);
  return inr(n, { decimals: Number.isInteger(n) ? 0 : 2 });
};

export const isOnDeal = (p) =>
  Boolean(p?.deal && p.dealPrice != null && Number(p.dealPrice) < Number(p.price));

export const activePrice = (p) => (isOnDeal(p) ? Number(p.dealPrice) : Number(p.price));

export const discountPercent = (p) =>
  isOnDeal(p) ? Math.round(((p.price - p.dealPrice) / p.price) * 100) : 0;

export const LOW_STOCK = 5;
