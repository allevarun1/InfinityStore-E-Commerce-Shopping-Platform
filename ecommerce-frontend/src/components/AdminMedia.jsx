import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { imageSrc } from '../lib/format';
import { kb, shrinkForUpload } from '../lib/imageTools';

const MAX_VIDEO = 20 * 1024 * 1024;

/** The owner's library of photos and videos: upload several at once, preview, copy a link, delete what is unused. */
export function AdminMedia({ onStudio }) {
  const toast = useToast();
  const [items, setItems] = useState(null);
  const [filter, setFilter] = useState('ALL');
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState({});
  const [over, setOver] = useState(false);
  const [sure, setSure] = useState(null);
  const input = useRef(null);

  const load = () => ApiService.adminMedia().then(setItems).catch(() => { setItems([]); toast.error('Could not load the library'); });
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const upload = async (files) => {
    for (const file of [...files]) {
      const key = `${file.name}-${file.size}`;
      const isVideo = file.type.startsWith('video/');
      if (!isVideo && !file.type.startsWith('image/')) { toast.error(`${file.name}: use a photo or an MP4/WebM video`); continue; }
      if (isVideo && file.size > MAX_VIDEO) { toast.error(`${file.name}: videos can be up to 20 MB. Shorten or compress it first.`); continue; }
      setBusy(b => ({ ...b, [key]: { name: file.name, p: 0 } }));
      try {
        const body = isVideo ? file : await shrinkForUpload(file);
        await ApiService.uploadMedia(body, (p) => setBusy(b => ({ ...b, [key]: { name: file.name, p } })));
        await load();
      } catch (e) {
        toast.error(e.response?.data?.message || `Could not upload ${file.name}`);
      } finally {
        setBusy(b => { const n = { ...b }; delete n[key]; return n; });
      }
    }
  };

  const remove = async (a) => {
    try {
      await ApiService.deleteMedia(a.id);
      toast.success('Removed from your library');
      setSure(null);
      load();
    } catch (e) {
      toast.error(e.response?.data?.message || 'Could not remove that file');
      setSure(null);
    }
  };

  const copy = (a) => navigator.clipboard?.writeText(imageSrc(a.url)).then(() => toast.success('Link copied'), () => toast.error('Copy did not work'));
  const shown = useMemo(() => (items || []).filter(a => (filter === 'ALL' || a.kind === filter) && a.name.toLowerCase().includes(q.toLowerCase())), [items, filter, q]);
  const photos = (items || []).filter(a => a.kind === 'IMAGE').length;

  return (
    <div className="tab-content media-lib">
      <h2>Media library</h2>
      <p className="admin-hint">Every photo and video you upload lives here, ready to use on any saree, reel or pop-up. Photos up to 8 MB, videos (MP4 or WebM) up to 20 MB.</p>

      <div className={`dropzone ${over ? 'over' : ''}`} onDragOver={(e) => { e.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); upload(e.dataTransfer.files); }}>
        <i className="bi bi-cloud-arrow-up" aria-hidden="true"></i>
        <div>
          <strong>Drop photos and videos here</strong>
          <span>or</span>
          <button type="button" className="btn btn-primary" onClick={() => input.current?.click()}>Choose files</button>
        </div>
        <input ref={input} type="file" accept="image/*,video/mp4,video/webm" multiple hidden onChange={(e) => { upload(e.target.files); e.target.value = ''; }} />
      </div>

      {Object.entries(busy).length > 0 && (
        <ul className="photo-busy" aria-live="polite">
          {Object.entries(busy).map(([k, b]) => <li key={k}><span className="spin" aria-hidden="true"></span>{b.name}: {Math.round(b.p * 100)}%</li>)}
        </ul>
      )}

      <div className="toolbar">
        <div className="chips" role="group" aria-label="Show">
          {[['ALL', `All (${(items || []).length})`], ['IMAGE', `Photos (${photos})`], ['VIDEO', `Videos (${(items || []).length - photos})`]].map(([k, l]) => (
            <button key={k} type="button" className={filter === k ? 'on' : ''} aria-pressed={filter === k} onClick={() => setFilter(k)}>{l}</button>
          ))}
        </div>
        <input type="search" placeholder="Search by name" value={q} onChange={e => setQ(e.target.value)} aria-label="Search the library" />
      </div>

      {items === null && <p className="admin-hint">Loading…</p>}
      {items && shown.length === 0 && <p className="ap-empty">{items.length === 0 ? 'Nothing uploaded yet. Drop your first photo above.' : 'No files match.'}</p>}
      <ul className="media-grid">
        {shown.map(a => (
          <li key={a.id} className="media-card">
            <div className="media-thumb">
              {a.kind === 'VIDEO' ? <video src={imageSrc(a.url)} muted preload="metadata" controls /> : <img src={imageSrc(a.url)} alt={a.name} loading="lazy" />}
              <span className="kind">{a.kind === 'VIDEO' ? 'Video' : 'Photo'}</span>
            </div>
            <div className="media-meta"><strong title={a.name}>{a.name}</strong><small>{kb(a.sizeBytes)}</small></div>
            <div className="media-actions">
              <button type="button" onClick={() => copy(a)}><i className="bi bi-link-45deg" aria-hidden="true"></i> Copy link</button>
              {a.kind === 'IMAGE' && onStudio && <button type="button" onClick={() => onStudio(a)}><i className="bi bi-stars" aria-hidden="true"></i> Studio</button>}
              {sure === a.id
                ? <><button type="button" className="danger-btn" onClick={() => remove(a)}>Yes, delete</button><button type="button" onClick={() => setSure(null)}>Keep</button></>
                : <button type="button" className="danger-btn" onClick={() => setSure(a.id)} aria-label={`Delete ${a.name}`}><i className="bi bi-trash" aria-hidden="true"></i></button>}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
