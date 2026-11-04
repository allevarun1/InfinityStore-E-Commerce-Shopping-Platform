import React, { useEffect, useState } from 'react';
import { useToast } from '../contexts/ToastContext';
import { ApiService } from '../services/api';
import { pushState, enablePush, disablePush, testPush, syncPush } from '../lib/push';

const TEXT = {
  title: 'New order alerts on this phone',
  off: 'Get a notification, like an app, the moment a customer pays, so you can pack and ship it. Turn it on once on each phone you use.',
  on: 'This phone gets a notification for every new paid order.',
};

/** The owner's new-order alerts: turns phone notifications on or off for this browser (admin pages only). */
export function NotifyCard({ className = '' }) {
  const toast = useToast();
  const [state, setState] = useState(null);
  const [busy, setBusy] = useState(false);
  const [count, setCount] = useState(null);
  const t = TEXT;

  const refresh = () => {
    pushState().then(setState).catch(() => setState('unsupported'));
    ApiService.adminPushDevices().then(setCount).catch(() => {});
  };
  // On open, make sure the server still has this device (it is the owner's only alert channel).
  useEffect(() => { syncPush().catch(() => {}).finally(refresh); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const run = (fn, ok) => {
    setBusy(true);
    fn().then((r) => { toast.success(r?.message || ok); refresh(); })
      .catch((e) => toast.error(e.response?.data?.message || e.message || 'That did not work'))
      .finally(() => setBusy(false));
  };

  if (state === null) return null;

  return (
    <section className={`notify-card ${state === 'on' ? 'on' : ''} ${className}`} aria-labelledby="notify-title">
      <i className={`bi ${state === 'on' ? 'bi-bell-fill' : 'bi-bell'}`} aria-hidden="true"></i>
      <div className="notify-text">
        <h3 id="notify-title">{t.title} {state === 'on' && <span className="chip-status live">ON</span>}</h3>
        {state === 'on' && <p>{t.on}</p>}
        {state === 'off' && <p>{t.off}</p>}
        {state === 'blocked' && <p>Notifications are blocked for this site. Tap the lock icon next to the address, allow <strong>Notifications</strong>, then reload this page.</p>}
        {state === 'ios-install' && <p>On iPhone, first tap <strong>Share</strong> → <strong>Add to Home Screen</strong>, open the shop from the new icon, then turn this on.</p>}
        {state === 'unsupported' && <p>This browser cannot show notifications. Open this page in <strong>Chrome</strong> on your phone.</p>}
        {count != null && <p className="admin-hint">{count === 0 ? 'No phone gets order alerts yet.' : `${count} ${count === 1 ? 'device gets' : 'devices get'} order alerts.`}</p>}
      </div>
      <div className="notify-actions">
        {state === 'off' && <button type="button" className="btn btn-primary" disabled={busy} onClick={() => run(enablePush, 'Notifications are on')}>{busy ? 'Turning on…' : 'Turn on notifications'}</button>}
        {state === 'on' && (
          <>
            <button type="button" className="btn btn-outline" disabled={busy} onClick={() => run(testPush, 'Test sent')}>Send a test</button>
            <button type="button" className="btn btn-outline" disabled={busy} onClick={() => run(disablePush, 'Notifications are off on this device')}>Turn off</button>
          </>
        )}
      </div>
    </section>
  );
}
