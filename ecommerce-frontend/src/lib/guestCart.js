// Cart for visitors who have not signed in yet. Lives in this browser only; it is merged into the
// customer's server cart at sign-in, where stock and price are validated by the backend.
const KEY = 'infinity_guest_cart';
const EVENT = 'infinity-guest-cart';
export const MAX_PER_LINE = 10;

function read() {
  try {
    const items = JSON.parse(localStorage.getItem(KEY) || '[]');
    return Array.isArray(items)
      ? items.filter(i => Number.isInteger(i.productId) && Number.isInteger(i.quantity) && i.quantity > 0)
      : [];
  } catch {
    return [];
  }
}

function write(items) {
  try {
    localStorage.setItem(KEY, JSON.stringify(items));
  } catch {
    // storage unavailable (private mode); the cart simply does not persist
  }
  window.dispatchEvent(new Event(EVENT));
}

export const guestCart = {
  items: read,
  count: () => read().reduce((n, i) => n + i.quantity, 0),
  add(productId, quantity = 1, stock = MAX_PER_LINE) {
    const items = read();
    const cap = Math.min(MAX_PER_LINE, stock);
    const line = items.find(i => i.productId === productId);
    if (line) line.quantity = Math.min(cap, line.quantity + quantity);
    else items.push({ productId, quantity: Math.min(cap, quantity) });
    write(items);
  },
  setQuantity(productId, quantity) {
    write(read().map(i => (i.productId === productId ? { ...i, quantity } : i)));
  },
  remove(productId) {
    write(read().filter(i => i.productId !== productId));
  },
  clear() {
    write([]);
  },
  subscribe(listener) {
    window.addEventListener(EVENT, listener);
    window.addEventListener('storage', listener);
    return () => {
      window.removeEventListener(EVENT, listener);
      window.removeEventListener('storage', listener);
    };
  },
};
