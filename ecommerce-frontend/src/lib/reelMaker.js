/**
 * Turns one photo into a short cinematic reel in the browser, the same look as the store's own reels: a slow push-in on the
 * whole drape, then two close-ups (zari and border, the fall of the fabric), film grain, a vignette, a gold light sweep,
 * letterbox bars and the saree's name. The result is recorded from a canvas, so it is real video the owner can publish.
 */
import { loadImage } from './imageTools';

export const REEL = { w: 540, h: 960, fps: 30, seconds: 9, shot: 3.4, fade: 0.55 };

const GOLD = '#e9cf95';

/** Which container the browser can record: MP4 where available (plays everywhere), else WebM. */
export function reelFormat() {
  if (typeof MediaRecorder === 'undefined') return null;
  return ['video/mp4;codecs=avc1.42E01E', 'video/mp4', 'video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'].find((t) => MediaRecorder.isTypeSupported(t)) || null;
}

const ease = (t) => 1 - Math.pow(1 - Math.min(Math.max(t, 0), 1), 3);
const lerp = (a, b, t) => a + (b - a) * t;

function noiseTile() {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d');
  const d = g.createImageData(128, 128);
  for (let i = 0; i < d.data.length; i += 4) {
    const v = Math.random() * 255;
    d.data[i] = d.data[i + 1] = d.data[i + 2] = v;
    d.data[i + 3] = 255;
  }
  g.putImageData(d, 0, 0);
  return c;
}

/** Draws the frame for time t (seconds) onto ctx. */
function drawFrame(ctx, src, o, t, grain) {
  const { w, h, shot, fade, seconds } = REEL;
  const iw = src.naturalWidth || src.width;
  const ih = src.naturalHeight || src.height;
  // the 9:16 window of the photo, then zoom and drift inside it
  let bw = iw; let bh = ih;
  if (iw / ih > w / h) bw = ih * (w / h); else bh = iw / (w / h);
  const bx0 = Math.min(Math.max(iw * 0.5 - bw / 2, 0), iw - bw);
  const by0 = Math.min(Math.max(ih * 0.5 - bh / 2, 0), ih - bh);
  const spec = [
    { z0: 1.0, z1: 1.16, f0: [0.5, 0.5], f1: [0.5, 0.46] },
    { z0: 1.9, z1: 2.2, f0: [o.focusA[0] - 0.08, o.focusA[1]], f1: [o.focusA[0] + 0.08, o.focusA[1] - 0.03] },
    { z0: 1.7, z1: 1.95, f0: [o.focusB[0] + 0.06, o.focusB[1] + 0.06], f1: [o.focusB[0] - 0.06, o.focusB[1] - 0.06] },
  ];
  const draw = (i, alpha) => {
    const s = spec[i];
    const p = Math.min(1, Math.max(0, (t - i * (shot - fade)) / shot));
    const z = lerp(s.z0, s.z1, p);
    const fx = lerp(s.f0[0], s.f1[0], p);
    const fy = lerp(s.f0[1], s.f1[1], p);
    const sw = bw / z; const sh = bh / z;
    const sx = Math.min(Math.max(bx0 + bw * fx - sw / 2, bx0), bx0 + bw - sw);
    const sy = Math.min(Math.max(by0 + bh * fy - sh / 2, by0), by0 + bh - sh);
    ctx.globalAlpha = alpha;
    ctx.drawImage(src, sx, sy, sw, sh, 0, 0, w, h);
    ctx.globalAlpha = 1;
  };
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);
  const start1 = (shot - fade);
  const start2 = 2 * (shot - fade);
  if (t < start1) draw(0, 1);
  else if (t < start2) { draw(0, 1); draw(1, (t - start1) / fade > 1 ? 1 : (t - start1) / fade); }
  else { draw(1, 1); draw(2, Math.min(1, (t - start2) / fade)); }
  if (t >= start1 + fade && t < start2) { /* shot 2 fully in */ }

  // warm contrast, then vignette
  ctx.globalCompositeOperation = 'soft-light';
  ctx.fillStyle = 'rgba(255,196,120,0.18)';
  ctx.fillRect(0, 0, w, h);
  ctx.globalCompositeOperation = 'source-over';
  const vg = ctx.createRadialGradient(w / 2, h / 2, h * 0.28, w / 2, h / 2, h * 0.72);
  vg.addColorStop(0, 'rgba(0,0,0,0)');
  vg.addColorStop(1, 'rgba(0,0,0,0.55)');
  ctx.fillStyle = vg;
  ctx.fillRect(0, 0, w, h);

  // a gold light sweep during the first close-up
  const sweep = Math.min(Math.max((t - 1.9) / 1.6, 0), 1);
  if (sweep > 0 && sweep < 1) {
    const x = lerp(-w * 0.6, w * 1.6, sweep);
    const lg = ctx.createLinearGradient(x, 0, x + w * 0.45, h * 0.35);
    lg.addColorStop(0, 'rgba(255,205,120,0)');
    lg.addColorStop(0.5, 'rgba(255,205,120,0.30)');
    lg.addColorStop(1, 'rgba(255,205,120,0)');
    ctx.globalCompositeOperation = 'lighter';
    ctx.fillStyle = lg;
    ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'source-over';
  }

  // film grain
  ctx.globalAlpha = 0.07;
  ctx.globalCompositeOperation = 'overlay';
  ctx.fillStyle = ctx.createPattern(grain, 'repeat');
  ctx.save();
  ctx.translate(-Math.random() * 128, -Math.random() * 128);
  ctx.fillRect(0, 0, w + 128, h + 128);
  ctx.restore();
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = 'source-over';

  // letterbox bars slide in, then the words
  const bar = 96 * ease(t / 0.8);
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, bar);
  ctx.fillRect(0, h - bar, w, bar);
  ctx.textAlign = 'center';
  const text = (str, y, size, colour, delay, italic = true, spacing = 0) => {
    const a = ease((t - delay) / 0.7);
    if (a <= 0 || !str) return;
    ctx.globalAlpha = a;
    ctx.fillStyle = colour;
    ctx.font = `${italic ? 'italic ' : ''}${size}px Georgia, "Times New Roman", serif`;
    if ('letterSpacing' in ctx) ctx.letterSpacing = `${spacing}px`;
    ctx.fillText(str, w / 2, y + (1 - a) * 30);
    ctx.globalAlpha = 1;
  };
  text(o.brand.toUpperCase(), 58, 19, GOLD, 0.5, true, 2);
  text(o.title, h - 218, 34, '#fff', 1.0);
  text(o.subtitle.toUpperCase(), h - 170, 16, GOLD, 1.4, true, 1.5);
  if ('letterSpacing' in ctx) ctx.letterSpacing = '0px';

  // fade in and out
  const fadeA = Math.max(1 - t / 0.35, 0, (t - (seconds - 0.4)) / 0.4);
  if (fadeA > 0) { ctx.fillStyle = `rgba(0,0,0,${Math.min(1, fadeA)})`; ctx.fillRect(0, 0, w, h); }
}

/** A still from the middle of the reel, used as the cover picture. */
export async function reelCover(source, options) {
  const src = source instanceof Blob || typeof source === 'string' ? await loadImage(source) : source;
  const c = document.createElement('canvas');
  c.width = REEL.w; c.height = REEL.h;
  drawFrame(c.getContext('2d'), src, normalise(options), 3.3, noiseTile());
  return new Promise((resolve) => c.toBlob(resolve, 'image/jpeg', 0.9));
}

const normalise = (o = {}) => ({
  title: o.title || 'Handwoven saree',
  subtitle: o.subtitle || 'Rekha Shopping Mall',
  brand: o.brand || 'Rekha Shopping Mall',
  focusA: o.focusA || [0.5, 0.3],
  focusB: o.focusB || [0.5, 0.72],
});

/**
 * Records the reel in real time (about nine seconds; the page has to stay open and visible while it runs).
 * onFrame receives the live canvas so the owner can watch it being made.
 */
export async function makeReel(source, options, { onProgress, onFrame } = {}) {
  const mime = reelFormat();
  if (!mime) throw new Error('This browser cannot record video. Use a recent Chrome, Edge or Firefox.');
  const src = source instanceof Blob || typeof source === 'string' ? await loadImage(source) : source;
  const o = normalise(options);
  const c = document.createElement('canvas');
  c.width = REEL.w; c.height = REEL.h;
  const ctx = c.getContext('2d');
  const grain = noiseTile();
  const rec = new MediaRecorder(c.captureStream(REEL.fps), { mimeType: mime, videoBitsPerSecond: 2500000 });
  const chunks = [];
  rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
  const done = new Promise((resolve, reject) => {
    rec.onstop = () => resolve(new Blob(chunks, { type: mime.split(';')[0] }));
    rec.onerror = (e) => reject(e.error || new Error('Recording failed'));
  });
  drawFrame(ctx, src, o, 0, grain);
  rec.start(250);
  const t0 = performance.now();
  await new Promise((resolve) => {
    const tick = () => {
      const t = (performance.now() - t0) / 1000;
      drawFrame(ctx, src, o, Math.min(t, REEL.seconds), grain);
      onFrame?.(c);
      onProgress?.(Math.min(1, t / REEL.seconds));
      if (t >= REEL.seconds) resolve(); else requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
  await new Promise((r) => setTimeout(r, 150));
  rec.stop();
  return done;
}
