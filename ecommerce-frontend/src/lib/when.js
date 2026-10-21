// Small display helpers for the admin follow-up screens.

/** "5 min ago". Server times carry no zone, so compare them with the server's own clock when it is known. */
export function ago(value, serverNow) {
  if (!value) return '';
  const now = serverNow ? new Date(serverNow).getTime() : Date.now();
  return minutesAgo(Math.round((now - new Date(value).getTime()) / 60000));
}

export function minutesAgo(mins) {
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const h = Math.round(mins / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return d === 1 ? 'yesterday' : `${d} days ago`;
}

export const waLink = (phone, text) => `https://wa.me/91${phone}?text=${encodeURIComponent(text)}`;
