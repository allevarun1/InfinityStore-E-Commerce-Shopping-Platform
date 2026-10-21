import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useStore } from '../contexts/StoreContext';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';
import { whatsappLink } from './Growth';
import { CountUp } from './Motion';

const when = (iso) => (iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '');

/** Share a personal code; the friend saves on a first order and the sharer earns credit when it is delivered. */
export function Refer() {
  const [data, setData] = useState(null);
  const [failed, setFailed] = useState(false);
  const toast = useToast();
  const { info } = useStore();

  useEffect(() => {
    document.title = 'Refer a friend | InfinityStore';
    ApiService.referral().then(setData).catch(() => setFailed(true));
  }, []);

  if (failed) return <p className="refer-wrap muted">We could not load your referral code. Please try again shortly.</p>;
  if (!data) return <Loader />;

  const message = `I shop sarees at InfinityStore. Use my code ${data.code} for 10% off your first order: ${window.location.origin}`;
  const copy = () => navigator.clipboard?.writeText(data.code).then(() => toast.success('Code copied'), () => toast.error('Copy did not work, please select the code'));
  const share = () => (navigator.share ? navigator.share({ text: message }).catch(() => {}) : copy());
  const live = data.rewards.filter(r => !r.used);

  return (
    <div className="refer-wrap">
      <section className="refer-hero" aria-labelledby="refer-title">
        <span className="eyebrow">Refer a friend</span>
        <h1 id="refer-title">Gift a saree discount. Earn Rs. 200.</h1>
        <p>Your friend gets {data.friendOffer}. When their order is delivered, you get {data.reward}.</p>
        <div className="refer-code" aria-label="Your referral code">
          <code>{data.code}</code>
          <button type="button" className="btn btn-outline" onClick={copy}><i className="bi bi-clipboard" aria-hidden="true"></i> Copy</button>
        </div>
        <div className="refer-actions">
          <a className="btn btn-primary" target="_blank" rel="noopener noreferrer" href={whatsappLink('', message)}>
            <i className="bi bi-whatsapp" aria-hidden="true"></i> Share on WhatsApp</a>
          <button type="button" className="btn btn-outline" onClick={share}><i className="bi bi-share" aria-hidden="true"></i> Share</button>
        </div>
      </section>
      <section className="refer-earned" aria-labelledby="earned-title">
        <h2 id="earned-title">Your credits</h2>
        <p className="refer-stat"><strong><CountUp to={data.friendsRewarded} eager /></strong> friend{data.friendsRewarded === 1 ? '' : 's'} rewarded</p>
        {live.length === 0 ? <p className="muted">Nothing to use yet. Credits appear here after a friend’s order is delivered.</p> : (
          <ul className="refer-credits">
            {live.map(r => (
              <li key={r.code}><code>{r.code}</code><span>Rs. {Number(r.amount)} off an order above Rs. 1,499 · use by {when(r.expiresAt)}</span></li>
            ))}
          </ul>
        )}
        {info?.whatsappNumber && <p className="muted">Questions? <a className="text-link" href={whatsappLink(info.whatsappNumber, 'Hi InfinityStore, a question about referrals.')} target="_blank" rel="noopener noreferrer">Chat with us</a></p>}
      </section>
    </div>
  );
}
