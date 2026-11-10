// Ready-to-send WhatsApp messages and a WhatsApp Status image for any saree, plus order update messages.
// Everything opens WhatsApp with the text filled in; the owner presses send, so nothing goes out by itself.
import { activePrice, discountPercent, imageSrc, inr, isOnDeal } from './format';
import { loadImage } from './imageTools';

export const STORE = 'Rekha Shopping Mall';
const money = (v) => inr(v, { decimals: 0 });

/** A link to the saree on the shop, tagged so analytics can tell which WhatsApp message brought the visit. */
export function productLink(p, campaign = 'share') {
  return `${window.location.origin}/products/${p.id}?utm_source=whatsapp&utm_medium=message&utm_campaign=${campaign}`;
}

/** wa.me link. With a phone it opens that chat; without one WhatsApp asks who to send it to (a chat, group or broadcast list). */
export function waLink(phone, text) {
  const to = phone ? (String(phone).replace(/\D/g, '').length === 10 ? `91${String(phone).replace(/\D/g, '')}` : String(phone).replace(/\D/g, '')) : '';
  return `https://wa.me/${to}?text=${encodeURIComponent(text)}`;
}

export const firstName = (name) => (name || '').trim().split(/\s+/)[0] || '';

function priceLine(p) {
  if (isOnDeal(p)) return `*${money(p.dealPrice)}* (was ${money(p.price)}, ${discountPercent(p)}% off)`;
  return `*${money(p.price)}*`;
}

function details(p) {
  return p.category?.name || '';
}

export const TEMPLATES = [
  { key: 'new', label: 'New arrival', tag: 'NEW ARRIVAL' },
  { key: 'deal', label: 'Offer', tag: (p) => (isOnDeal(p) ? `${discountPercent(p)}% OFF` : 'SPECIAL PRICE') },
  { key: 'back', label: 'Back in stock', tag: 'BACK IN STOCK' },
  { key: 'festive', label: 'Festive pick', tag: 'FESTIVE PICK' },
  { key: 'few', label: 'Only a few left', tag: (p) => (p.stockQuantity > 0 && p.stockQuantity <= 5 ? `ONLY ${p.stockQuantity} LEFT` : 'SELLING FAST') },
];
export const tagFor = (key, p) => { const t = TEMPLATES.find(x => x.key === key)?.tag; return typeof t === 'function' ? t(p) : t; };
export const defaultTemplate = (p) => (isOnDeal(p) ? 'deal' : 'new');

/** The message for one saree. `to` personalises the greeting and, when we know why they care, the first line. */
export function productMessage(p, key = 'new', to = null) {
  const hi = to?.name ? `Hi ${firstName(to.name)}! ` : '';
  const saved = to?.reasons?.includes('Saved this saree');
  const waiting = to?.reasons?.includes('Asked to hear when it is back');
  const d = details(p);
  const open = {
    new: `${hi}✨ Just arrived at ${STORE}:`,
    deal: `${hi}🎉 ${isOnDeal(p) ? `${discountPercent(p)}% off today` : 'A special price today'} at ${STORE}:`,
    back: `${hi}Good news, it's back in stock:`,
    festive: `${hi}🪔 Our festive pick for you:`,
    few: `${hi}Only ${p.stockQuantity > 0 && p.stockQuantity <= 5 ? p.stockQuantity : 'a few'} left of this one:`,
  }[key] || `${hi}From ${STORE}:`;
  const lead = saved ? `${hi}The saree you saved is waiting for you${isOnDeal(p) ? `, and it's ${discountPercent(p)}% off today` : ''}:`
    : waiting && key !== 'new' ? `${hi}You asked us to tell you, and it's back in stock:` : open;
  return [
    lead,
    '',
    `*${p.name}*`,
    d,
    priceLine(p),
    '',
    `See it here: ${productLink(p, key)}`,
    '',
    'Reply to this message to reserve it.',
  ].filter((line, i, all) => line !== '' || all[i - 1] !== '').join('\n').replace(/\n{3,}/g, '\n\n');
}

/** One message listing several sarees, for "this week's new arrivals". */
export function collectionMessage(list, heading = `✨ New this week at ${STORE}`) {
  const lines = [heading, ''];
  list.slice(0, 8).forEach((p, i) => {
    lines.push(`${i + 1}. *${p.name}*: ${isOnDeal(p) ? `${money(p.dealPrice)} (${discountPercent(p)}% off)` : money(p.price)}`);
    lines.push(`   ${productLink(p, 'collection')}`);
  });
  lines.push('', `Everything: ${window.location.origin}/?utm_source=whatsapp&utm_medium=message&utm_campaign=collection#products`, '', 'Reply with the number you like and we will keep it for you. Cash on delivery available.');
  return lines.join('\n');
}

/** Instagram / Facebook caption. */
export function captionFor(p, key = 'new') {
  const tag = tagFor(key, p);
  const cat = (p.category?.name || 'saree').replace(/\s+/g, '');
  return `${tag ? `${tag.charAt(0)}${tag.slice(1).toLowerCase()} · ` : ''}${p.name}\n${details(p)}\n${isOnDeal(p) ? `${money(p.dealPrice)} (was ${money(p.price)})` : money(p.price)}\n\nShop: ${productLink(p, 'instagram').replace('utm_source=whatsapp', 'utm_source=instagram')}\n\n#saree #${cat} #sareelove #RekhaShoppingMall`;
}

// ---------- order updates ----------

const itemsOf = (o) => (o.orderItems || []).map(i => `${i.product?.name || i.productName || 'Saree'}${i.quantity > 1 ? ` × ${i.quantity}` : ''}`).join(', ');
const nameOf = (o) => firstName(o.user?.firstName || o.shippingAddress || '');

/** The messages that fit where an order is now. Each has a label for the button and the text. */
export function orderMessages(o) {
  const hi = nameOf(o) ? `Hi ${nameOf(o)}, ` : 'Hi, ';
  const track = `${window.location.origin}/track?o=${encodeURIComponent(o.trackingNumber)}`;
  const total = money(o.totalPrice);
  const out = [];
  if (o.status === 'PENDING' && o.paymentMethod === 'COD') {
    out.push({ key: 'confirm', label: 'Confirm cash on delivery', text: `${hi}thank you for your order at ${STORE}! 🙏\n\nOrder ${o.trackingNumber}: ${itemsOf(o)}\nTotal ${total}, cash on delivery.\n\nPlease reply *YES* to confirm and we will pack it today. If anything should change (address, colour), just tell us here.` });
  } else if (o.status === 'PENDING' || o.status === 'CONFIRMED') {
    out.push({ key: 'thanks', label: 'Thank and confirm', text: `${hi}thank you for shopping with ${STORE}! 🙏 Your order ${o.trackingNumber} (${itemsOf(o)}) is confirmed and being packed. We'll send you the tracking as soon as it ships.` });
  }
  if (o.status === 'SHIPPED') {
    out.push({ key: 'shipped', label: 'Shipped, with tracking', text: `${hi}your order ${o.trackingNumber} is on its way! 🚚\n\nTrack it here: ${track}\n\n${o.paymentMethod === 'COD' ? `Please keep ${total} ready for the delivery person.` : 'It is fully paid, nothing to pay at the door.'}` });
  }
  if (o.status === 'DELIVERED') {
    const first = o.orderItems?.[0]?.product;
    out.push({ key: 'review', label: 'Ask for a review', text: `${hi}we hope you love your saree! 💛\n\nCould you spare a minute to tell other shoppers what you think?${first ? `\n${window.location.origin}/products/${first.id}#reviews` : ''}\n\nAnd if you send us a photo of how you draped it, we'd love to feature you (only with your permission).` });
    out.push({ key: 'again', label: 'Invite back', text: `${hi}thank you again for shopping with ${STORE}. We've added new sarees this week, have a look: ${window.location.origin}/?utm_source=whatsapp&utm_medium=message&utm_campaign=repeat#products` });
  }
  out.push({ key: 'note', label: 'Write my own', text: `${hi}this is ${STORE} about your order ${o.trackingNumber}. ` });
  return out;
}

/** "Haven't seen you in a while" for the customer list. */
export function winBackMessage(c) {
  const hi = c.name ? `Hi ${firstName(c.name)}, ` : 'Hi, ';
  const fav = c.favouriteCategory ? c.favouriteCategory.toLowerCase() : 'sarees';
  return `${hi}it's ${STORE}. 🙏 We've just added new ${fav} that we think you'll love: ${window.location.origin}/?utm_source=whatsapp&utm_medium=message&utm_campaign=winback#products\n\nReply here and we'll send you photos of anything you like.`;
}

// ---------- "sent" marks, so nobody gets the same saree twice ----------

const SENT = 'rsm_sent_v1';
const readSent = () => { try { return JSON.parse(localStorage.getItem(SENT) || '{}'); } catch { return {}; } };
export function markSent(scope, phone) {
  try { const all = readSent(); all[`${scope}:${phone}`] = Date.now(); localStorage.setItem(SENT, JSON.stringify(all)); } catch { /* private mode */ }
}
export function sentAt(scope, phone) { return readSent()[`${scope}:${phone}`] || null; }

// ---------- WhatsApp Status / Instagram story image ----------

let fontsReady = null;
const ensureFonts = () => (fontsReady ||= Promise.all([
  document.fonts?.load('700 80px "Cormorant Garamond"'), document.fonts?.load('600 40px "DM Sans Variable"'),
  document.fonts?.load('400 90px "Great Vibes"'),
].filter(Boolean)).catch(() => {}));

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}

function wrap(ctx, text, maxW) {
  const words = text.split(' '); const lines = []; let line = '';
  for (const w of words) { const t = line ? `${line} ${w}` : w; if (ctx.measureText(t).width > maxW && line) { lines.push(line); line = w; } else line = t; }
  if (line) lines.push(line);
  return lines.slice(0, 2);
}

/** 1080 x 1920 story card: brand, the saree photo, its name and price, and where to order. Returns a PNG blob. */
export async function statusImage(p, key = 'new') {
  await ensureFonts();
  const W = 1080, H = 1920;
  const c = document.createElement('canvas'); c.width = W; c.height = H;
  const ctx = c.getContext('2d');
  const bg = ctx.createLinearGradient(0, 0, W, H); bg.addColorStop(0, '#fcf4ea'); bg.addColorStop(0.55, '#fbeee6'); bg.addColorStop(1, '#f6dcd8');
  ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
  ctx.strokeStyle = 'rgba(181,134,47,0.55)'; ctx.lineWidth = 2; roundRect(ctx, 36, 36, W - 72, H - 72, 36); ctx.stroke();

  try { const logo = await loadImage('/brand/rekha-signature.svg'); const lw = 520, lh = lw * logo.height / logo.width; ctx.drawImage(logo, (W - lw) / 2, 92, lw, lh); }
  catch { ctx.fillStyle = '#7b1e2b'; ctx.font = '700 64px "Cormorant Garamond", serif'; ctx.textAlign = 'center'; ctx.fillText('REKHA SHOPPING MALL', W / 2, 160); }

  const px = 110, py = 300, pw = W - 220, ph = 1100;
  ctx.save(); roundRect(ctx, px, py, pw, ph, 40); ctx.clip();
  ctx.fillStyle = '#efe4d8'; ctx.fillRect(px, py, pw, ph);
  try {
    const img = await loadImage(imageSrc(p.imageUrl));
    const s = Math.max(pw / img.width, ph / img.height); const iw = img.width * s, ih = img.height * s;
    ctx.drawImage(img, px + (pw - iw) / 2, py + (ph - ih) * 0.3, iw, ih);
  } catch { /* no photo: keep the soft panel */ }
  ctx.restore();
  ctx.strokeStyle = 'rgba(255,255,255,0.8)'; ctx.lineWidth = 3; roundRect(ctx, px + 18, py + 18, pw - 36, ph - 36, 28); ctx.stroke();

  const tag = tagFor(key, p);
  if (tag) {
    ctx.font = '700 34px "DM Sans Variable", "DM Sans", sans-serif'; ctx.textAlign = 'center';
    const tw = ctx.measureText(tag).width + 70;
    const g = ctx.createLinearGradient(W / 2 - tw / 2, 0, W / 2 + tw / 2, 0); g.addColorStop(0, '#d81b60'); g.addColorStop(1, '#8e1050');
    ctx.fillStyle = g; roundRect(ctx, W / 2 - tw / 2, py - 34, tw, 68, 34); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.fillText(tag, W / 2, py + 12);
  }

  ctx.textAlign = 'center'; ctx.fillStyle = '#3b0d17';
  ctx.font = '700 76px "Cormorant Garamond", serif';
  const lines = wrap(ctx, p.name, W - 200);
  lines.forEach((l, i) => ctx.fillText(l, W / 2, 1500 + i * 80));
  let y = 1500 + lines.length * 80 + 10;
  const d = details(p);
  if (d) { ctx.font = '500 34px "DM Sans Variable", "DM Sans", sans-serif'; ctx.fillStyle = 'rgba(59,13,23,0.7)'; ctx.fillText(d, W / 2, y); y += 70; }
  ctx.font = '700 64px "DM Sans Variable", "DM Sans", sans-serif'; ctx.fillStyle = '#8e1050';
  const price = money(activePrice(p));
  if (isOnDeal(p)) {
    const was = money(p.price);
    ctx.font = '700 64px "DM Sans Variable", "DM Sans", sans-serif'; const a = ctx.measureText(price).width;
    ctx.font = '500 40px "DM Sans Variable", "DM Sans", sans-serif'; const b = ctx.measureText(was).width;
    const x0 = W / 2 - (a + 24 + b) / 2;
    ctx.textAlign = 'left'; ctx.font = '700 64px "DM Sans Variable", "DM Sans", sans-serif'; ctx.fillText(price, x0, y);
    ctx.font = '500 40px "DM Sans Variable", "DM Sans", sans-serif'; ctx.fillStyle = 'rgba(59,13,23,0.55)'; ctx.fillText(was, x0 + a + 24, y);
    ctx.fillRect(x0 + a + 24, y - 14, b, 3); ctx.textAlign = 'center';
  } else ctx.fillText(price, W / 2, y);

  ctx.font = '600 32px "DM Sans Variable", "DM Sans", sans-serif'; ctx.fillStyle = '#7b1e2b';
  ctx.fillText('Reply to order', W / 2, H - 150);
  ctx.font = '500 30px "DM Sans Variable", "DM Sans", sans-serif'; ctx.fillStyle = 'rgba(59,13,23,0.6)';
  ctx.fillText(window.location.host, W / 2, H - 100);
  return new Promise((resolve) => c.toBlob(resolve, 'image/png'));
}

/** Phone share sheet with the picture and text (WhatsApp, Status, Instagram...). False when the browser cannot. */
export async function sharePicture(blob, name, text) {
  const file = new File([blob], `${name.replace(/[^\w-]+/g, '-').toLowerCase()}.png`, { type: 'image/png' });
  if (!navigator.canShare?.({ files: [file] })) return false;
  try { await navigator.share({ files: [file], text }); return true; } catch (e) { return e?.name === 'AbortError'; }
}
