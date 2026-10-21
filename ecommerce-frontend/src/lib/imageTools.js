/**
 * Photo tools that run entirely in the owner's browser (nothing is sent anywhere until they press Save):
 * a smart enhance (stepwise upscale, auto-levels, colour lift, unsharp mask) that turns a phone photo into a crisp
 * 1200x1500 catalogue image. This is classic image processing, not generative AI: it sharpens and corrects what is in
 * the photo, it never invents detail.
 */

export const TARGET = { w: 1200, h: 1500 }; // 4:5, the shape of every product card

/** Loads a File, Blob or URL as something drawImage accepts. Honors the phone's rotation (EXIF) for files. */
export async function loadImage(src) {
  if (src instanceof Blob) {
    if (typeof createImageBitmap === 'function') {
      try { return await createImageBitmap(src, { imageOrientation: 'from-image' }); } catch { /* fall through */ }
    }
    return htmlImage(URL.createObjectURL(src));
  }
  return htmlImage(src);
}

function htmlImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('That image could not be opened'));
    img.src = url;
  });
}

const sizeOf = (im) => ({ w: im.naturalWidth || im.width, h: im.naturalHeight || im.height });

function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.round(w);
  c.height = Math.round(h);
  return c;
}

/** Draws the source region into a new canvas, enlarging in gentle steps (much smoother than one big jump). */
function drawScaled(src, sx, sy, sw, sh, w, h) {
  let cur = canvas(sw, sh);
  cur.getContext('2d').drawImage(src, sx, sy, sw, sh, 0, 0, cur.width, cur.height);
  while (cur.width * 1.5 < w) {
    const next = canvas(cur.width * 1.5, cur.height * 1.5);
    const g = next.getContext('2d');
    g.imageSmoothingQuality = 'high';
    g.drawImage(cur, 0, 0, next.width, next.height);
    cur = next;
  }
  const out = canvas(w, h);
  const g = out.getContext('2d');
  g.imageSmoothingQuality = 'high';
  g.drawImage(cur, 0, 0, out.width, out.height);
  return out;
}

/** Fill the frame (cropping around the focus point), or fit the whole photo over a soft blurred copy of itself. */
function compose(src, w, h, mode, focus) {
  const { w: iw, h: ih } = sizeOf(src);
  const ratio = w / h;
  if (mode === 'contain') {
    const out = canvas(w, h);
    const g = out.getContext('2d');
    const bgScale = Math.max(w / iw, h / ih);
    g.filter = 'blur(28px) brightness(0.92)';
    g.drawImage(src, (w - iw * bgScale) / 2, (h - ih * bgScale) / 2, iw * bgScale, ih * bgScale);
    g.filter = 'none';
    const s = Math.min(w / iw, h / ih);
    const fit = drawScaled(src, 0, 0, iw, ih, Math.round(iw * s), Math.round(ih * s));
    g.drawImage(fit, (w - fit.width) / 2, (h - fit.height) / 2);
    return out;
  }
  let sw = iw;
  let sh = ih;
  if (iw / ih > ratio) sw = ih * ratio; else sh = iw / ratio;
  const sx = Math.min(Math.max(iw * focus[0] - sw / 2, 0), iw - sw);
  const sy = Math.min(Math.max(ih * focus[1] - sh / 2, 0), ih - sh);
  return drawScaled(src, sx, sy, sw, sh, w, h);
}

/** Separable box blur on one channel stack, three passes approximate a gaussian. */
function blurRGBA(data, w, h, radius) {
  const tmp = new Uint8ClampedArray(data.length);
  const pass = (from, to, horizontal) => {
    const len = horizontal ? w : h;
    const lines = horizontal ? h : w;
    const step = horizontal ? 4 : w * 4;
    const lineStep = horizontal ? w * 4 : 4;
    const span = radius * 2 + 1;
    for (let l = 0; l < lines; l++) {
      const base = l * lineStep;
      for (let c = 0; c < 3; c++) {
        let sum = 0;
        for (let i = -radius; i <= radius; i++) sum += from[base + Math.min(Math.max(i, 0), len - 1) * step + c];
        for (let i = 0; i < len; i++) {
          to[base + i * step + c] = sum / span;
          sum += from[base + Math.min(i + radius + 1, len - 1) * step + c] - from[base + Math.max(i - radius, 0) * step + c];
        }
      }
    }
  };
  const a = new Uint8ClampedArray(data);
  pass(a, tmp, true); pass(tmp, a, false);
  pass(a, tmp, true); pass(tmp, a, false);
  pass(a, tmp, true); pass(tmp, a, false);
  return a;
}

/** Auto-levels from the 0.4% and 99.6% tones, a gentle gamma toward a natural mid-tone, then a saturation lift. */
function levels(data, { colour, brightness }) {
  const hist = new Uint32Array(256);
  const n = data.length / 4;
  for (let i = 0; i < data.length; i += 4) hist[Math.round(0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2])]++;
  let acc = 0; let lo = 0; let hi = 255;
  for (let v = 0; v < 256; v++) { acc += hist[v]; if (acc >= n * 0.004) { lo = v; break; } }
  acc = 0;
  for (let v = 255; v >= 0; v--) { acc += hist[v]; if (acc >= n * 0.004) { hi = v; break; } }
  if (hi - lo < 120) { lo = Math.max(0, lo - 20); hi = Math.min(255, hi + 20); } // already flat: do not over-stretch
  let mean = 0;
  for (let v = 0; v < 256; v++) mean += v * hist[v];
  mean = (mean / n - lo) / Math.max(1, hi - lo);
  const gamma = Math.min(1.25, Math.max(0.8, Math.log(0.46) / Math.log(Math.min(0.9, Math.max(0.1, mean)))));
  const lut = new Uint8ClampedArray(256);
  for (let v = 0; v < 256; v++) {
    const t = Math.min(1, Math.max(0, (v - lo) / Math.max(1, hi - lo)));
    lut[v] = Math.pow(t, 1 / gamma) * 255 + brightness;
  }
  for (let i = 0; i < data.length; i += 4) {
    const r = lut[data[i]]; const g = lut[data[i + 1]]; const b = lut[data[i + 2]];
    const y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    data[i] = y + (r - y) * colour;
    data[i + 1] = y + (g - y) * colour;
    data[i + 2] = y + (b - y) * colour;
  }
}

function unsharp(data, w, h, amount, radius) {
  const blur = blurRGBA(data, w, h, radius);
  for (let i = 0; i < data.length; i += 4) {
    for (let c = 0; c < 3; c++) {
      const diff = data[i + c] - blur[i + c];
      if (Math.abs(diff) > 2) data[i + c] += diff * amount; // the small threshold keeps film grain and noise from being boosted
    }
  }
}

/** Variance of the Laplacian on a 400px copy: higher means crisper. Only meaningful for comparing two versions of one photo. */
export function sharpness(src) {
  const { w: iw, h: ih } = sizeOf(src);
  const s = 400 / Math.max(iw, ih);
  const c = canvas(iw * s, ih * s);
  c.getContext('2d').drawImage(src, 0, 0, c.width, c.height);
  const { data, width, height } = c.getContext('2d').getImageData(0, 0, c.width, c.height);
  const lum = new Float32Array(width * height);
  for (let i = 0; i < lum.length; i++) lum[i] = 0.2126 * data[i * 4] + 0.7152 * data[i * 4 + 1] + 0.0722 * data[i * 4 + 2];
  let sum = 0; let sq = 0; let n = 0;
  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const i = y * width + x;
      const l = 4 * lum[i] - lum[i - 1] - lum[i + 1] - lum[i - width] - lum[i + width];
      sum += l; sq += l * l; n++;
    }
  }
  const mean = sum / n;
  return sq / n - mean * mean;
}

export const ENHANCE_DEFAULTS = { sharpen: 0.7, colour: 1.1, brightness: 0, mode: 'cover', focus: [0.5, 0.45] };

/** The whole pipeline. Resolves with the enhanced picture as a WebP (JPEG where WebP is unavailable) plus before/after numbers. */
export async function enhance(src, options = {}) {
  const o = { ...ENHANCE_DEFAULTS, ...options };
  const original = typeof src === 'string' || src instanceof Blob ? await loadImage(src) : src;
  const { w: iw, h: ih } = sizeOf(original);
  const before = compose(original, TARGET.w, TARGET.h, o.mode, o.focus); // same crop and size, untouched: a fair comparison
  const out = compose(original, TARGET.w, TARGET.h, o.mode, o.focus);
  const g = out.getContext('2d');
  const img = g.getImageData(0, 0, out.width, out.height);
  levels(img.data, o);
  // sharpen a little more when the photo had to be enlarged a lot, because enlarging softens it
  const growth = Math.max(TARGET.w / iw, TARGET.h / ih);
  unsharp(img.data, out.width, out.height, o.sharpen * (growth > 1.6 ? 1.25 : 1), growth > 1.6 ? 2 : 1);
  g.putImageData(img, 0, 0);
  const blob = await toBlob(out, 0.9);
  return {
    blob,
    url: URL.createObjectURL(blob),
    beforeUrl: await toDataUrl(before),
    width: out.width,
    height: out.height,
    from: { w: iw, h: ih },
    sharpBefore: sharpness(before),
    sharpAfter: sharpness(out),
  };
}

export async function toBlob(c, quality = 0.9) {
  const tryType = (type) => new Promise((resolve) => c.toBlob((b) => resolve(b && b.type === type ? b : null), type, quality));
  return (await tryType('image/webp')) || (await tryType('image/jpeg')) || new Promise((resolve) => c.toBlob(resolve, 'image/png'));
}

const toDataUrl = async (c) => URL.createObjectURL(await toBlob(c, 0.85));

/** A photo straight from a phone, made upload-friendly: right way up and at most 2000px on the long side. */
export async function shrinkForUpload(file) {
  const img = await loadImage(file);
  const { w, h } = sizeOf(img);
  const s = Math.min(1, 2000 / Math.max(w, h));
  const c = canvas(w * s, h * s);
  const g = c.getContext('2d');
  g.imageSmoothingQuality = 'high';
  g.drawImage(img, 0, 0, c.width, c.height);
  const blob = await toBlob(c, 0.92);
  return new File([blob], file.name.replace(/\.[^.]+$/, '') + (blob.type === 'image/webp' ? '.webp' : '.jpg'), { type: blob.type });
}

export const kb = (bytes) => (bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);
