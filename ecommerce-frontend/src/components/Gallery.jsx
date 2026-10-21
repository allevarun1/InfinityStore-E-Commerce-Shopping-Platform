import React, { useEffect, useRef, useState } from 'react';
import { imageSrc } from '../lib/format';

/**
 * Product photos: thumbnails + hover zoom on desktop, swipeable slides with dots on touch screens,
 * and a full-screen viewer on click/tap. Falls back to the single main image.
 */
export function ProductGallery({ product }) {
  const photos = [product.imageUrl, ...(product.gallery || [])].filter(Boolean).map(imageSrc);
  const [index, setIndex] = useState(0);
  const [zoom, setZoom] = useState(null);
  const [viewer, setViewer] = useState(false);
  const track = useRef(null);

  useEffect(() => { setIndex(0); track.current?.scrollTo({ left: 0 }); }, [product.id]);

  const go = (i) => {
    const n = (i + photos.length) % photos.length;
    setIndex(n);
    const el = track.current;
    if (el) el.scrollTo({ left: n * el.clientWidth, behavior: 'smooth' });
  };

  const onScroll = () => {
    const el = track.current;
    if (!el) return;
    const n = Math.round(el.scrollLeft / el.clientWidth);
    if (n !== index) setIndex(n);
  };

  const moveZoom = (e) => {
    if (e.pointerType && e.pointerType !== 'mouse') return;
    const r = e.currentTarget.getBoundingClientRect();
    setZoom(`${((e.clientX - r.left) / r.width) * 100}% ${((e.clientY - r.top) / r.height) * 100}%`);
  };

  return (
    <div className={`gallery ${photos.length > 1 ? 'has-thumbs' : ''}`}>
      {photos.length > 1 && (
        <div className="gallery-thumbs" role="tablist" aria-label="Product photos">
          {photos.map((src, i) => (
            <button key={src + i} type="button" role="tab" aria-selected={i === index} aria-label={`Photo ${i + 1} of ${photos.length}`}
              className={i === index ? 'on' : ''} onClick={() => go(i)}>
              <img src={src} alt="" loading="lazy" width="80" height="100" />
            </button>
          ))}
        </div>
      )}

      <div className="gallery-stage">
        <div className="gallery-track" ref={track} onScroll={onScroll}>
          {photos.map((src, i) => (
            <button key={src + i} type="button" className={`gallery-slide ${zoom && i === index ? 'zoomed' : ''}`}
              onPointerMove={moveZoom} onPointerLeave={() => setZoom(null)} onClick={() => { setIndex(i); setViewer(true); }}
              aria-label={`Open photo ${i + 1} full screen`}>
              <img src={src} alt={i === 0 ? product.name : `${product.name}, photo ${i + 1}`} data-fly-src={product.id} width="900" height="1125"
                loading={i === 0 ? 'eager' : 'lazy'} style={zoom && i === index ? { transformOrigin: zoom } : undefined} />
            </button>
          ))}
        </div>
        {photos.length > 1 && (
          <>
            <button type="button" className="gallery-arrow prev" aria-label="Previous photo" onClick={() => go(index - 1)}><i className="bi bi-chevron-left" aria-hidden="true"></i></button>
            <button type="button" className="gallery-arrow next" aria-label="Next photo" onClick={() => go(index + 1)}><i className="bi bi-chevron-right" aria-hidden="true"></i></button>
            <div className="gallery-dots" aria-hidden="true">
              {photos.map((_, i) => <span key={i} className={i === index ? 'on' : ''}></span>)}
            </div>
          </>
        )}
        <span className="gallery-hint" aria-hidden="true"><i className="bi bi-arrows-fullscreen"></i></span>
      </div>

      {viewer && <PhotoViewer photos={photos} start={index} name={product.name} onClose={(i) => { setViewer(false); go(i); }} />}
    </div>
  );
}

function PhotoViewer({ photos, start, name, onClose }) {
  const [i, setI] = useState(start);
  const closeRef = useRef(null);
  const touch = useRef(null);

  useEffect(() => {
    const opener = document.activeElement;
    closeRef.current?.focus();
    document.body.classList.add('no-scroll');
    const onKey = (e) => {
      if (e.key === 'Escape') onClose(i);
      if (e.key === 'ArrowRight') setI(n => (n + 1) % photos.length);
      if (e.key === 'ArrowLeft') setI(n => (n - 1 + photos.length) % photos.length);
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('no-scroll');
      opener?.focus?.();
    };
  }, [i, onClose, photos.length]);

  return (
    <div className="viewer" role="dialog" aria-modal="true" aria-label={`${name} photos`}
      onTouchStart={e => { touch.current = e.touches[0].clientX; }}
      onTouchEnd={e => {
        const dx = e.changedTouches[0].clientX - (touch.current ?? 0);
        if (Math.abs(dx) > 40) setI(n => (n + (dx < 0 ? 1 : -1) + photos.length) % photos.length);
      }}>
      <button ref={closeRef} type="button" className="viewer-close" onClick={() => onClose(i)} aria-label="Close"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
      <img src={photos[i]} alt={`${name}, photo ${i + 1} of ${photos.length}`} />
      {photos.length > 1 && (
        <>
          <button type="button" className="viewer-nav prev" aria-label="Previous photo" onClick={() => setI(n => (n - 1 + photos.length) % photos.length)}><i className="bi bi-chevron-left" aria-hidden="true"></i></button>
          <button type="button" className="viewer-nav next" aria-label="Next photo" onClick={() => setI(n => (n + 1) % photos.length)}><i className="bi bi-chevron-right" aria-hidden="true"></i></button>
          <span className="viewer-count">{i + 1} / {photos.length}</span>
        </>
      )}
    </div>
  );
}

/** Admin editor for the extra photos: upload several at once, paste a link, reorder, remove. */
export function GalleryEditor({ value, preview, onChange }) {
  const list = value || [];
  const shown = (i) => (preview && preview[i]) || imageSrc(list[i]);
  const [link, setLink] = useState('');

  const addFiles = (files) => {
    const room = 6 - list.length;
    const picked = [...files].slice(0, Math.max(0, room));
    Promise.all(picked.map(f => new Promise((res) => {
      const r = new FileReader();
      r.onloadend = () => res(r.result);
      r.readAsDataURL(f);
    }))).then(urls => onChange([...list, ...urls]));
  };
  const move = (i, d) => {
    const next = [...list];
    const j = i + d;
    if (j < 0 || j >= next.length) return;
    [next[i], next[j]] = [next[j], next[i]];
    onChange(next);
  };

  return (
    <div className="field-group gallery-editor">
      <label htmlFor="gallery-files">More photos <span className="admin-hint">(up to 6: drape, pallu, border, blouse, close-ups)</span></label>
      {list.length > 0 && (
        <ul>
          {list.map((u, i) => (
            <li key={i}>
              <img src={shown(i)} alt={`Extra photo ${i + 1}`} />
              <div>
                <button type="button" onClick={() => move(i, -1)} disabled={i === 0} aria-label={`Move photo ${i + 1} earlier`}><i className="bi bi-arrow-left" aria-hidden="true"></i></button>
                <button type="button" onClick={() => move(i, 1)} disabled={i === list.length - 1} aria-label={`Move photo ${i + 1} later`}><i className="bi bi-arrow-right" aria-hidden="true"></i></button>
                <button type="button" onClick={() => onChange(list.filter((_, k) => k !== i))} aria-label={`Remove photo ${i + 1}`}><i className="bi bi-trash" aria-hidden="true"></i></button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {list.length < 6 && (
        <div className="gallery-add">
          <input id="gallery-files" type="file" multiple accept="image/png,image/jpeg,image/webp,image/gif"
            onChange={e => { addFiles(e.target.files); e.target.value = ''; }} />
          <div className="gallery-link">
            <input type="url" placeholder="…or paste an image link (https://)" value={link} onChange={e => setLink(e.target.value)} aria-label="Image link" />
            <button type="button" className="btn btn-outline" disabled={!/^https?:\/\//.test(link.trim())}
              onClick={() => { onChange([...list, link.trim()]); setLink(''); }}>Add</button>
          </div>
        </div>
      )}
    </div>
  );
}
