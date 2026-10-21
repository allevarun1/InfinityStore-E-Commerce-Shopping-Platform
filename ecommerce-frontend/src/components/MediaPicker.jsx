import React, { useEffect, useMemo, useState } from 'react';
import { ApiService } from '../services/api';
import { imageSrc } from '../lib/format';
import { kb } from '../lib/imageTools';

/** Modal that lets the owner pick a photo or video from the library. */
export function MediaPicker({ kind, onPick, onClose }) {
  const [items, setItems] = useState(null);
  const [q, setQ] = useState('');
  useEffect(() => {
    ApiService.adminMedia().then(setItems).catch(() => setItems([]));
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  const shown = useMemo(() => (items || []).filter(a => (!kind || a.kind === kind) && a.name.toLowerCase().includes(q.toLowerCase())), [items, kind, q]);
  return (
    <div className="picker-back" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="picker" role="dialog" aria-modal="true" aria-label="Choose from your library">
        <header>
          <h2>Your library{kind === 'VIDEO' ? ': videos' : kind === 'IMAGE' ? ': photos' : ''}</h2>
          <input type="search" placeholder="Search by name" value={q} onChange={e => setQ(e.target.value)} aria-label="Search the library" />
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
        </header>
        {items === null && <p className="admin-hint">Loading…</p>}
        {items && shown.length === 0 && <p className="admin-hint">Nothing here yet. Upload files under <strong>Media library</strong> first.</p>}
        <ul className="picker-grid">
          {shown.map(a => (
            <li key={a.id}>
              <button type="button" onClick={() => onPick(a)}>
                {a.kind === 'VIDEO' ? <video src={imageSrc(a.url)} muted preload="metadata" /> : <img src={imageSrc(a.url)} alt="" loading="lazy" />}
                <span>{a.name}<small>{a.kind === 'VIDEO' ? 'Video' : 'Photo'} · {kb(a.sizeBytes)}</small></span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
