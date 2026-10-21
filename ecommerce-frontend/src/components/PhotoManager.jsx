import React, { useRef, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { imageSrc } from '../lib/format';
import { enhance, loadImage, shrinkForUpload, kb } from '../lib/imageTools';
import { MediaPicker } from './MediaPicker';

const LEGACY = /^\/api\/products\/\d+\/(image|gallery\/\d+)/; // photos stored inside the product row by older versions
const MAX_PHOTOS = 7; // the cover plus up to six extra photos, the same limits the server enforces

/** Fill the 4:5 frame when the photo is already close to it, otherwise keep the whole saree over a soft backdrop. */
async function autoMode(file) {
  const im = await loadImage(file);
  const r = (im.naturalWidth || im.width) / (im.naturalHeight || im.height);
  return r > 0.68 && r < 0.95 ? 'cover' : 'contain';
}

/**
 * The photos of one saree: drag in or pick any number of pictures, each is made crisp and stored in the library, drag to
 * reorder, the first is the cover. Enhance can be run again on any picture.
 */
export function PhotoManager({ cover, gallery = [], onChange, label = 'Photos' }) {
  const toast = useToast();
  const [busy, setBusy] = useState([]);
  const [auto, setAuto] = useState(true);
  const [picker, setPicker] = useState(false);
  const [over, setOver] = useState(false);
  const [link, setLink] = useState('');
  const drag = useRef(null);
  const input = useRef(null);
  const photos = [cover, ...gallery].filter(Boolean);

  const commit = (list) => onChange({ imageUrl: list[0] || '', gallery: list.slice(1, MAX_PHOTOS) });

  // Old photos are addressed by position, so after a reorder such a link could point at a different picture. Move them into
  // the library first (once) and every photo then has a link that never changes.
  const reorder = async (build) => {
    let list = photos;
    if (list.some(u => LEGACY.test(u))) {
      const done = note('Moving older photos into your library');
      try {
        list = await Promise.all(list.map(async (u) => {
          if (!LEGACY.test(u)) return u;
          const blob = await (await fetch(imageSrc(u))).blob();
          return (await ApiService.uploadMedia(new File([blob], 'photo.' + (blob.type.split('/')[1] || 'jpg'), { type: blob.type }))).url;
        }));
      } catch (e) {
        toast.error('Could not move the older photos. Please try again.');
        return;
      } finally { done(); }
    }
    commit(build(list));
  };
  const note = (text) => { setBusy(b => [...b, text]); return () => setBusy(b => b.filter(x => x !== text)); };

  const addFiles = async (files) => {
    const imgs = [...files].filter(f => f.type.startsWith('image/'));
    if (imgs.length < files.length) toast.error('Only photos can be added here (use the Media library for videos)');
    let list = [...photos];
    for (const file of imgs) {
      if (list.length >= MAX_PHOTOS) { toast.error(`A saree can have up to ${MAX_PHOTOS} photos`); break; }
      const done = note(`${file.name}${auto ? ': enhancing and uploading' : ': uploading'}`);
      try {
        let toSend;
        if (auto) {
          const r = await enhance(file, { mode: await autoMode(file) });
          toSend = new File([r.blob], file.name.replace(/\.[^.]+$/, '') + (r.blob.type === 'image/webp' ? '.webp' : '.jpg'), { type: r.blob.type });
        } else {
          toSend = await shrinkForUpload(file);
        }
        const asset = await ApiService.uploadMedia(toSend);
        list = [...list, asset.url];
        commit(list);
      } catch (e) {
        toast.error(e.response?.data?.message || e.message || `Could not add ${file.name}`);
      } finally { done(); }
    }
  };

  const enhanceOne = async (i) => {
    const done = note(`Photo ${i + 1}: enhancing`);
    try {
      const r = await enhance(imageSrc(photos[i]), { mode: 'cover' });
      const asset = await ApiService.uploadMedia(new File([r.blob], `enhanced-${Date.now()}.${r.blob.type === 'image/webp' ? 'webp' : 'jpg'}`, { type: r.blob.type }));
      const next = [...photos];
      next[i] = asset.url;
      commit(next);
      const gain = r.sharpBefore > 0 ? Math.round((r.sharpAfter / r.sharpBefore - 1) * 100) : 0;
      toast.success(`Enhanced to ${r.width}×${r.height}${gain > 0 ? `, ${gain}% crisper` : ''} (${kb(r.blob.size)})`);
    } catch (e) {
      toast.error(e.response?.data?.message || e.message || 'Could not enhance that photo');
    } finally { done(); }
  };

  const move = (from, to) => {
    if (to < 0 || to >= photos.length || from === to) return;
    reorder((list) => {
      const next = [...list];
      const [m] = next.splice(from, 1);
      next.splice(to, 0, m);
      return next;
    });
  };

  const addLink = () => {
    const url = link.trim();
    if (!/^https:\/\/\S+$/.test(url)) { toast.error('Paste a link that starts with https://'); return; }
    if (photos.length >= MAX_PHOTOS) { toast.error(`A saree can have up to ${MAX_PHOTOS} photos`); return; }
    commit([...photos, url]);
    setLink('');
  };

  return (
    <div className="photos" role="group" aria-label={label}>
      <div
        className={`dropzone ${over ? 'over' : ''}`}
        onDragOver={(e) => { if (e.dataTransfer.types.includes('Files')) { e.preventDefault(); setOver(true); } }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { if (e.dataTransfer.files.length) { e.preventDefault(); setOver(false); addFiles(e.dataTransfer.files); } }}>
        <i className="bi bi-cloud-arrow-up" aria-hidden="true"></i>
        <div>
          <strong>Drop photos here</strong>
          <span>or</span>
          <button type="button" className="btn btn-outline" onClick={() => input.current?.click()}>Choose photos</button>
          <button type="button" className="btn btn-outline" onClick={() => setPicker(true)}>From library</button>
        </div>
        <input ref={input} type="file" accept="image/*" multiple hidden onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
        <label className="switch"><input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} /> <span>Make every new photo crisp (recommended)</span></label>
      </div>

      {busy.length > 0 && <ul className="photo-busy" aria-live="polite">{busy.map(b => <li key={b}><span className="spin" aria-hidden="true"></span>{b}</li>)}</ul>}

      {photos.length > 0 && (
        <ul className="photo-grid">
          {photos.map((u, i) => (
            <li key={u + i} draggable onDragStart={() => { drag.current = i; }} onDragOver={(e) => { if (drag.current !== null) e.preventDefault(); }}
              onDrop={(e) => { if (drag.current !== null) { e.preventDefault(); move(drag.current, i); drag.current = null; } }}
              className={i === 0 ? 'is-cover' : ''}>
              <img src={imageSrc(u)} alt={i === 0 ? 'Cover photo' : `Photo ${i + 1}`} loading="lazy" />
              {i === 0 && <span className="cover-tag">Cover</span>}
              <div className="photo-tools">
                <button type="button" onClick={() => move(i, i - 1)} disabled={i === 0} aria-label={`Move photo ${i + 1} earlier`} title="Move earlier"><i className="bi bi-arrow-left" aria-hidden="true"></i></button>
                <button type="button" onClick={() => move(i, i + 1)} disabled={i === photos.length - 1} aria-label={`Move photo ${i + 1} later`} title="Move later"><i className="bi bi-arrow-right" aria-hidden="true"></i></button>
                {i > 0 && <button type="button" onClick={() => move(i, 0)} aria-label={`Make photo ${i + 1} the cover`} title="Make cover"><i className="bi bi-star" aria-hidden="true"></i></button>}
                <button type="button" onClick={() => enhanceOne(i)} aria-label={`Enhance photo ${i + 1}`} title="Make it crisper"><i className="bi bi-stars" aria-hidden="true"></i></button>
                <button type="button" className="danger" onClick={() => reorder((list) => list.filter((_, k) => k !== i))} aria-label={`Remove photo ${i + 1}`} title="Remove"><i className="bi bi-trash" aria-hidden="true"></i></button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <p className="admin-hint">{photos.length} of {MAX_PHOTOS} photos. The first one is the cover shoppers see on the card. Drag to reorder.</p>

      <div className="photo-link">
        <input type="url" placeholder="…or paste an https:// link to a photo" value={link} onChange={e => setLink(e.target.value)} aria-label="Photo link" />
        <button type="button" className="btn btn-outline" onClick={addLink}>Add link</button>
      </div>

      {picker && <MediaPicker kind="IMAGE" onClose={() => setPicker(false)} onPick={(a) => {
        setPicker(false);
        if (photos.length >= MAX_PHOTOS) { toast.error(`A saree can have up to ${MAX_PHOTOS} photos`); return; }
        commit([...photos, a.url]);
      }} />}
    </div>
  );
}
