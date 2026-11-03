// Shrinks a video in the browser before upload: at most 720p and about 1.6 Mbps, sound kept. Phone videos are
// usually 1080p/4K at 10-20 Mbps, so this cuts them 3-5x. It plays the clip once (real time) into a canvas and
// re-records it with MediaRecorder, so a 30 s clip takes about 30 s. The admin tab must stay open while it runs.
// Whenever anything is not supported, or the result would not be smaller, the original file is returned unchanged.

const MAX_SIDE = 1280;           // 720p for landscape (1280x720) and for portrait reels (720x1280)
const VIDEO_BPS = 1_600_000;
const AUDIO_BPS = 96_000;
const SKIP_BELOW = 3 * 1024 * 1024; // already small: not worth re-encoding

function pickType() {
  if (typeof MediaRecorder === 'undefined') return null;
  return ['video/mp4;codecs=avc1.42E01E,mp4a.40.2', 'video/mp4', 'video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm']
    .find((t) => MediaRecorder.isTypeSupported(t)) || null;
}

/** Returns a smaller File, or the original one. onProgress gets 0..1. */
export async function shrinkVideo(file, onProgress = () => {}) {
  const mime = pickType();
  if (!mime || file.size < SKIP_BELOW || !HTMLCanvasElement.prototype.captureStream) return file;

  const url = URL.createObjectURL(file);
  const video = document.createElement('video');
  video.src = url;
  video.playsInline = true;
  video.preload = 'auto';
  let audioCtx = null;
  try {
    await new Promise((resolve, reject) => {
      video.onloadedmetadata = resolve;
      video.onerror = () => reject(new Error('unreadable'));
    });
    const { videoWidth: vw, videoHeight: vh, duration } = video;
    if (!vw || !vh || !isFinite(duration) || duration > 180) return file;
    const scale = Math.min(1, MAX_SIDE / Math.max(vw, vh));
    const w = Math.round((vw * scale) / 2) * 2;
    const h = Math.round((vh * scale) / 2) * 2;

    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const g = canvas.getContext('2d');
    const stream = canvas.captureStream(30);

    // Sound: routed through Web Audio into the recording only, so nothing plays out loud while it works.
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const src = audioCtx.createMediaElementSource(video);
      const dest = audioCtx.createMediaStreamDestination();
      src.connect(dest);
      dest.stream.getAudioTracks().forEach((t) => stream.addTrack(t));
      await audioCtx.resume();
    } catch {
      return file; // could not keep the sound: never upload a silent copy
    }

    const rec = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: VIDEO_BPS, audioBitsPerSecond: AUDIO_BPS });
    const chunks = [];
    rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
    const stopped = new Promise((resolve) => { rec.onstop = resolve; });

    let done = false;
    const draw = () => {
      if (done) return;
      g.drawImage(video, 0, 0, w, h);
      onProgress(Math.min(0.99, video.currentTime / duration));
      if (video.requestVideoFrameCallback) video.requestVideoFrameCallback(draw);
      else requestAnimationFrame(draw);
    };

    rec.start(1000);
    await video.play();
    draw();
    await new Promise((resolve, reject) => {
      video.onended = resolve;
      video.onerror = () => reject(new Error('playback failed'));
    });
    done = true;
    rec.stop();
    await stopped;

    const type = mime.split(';')[0];
    const blob = new Blob(chunks, { type });
    onProgress(1);
    if (!blob.size || blob.size >= file.size * 0.9) return file;
    const ext = type === 'video/mp4' ? 'mp4' : 'webm';
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.' + ext, { type });
  } catch {
    return file;
  } finally {
    video.pause();
    URL.revokeObjectURL(url);
    if (audioCtx) audioCtx.close().catch(() => {});
  }
}
