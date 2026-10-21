import { MediaPicker } from './MediaPicker';
import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';

const BLANK = { title: '', caption: '', kind: 'REEL', videoUrl: '', posterUrl: '', productId: '' };

/** Storefront videos: a wide "how it is made" story, short vertical reels, and clips tied to one saree. */
export function AdminVideos({ products = [] }) {
  const [list, setList] = useState([]);
  const [form, setForm] = useState(BLANK);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = () => ApiService.adminVideos().then(setList).catch(() => toast.error('Could not load videos'));
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const add = (e) => {
    e.preventDefault();
    setBusy(true);
    ApiService.createVideo({ ...form, posterUrl: form.posterUrl || null, productId: form.productId ? Number(form.productId) : null })
      .then(() => { toast.success('Video added. It is live on the store now.'); setForm(BLANK); return load(); })
      .catch(err => toast.error(err.response?.data?.message || Object.values(err.response?.data?.errors || {})[0] || 'Could not add the video'))
      .finally(() => setBusy(false));
  };
  const act = (fn, msg) => fn().then(() => { toast.success(msg); return load(); }).catch(() => toast.error('That did not work'));
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const [pick, setPick] = useState(null);

  return (
    <div className="tab-content videos-admin">
      <h2>Videos</h2>
      <p className="admin-hint">
        Videos make sarees come alive. Upload a clip to your <strong>Media library</strong> and pick it with <em>From library</em>, or paste a link from a video host (YouTube, Cloudinary, Bunny or Cloudflare R2).
        Use a direct <strong>.mp4 or .webm</strong> link for clips that autoplay silently, or a <strong>YouTube</strong> link.
        Keep clips short (under 15 seconds) and light (under 4 MB); phone-shaped 9:16 works best for reels.
      </p>
      <form className="videos-form" onSubmit={add}>
        <div className="field-group"><label htmlFor="v-title">Title</label>
          <input id="v-title" required maxLength={120} value={form.title} onChange={set('title')} placeholder="From loom to you" /></div>
        <div className="field-group"><label htmlFor="v-kind">Where it shows</label>
          <select id="v-kind" value={form.kind} onChange={set('kind')}>
            <option value="STORY">Story: wide, in the "Behind the weave" section</option>
            <option value="REEL">Reel: short vertical clip in "Sarees in motion"</option>
          </select></div>
        <div className="field-group wide"><label htmlFor="v-url">Video link (https)</label>
          <div className="with-btn"><input id="v-url" type="text" inputMode="url" required maxLength={500} value={form.videoUrl} onChange={set('videoUrl')} placeholder="https://…/weaving.mp4, https://youtu.be/… or pick one you uploaded" />
            <button type="button" className="btn btn-outline" onClick={() => setPick('VIDEO')}>From library</button></div></div>
        <div className="field-group wide"><label htmlFor="v-poster">Cover image link <span className="admin-hint">(optional, shown before it plays)</span></label>
          <div className="with-btn"><input id="v-poster" type="text" inputMode="url" maxLength={500} value={form.posterUrl} onChange={set('posterUrl')} placeholder="https://…/cover.jpg" />
            <button type="button" className="btn btn-outline" onClick={() => setPick('IMAGE')}>From library</button></div></div>
        <div className="field-group"><label htmlFor="v-prod">Link to a saree <span className="admin-hint">(optional)</span></label>
          <select id="v-prod" value={form.productId} onChange={set('productId')}>
            <option value="">None</option>
            {products.filter(p => p.status === 'PUBLISHED' || !p.status).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select></div>
        <div className="field-group"><label htmlFor="v-cap">Caption <span className="admin-hint">(optional)</span></label>
          <input id="v-cap" maxLength={240} value={form.caption} onChange={set('caption')} placeholder="One line about the weave" /></div>
        <button type="submit" className="btn btn-primary" disabled={busy}>Add video</button>
      </form>

      {pick && <MediaPicker kind={pick} onClose={() => setPick(null)} onPick={(a) => { setForm({ ...form, [pick === 'VIDEO' ? 'videoUrl' : 'posterUrl']: a.url, ...(pick === 'VIDEO' && !form.title ? { title: a.name.replace(/\.[^.]+$/, '') } : {}) }); setPick(null); }} />}

      {list.length === 0 ? <p className="ap-empty">No videos yet. Until you add one, shoppers see an animated loom in "Behind the weave".</p> : (
        <ul className="offers-list videos-list">
          {list.map(v => (
            <li key={v.id} className={v.active ? '' : 'off'}>
              <strong>{v.title}<small>{v.kind === 'STORY' ? 'Story' : 'Reel'}{v.productName ? ` · ${v.productName}` : ''}{v.youtubeId ? ' · YouTube' : ''}</small></strong>
              <span>{v.active ? 'Live' : 'Paused'}</span>
              <button type="button" onClick={() => act(() => ApiService.setVideoActive(v.id, !v.active), v.active ? 'Paused' : 'Live again')}>{v.active ? 'Pause' : 'Resume'}</button>
              <button type="button" className="danger-btn" onClick={() => act(() => ApiService.deleteVideo(v.id), 'Video removed')}>Remove</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
