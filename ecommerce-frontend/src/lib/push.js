// The shop owner's phone notifications through the browser's own Web Push (Android, Windows, Mac, and iPhone once
// the shop is added to the Home Screen): "New paid order" the moment a customer pays. Customers get none.
import { ApiService } from '../services/api';

const toBytes = (b64) => {
  const s = atob((b64 + '='.repeat((4 - (b64.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(s, (c) => c.charCodeAt(0));
};

export const pushSupported = () =>
  typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;

const isIos = () => /iPhone|iPad|iPod/.test(navigator.userAgent);
const standalone = () => window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true;

/** iPhones only allow notifications for sites added to the Home Screen. */
export const needsHomeScreen = () => typeof navigator !== 'undefined' && isIos() && !standalone();

async function registration() {
  const existing = await navigator.serviceWorker.getRegistration('/');
  return existing || navigator.serviceWorker.register('/sw.js', { scope: '/' });
}

/** 'on' | 'off' | 'blocked' | 'unsupported' | 'ios-install' for this browser. */
export async function pushState() {
  if (needsHomeScreen()) return 'ios-install';
  if (!pushSupported()) return 'unsupported';
  if (Notification.permission === 'denied') return 'blocked';
  const reg = await navigator.serviceWorker.getRegistration('/');
  const sub = reg && (await reg.pushManager.getSubscription());
  return sub && Notification.permission === 'granted' ? 'on' : 'off';
}

/** Asks permission (must run from a tap) and registers this device for the owner's new-order alerts. */
export async function enablePush() {
  if (!pushSupported()) throw new Error('This browser cannot show notifications');
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error(permission === 'denied'
    ? 'Notifications are blocked. Allow them for this site in your browser settings, then try again.'
    : 'Notifications were not allowed');
  const reg = await registration();
  await navigator.serviceWorker.ready;
  const key = await ApiService.pushKey();
  let sub = await reg.pushManager.getSubscription();
  if (sub) {
    // A subscription made with a different key cannot receive our messages: replace it.
    const current = sub.options?.applicationServerKey;
    const same = current && btoa(String.fromCharCode(...new Uint8Array(current))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '') === key;
    if (!same) {
      try { await ApiService.pushUnsubscribe(sub.endpoint); } catch { /* the server forgets it later anyway */ }
      await sub.unsubscribe();
      sub = null;
    }
  }
  if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toBytes(key) });
  await ApiService.pushSubscribe(sub.toJSON());
  return sub;
}

/**
 * Quietly re-registers this device when notifications are already allowed, so the server always knows it
 * (and a subscription made with an old signing key is replaced). Never asks for permission.
 */
export async function syncPush() {
  if (!pushSupported() || Notification.permission !== 'granted') return false;
  const reg = await navigator.serviceWorker.getRegistration('/');
  if (!reg || !(await reg.pushManager.getSubscription())) return false;
  await enablePush();
  return true;
}

export async function disablePush() {
  const reg = await navigator.serviceWorker.getRegistration('/');
  const sub = reg && (await reg.pushManager.getSubscription());
  if (!sub) return;
  try { await ApiService.pushUnsubscribe(sub.endpoint); } catch { /* still turn it off here */ }
  await sub.unsubscribe();
}

export async function testPush() {
  const reg = await navigator.serviceWorker.getRegistration('/');
  const sub = reg && (await reg.pushManager.getSubscription());
  if (!sub) throw new Error('Turn notifications on first');
  return ApiService.pushTest(sub.endpoint);
}
