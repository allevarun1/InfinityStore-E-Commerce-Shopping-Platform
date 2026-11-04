/* Rekha Shopping Mall: phone notifications only. This worker never caches pages or handles requests, so the
   site always loads fresh; it just shows the order notifications the shop sends and opens the right page. */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let n = {};
  try { n = event.data ? event.data.json() : {}; } catch { n = { body: event.data ? event.data.text() : '' }; }
  const title = n.title || 'Rekha Shopping Mall';
  event.waitUntil(self.registration.showNotification(title, {
    body: n.body || '',
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    tag: n.tag || undefined,
    renotify: !!n.tag,
    requireInteraction: !!n.requireInteraction,
    vibrate: [200, 100, 200],
    data: { url: n.url || '/' },
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href;
  event.waitUntil((async () => {
    const tabs = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const tab of tabs) {
      if (new URL(tab.url).origin === self.location.origin) {
        await tab.focus();
        if ('navigate' in tab) return tab.navigate(url);
        return undefined;
      }
    }
    return self.clients.openWindow(url);
  })());
});
