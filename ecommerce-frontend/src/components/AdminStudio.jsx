import React, { useEffect, useRef, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { imageSrc } from '../lib/format';
import { enhance, ENHANCE_DEFAULTS, kb, loadImage } from '../lib/imageTools';
import { makeReel, reelCover, reelFormat, REEL } from '../lib/reelMaker';
import { MediaPicker } from './MediaPicker';

const cover = (p) => p?.previewImage || p?.imageUrl || '';

/** Copy-and-paste briefs for an AI image tool, written from what the saree really is. */
function promptsFor(p) {
  const fabric = [p.colour, p.weave, p.fabric].filter(Boolean).join(' ') || 'handwoven silk';
  const saree = `${fabric} saree${p.region ? ` from ${p.region}` : ''}`;
  const border = p.fabric?.toLowerCase().includes('cotton') ? 'a crisp woven border' : 'an intricate gold zari border and rich pallu';
  const base = 'photorealistic editorial photograph, natural skin texture, true-to-life fabric colour, no text, no logo, no watermark, portrait 4:5';
  return [
    { title: 'Full-length catalogue shot', text: `Full-length photograph of an elegant Indian woman in her 30s wearing a ${saree}, classic nivi drape with the pallu over the left shoulder, ${border} clearly visible, standing in a softly lit ivory studio, 85mm lens, soft window light, ${base}` },
    { title: 'Close-up on the weave', text: `Extreme close-up of the ${saree}: the ${border} catching warm light, the texture of the threads visible, the model's hand lightly holding the pallu, shallow depth of field, cinematic colour grade, ${base}` },
    { title: 'Festive lifestyle', text: `An Indian woman laughing softly in a sunlit courtyard during a ${p.occasion || 'festive'} morning, wearing a ${saree}, marigold garlands and brass lamps blurred behind her, golden hour, candid editorial style, ${base}` },
  ];
}

const slug = (s) => (s || 'saree').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) || 'saree';

/** Photo and video studio: make a photo crisp, turn it into a cinematic reel, and write briefs for AI model photos. */
export function AdminStudio({ products, initial, initialProductId, onUseOnProduct, onVideosChanged }) {
  const toast = useToast();
  const [src, setSrc] = useState(null); // { file?, url, name }
  const [productId, setProductId] = useState('');
  const [picker, setPicker] = useState(false);
  const [opts, setOpts] = useState({ ...ENHANCE_DEFAULTS, mode: 'cover' });
  const [working, setWorking] = useState(false);
  const [result, setResult] = useState(null);
  const [split, setSplit] = useState(50);
  const [reel, setReel] = useState({ title: '', subtitle: '', focus: 'A', A: [0.5, 0.3], B: [0.5, 0.72], pct: 0, blob: null, url: '', making: false, publishing: false });
  const live = useRef(null);
  const file = useRef(null);
  const product = products.find(p => String(p.id) === String(productId));

  const choose = (s) => { setSrc(s); setResult(null); setReel(r => ({ ...r, blob: null, url: '' })); };
  useEffect(() => { if (initialProductId) setProductId(initialProductId); }, [initialProductId]);
  useEffect(() => { if (initial) choose({ url: imageSrc(initial.url), name: initial.name }); }, [initial]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!product) return;
    setReel(r => ({ ...r, title: r.title || product.name, subtitle: r.subtitle || [product.fabric, product.region].filter(Boolean).join('  |  ') || 'Handwoven' }));
    if (!src && cover(product)) choose({ url: imageSrc(cover(product)), name: product.name });
  }, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const run = async () => {
    setWorking(true);
    try {
      const r = await enhance(src.file || src.url, opts);
      setResult(r);
      setSplit(50);
    } catch (e) {
      toast.error(e.message || 'Could not enhance that photo');
    } finally { setWorking(false); }
  };

  const saveToLibrary = async () => {
    try {
      const asset = await ApiService.uploadMedia(new File([result.blob], `${slug(product?.name || src.name)}-enhanced.${result.blob.type === 'image/webp' ? 'webp' : 'jpg'}`, { type: result.blob.type }));
      toast.success('Saved to your library');
      return asset;
    } catch (e) {
      toast.error(e.response?.data?.message || 'Could not save');
      return null;
    }
  };

  const useOnProduct = async (mode) => {
    if (!product) { toast.error('Choose the saree first'); return; }
    const asset = await saveToLibrary();
    if (asset) onUseOnProduct(product.id, asset.url, mode);
  };

  const startReel = async () => {
    setReel(r => ({ ...r, making: true, pct: 0, blob: null, url: '' }));
    try {
      const image = await loadImage(result?.blob || src.file || src.url);
      const blob = await makeReel(image, { title: reel.title, subtitle: reel.subtitle, focusA: reel.A, focusB: reel.B }, {
        onProgress: (pct) => setReel(r => ({ ...r, pct })),
        onFrame: (c) => { const t = live.current; if (t) { t.getContext('2d').drawImage(c, 0, 0); } },
      });
      setReel(r => ({ ...r, making: false, blob, url: URL.createObjectURL(blob) }));
    } catch (e) {
      setReel(r => ({ ...r, making: false }));
      toast.error(e.message || 'Could not make the reel');
    }
  };

  const publishReel = async () => {
    if (!product) { toast.error('Choose the saree this reel is for'); return; }
    setReel(r => ({ ...r, publishing: true }));
    try {
      const ext = reel.blob.type.includes('mp4') ? 'mp4' : 'webm';
      const image = await loadImage(result?.blob || src.file || src.url);
      const video = await ApiService.uploadMedia(new File([reel.blob], `${slug(product.name)}-reel.${ext}`, { type: reel.blob.type }));
      const posterBlob = await reelCover(image, { title: reel.title, subtitle: reel.subtitle, focusA: reel.A, focusB: reel.B });
      const poster = await ApiService.uploadMedia(new File([posterBlob], `${slug(product.name)}-reel-cover.jpg`, { type: 'image/jpeg' }));
      await ApiService.createVideo({ title: reel.title || product.name, caption: reel.subtitle, kind: 'REEL', videoUrl: video.url, posterUrl: poster.url, productId: product.id });
      toast.success('Published. It now appears in "Sarees in motion" on your home page.');
      onVideosChanged?.();
    } catch (e) {
      toast.error(e.response?.data?.message || e.message || 'Could not publish the reel');
    } finally { setReel(r => ({ ...r, publishing: false })); }
  };

  const place = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const pt = [Math.min(0.95, Math.max(0.05, (e.clientX - rect.left) / rect.width)), Math.min(0.95, Math.max(0.05, (e.clientY - rect.top) / rect.height))];
    setReel(r => ({ ...r, [r.focus]: pt }));
  };

  const gain = result && result.sharpBefore > 0 ? Math.round((result.sharpAfter / result.sharpBefore - 1) * 100) : 0;
  const format = reelFormat();
  const prompts = product ? promptsFor(product) : null;

  return (
    <div className="tab-content studio">
      <h2>Photo &amp; video studio</h2>
      <p className="admin-hint">Make any saree photo crisp and catalogue-ready, turn it into a cinematic close-up reel, and get ready-made briefs for AI model photos. Everything runs in your browser; nothing is saved until you press a Save or Publish button.</p>

      <section className="card studio-step">
        <h3><span className="step-no">1</span> Choose a photo</h3>
        <div className="studio-pick">
          <button type="button" className="btn btn-outline" onClick={() => file.current?.click()}><i className="bi bi-upload" aria-hidden="true"></i> Upload a photo</button>
          <button type="button" className="btn btn-outline" onClick={() => setPicker(true)}><i className="bi bi-images" aria-hidden="true"></i> From library</button>
          <div className="field-group inline">
            <label htmlFor="st-prod">Saree</label>
            <select id="st-prod" value={productId} onChange={e => setProductId(e.target.value)}>
              <option value="">Choose a saree…</option>
              {products.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </div>
          <input ref={file} type="file" accept="image/*" hidden onChange={(e) => { const f = e.target.files[0]; if (f) choose({ file: f, url: URL.createObjectURL(f), name: f.name }); e.target.value = ''; }} />
        </div>
        {src ? <p className="admin-hint">Using <strong>{src.name}</strong></p> : <p className="ap-empty">Pick a saree (its cover photo loads) or upload any picture to begin.</p>}
      </section>

      {src && (
        <section className="card studio-step">
          <h3><span className="step-no">2</span> Make it crisp</h3>
          <div className="studio-grid">
            <div className="studio-controls">
              <div className="field-group"><label htmlFor="st-sharp">Sharpness <output>{Math.round(opts.sharpen * 100)}</output></label>
                <input id="st-sharp" type="range" min="0" max="1.5" step="0.05" value={opts.sharpen} onChange={e => setOpts({ ...opts, sharpen: Number(e.target.value) })} /></div>
              <div className="field-group"><label htmlFor="st-col">Colour richness <output>{Math.round(opts.colour * 100)}</output></label>
                <input id="st-col" type="range" min="0.7" max="1.6" step="0.02" value={opts.colour} onChange={e => setOpts({ ...opts, colour: Number(e.target.value) })} /></div>
              <div className="field-group"><label htmlFor="st-br">Brightness <output>{opts.brightness}</output></label>
                <input id="st-br" type="range" min="-40" max="40" step="1" value={opts.brightness} onChange={e => setOpts({ ...opts, brightness: Number(e.target.value) })} /></div>
              <fieldset className="seg" aria-label="Framing">
                <label><input type="radio" name="st-mode" checked={opts.mode === 'cover'} onChange={() => setOpts({ ...opts, mode: 'cover' })} /> Fill the frame (4:5)</label>
                <label><input type="radio" name="st-mode" checked={opts.mode === 'contain'} onChange={() => setOpts({ ...opts, mode: 'contain' })} /> Show the whole photo</label>
              </fieldset>
              <button type="button" className="btn btn-primary" onClick={run} disabled={working}>{working ? 'Working…' : <><i className="bi bi-stars" aria-hidden="true"></i> Enhance photo</>}</button>
              <p className="admin-hint">Smart enlargement to 1200×1500, automatic light and colour correction and careful sharpening. It improves what is in the photo; it does not invent detail, so start from the best picture you have.</p>
            </div>
            <div className="studio-view">
              {result ? (
                <>
                  <div className="compare" style={{ '--split': `${split}%` }}>
                    <img src={result.url} alt="After: enhanced photo" />
                    <img className="before" src={result.beforeUrl} alt="Before: original photo, same crop" />
                    <span className="tag tag-before">Before</span><span className="tag tag-after">After</span>
                  </div>
                  <input type="range" min="0" max="100" value={split} onChange={e => setSplit(Number(e.target.value))} aria-label="Compare before and after" />
                  <ul className="stats">
                    <li><strong>{result.width}×{result.height}</strong><small>from {result.from.w}×{result.from.h}</small></li>
                    <li><strong>{gain > 0 ? `+${gain}%` : 'Same'}</strong><small>crispness</small></li>
                    <li><strong>{kb(result.blob.size)}</strong><small>{result.blob.type.split('/')[1]}</small></li>
                  </ul>
                  <div className="studio-actions">
                    <button type="button" className="btn btn-primary" onClick={() => useOnProduct('cover')} disabled={!product}>Use as cover of this saree</button>
                    <button type="button" className="btn btn-outline" onClick={() => useOnProduct('gallery')} disabled={!product}>Add to its gallery</button>
                    <button type="button" className="btn btn-outline" onClick={saveToLibrary}>Save to library</button>
                    <a className="btn btn-outline" href={result.url} download={`${slug(product?.name || 'saree')}.${result.blob.type === 'image/webp' ? 'webp' : 'jpg'}`}>Download</a>
                  </div>
                </>
              ) : <img className="studio-src" src={src.url} alt="Selected photo" />}
            </div>
          </div>
        </section>
      )}

      {src && (
        <section className="card studio-step">
          <h3><span className="step-no">3</span> Make a cinematic reel</h3>
          {!format && <p className="admin-warn">This browser cannot record video. Use a recent Chrome, Edge or Firefox.</p>}
          <div className="studio-grid">
            <div className="studio-controls">
              <div className="field-group"><label htmlFor="rl-title">Title on the reel</label>
                <input id="rl-title" maxLength={40} value={reel.title} onChange={e => setReel({ ...reel, title: e.target.value })} placeholder="Kanchipuram Pure Silk" /></div>
              <div className="field-group"><label htmlFor="rl-sub">Line under it</label>
                <input id="rl-sub" maxLength={44} value={reel.subtitle} onChange={e => setReel({ ...reel, subtitle: e.target.value })} placeholder="Mulberry silk  |  Gold zari" /></div>
              <fieldset className="seg" aria-label="Close-up to place">
                <legend className="sr-only">Which close-up to place</legend>
                <label><input type="radio" name="rl-focus" checked={reel.focus === 'A'} onChange={() => setReel({ ...reel, focus: 'A' })} /> Close-up 1: face or border</label>
                <label><input type="radio" name="rl-focus" checked={reel.focus === 'B'} onChange={() => setReel({ ...reel, focus: 'B' })} /> Close-up 2: fabric and fall</label>
              </fieldset>
              <p className="admin-hint">Click on the photo to choose what each close-up looks at.</p>
              <button type="button" className="btn btn-primary" onClick={startReel} disabled={reel.making || !format}>{reel.making ? `Recording… ${Math.round(reel.pct * 100)}%` : <><i className="bi bi-camera-reels" aria-hidden="true"></i> Create reel ({REEL.seconds} seconds)</>}</button>
              <p className="admin-hint">Keep this tab open and visible while it records.</p>
            </div>
            <div className="studio-view reel-view">
              <button type="button" className="focus-pick" onClick={place} aria-label="Click to place the close-up">
                <img src={result?.url || src.url} alt="" />
                <span className="dot a" style={{ left: `${reel.A[0] * 100}%`, top: `${reel.A[1] * 100}%` }}>1</span>
                <span className="dot b" style={{ left: `${reel.B[0] * 100}%`, top: `${reel.B[1] * 100}%` }}>2</span>
              </button>
              <div className="reel-stage">
                {reel.making && <canvas ref={live} width={REEL.w} height={REEL.h} aria-label="Reel being recorded" />}
                {!reel.making && reel.url && <video src={reel.url} controls loop autoPlay muted playsInline />}
                {!reel.making && !reel.url && <p className="ap-empty">Your reel appears here.</p>}
              </div>
            </div>
          </div>
          {reel.url && (
            <div className="studio-actions">
              <button type="button" className="btn btn-primary" onClick={publishReel} disabled={reel.publishing || !product}>{reel.publishing ? 'Publishing…' : 'Publish to "Sarees in motion"'}</button>
              <a className="btn btn-outline" href={reel.url} download={`${slug(reel.title)}-reel.${reel.blob.type.includes('mp4') ? 'mp4' : 'webm'}`}>Download</a>
              {!product && <span className="admin-hint">Choose the saree in step 1 to publish.</span>}
            </div>
          )}
        </section>
      )}

      <section className="card studio-step">
        <h3><span className="step-no">4</span> AI model photos</h3>
        <p className="admin-hint">This panel does not create pictures itself. Paste these briefs into an AI image tool (Midjourney, Adobe Firefly, Google Gemini, ChatGPT images), download the result, then upload it in step 1 and make it crisp. Only publish AI pictures if your listing says they are AI-generated, and check the saree shown matches what you sell.</p>
        {!prompts && <p className="ap-empty">Choose a saree in step 1 and the briefs are written for it.</p>}
        {prompts && prompts.map(p => (
          <div className="prompt" key={p.title}>
            <div><strong>{p.title}</strong><p>{p.text}</p></div>
            <button type="button" className="btn btn-outline" onClick={() => navigator.clipboard?.writeText(p.text).then(() => toast.success('Copied'), () => toast.error('Copy did not work'))}><i className="bi bi-clipboard" aria-hidden="true"></i> Copy</button>
          </div>
        ))}
      </section>

      {picker && <MediaPicker kind="IMAGE" onClose={() => setPicker(false)} onPick={(a) => { setPicker(false); choose({ url: imageSrc(a.url), name: a.name }); }} />}
    </div>
  );
}
