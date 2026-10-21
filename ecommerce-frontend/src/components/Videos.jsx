import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useStore } from '../contexts/StoreContext';
import { reducedMotion } from '../lib/motion';
import { Rise } from './Motion';
import { imageSrc } from '../lib/format';

const saveData = () => typeof navigator !== 'undefined' && navigator.connection?.saveData === true;
/** Muted autoplay is a courtesy: never for people who asked for less motion or are saving data. */
const canAutoplay = () => !reducedMotion() && !saveData();
const ytThumb = (id) => `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
const poster = (v) => (v.posterUrl ? imageSrc(v.posterUrl) : v.youtubeId ? ytThumb(v.youtubeId) : undefined);

/**
 * Plays one video. Files autoplay muted while at least 60% visible and pause when scrolled away;
 * YouTube clips show their cover first and only load YouTube once tapped (no tracking until then).
 */
export function VideoPlayer({ video, startMuted = true, autoplay = true, className = '' }) {
  const ref = useRef(null);
  const [muted, setMuted] = useState(startMuted);
  const [playing, setPlaying] = useState(false);
  const [yt, setYt] = useState(false);
  const auto = autoplay && canAutoplay();

  const start = useCallback((el) => {
    el.play().catch(() => {
      if (!el.muted) { el.muted = true; setMuted(true); el.play().catch(() => {}); }
    });
  }, []);

  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    el.muted = muted;
    return undefined;
  }, [muted]);

  // Opened on purpose (full-screen viewer): play straight away, with sound where the browser allows it.
  useEffect(() => {
    if (startMuted === false && ref.current) start(ref.current);
  }, [startMuted, start, video.id]);

  useEffect(() => {
    const el = ref.current;
    if (!el || !auto || typeof IntersectionObserver === 'undefined') return undefined;
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) start(el); else el.pause(); }, { threshold: 0.4 });
    io.observe(el);
    return () => io.disconnect();
  }, [auto, start, video.id]);

  if (video.youtubeId) {
    return (
      <div className={`vp vp-yt ${className}`}>
        {yt ? (
          <iframe title={video.title} loading="lazy" allow="autoplay; encrypted-media; picture-in-picture" allowFullScreen
            src={`https://www.youtube-nocookie.com/embed/${video.youtubeId}?autoplay=1&rel=0&modestbranding=1&playsinline=1`} />
        ) : (
          <button type="button" className="vp-cover" onClick={() => setYt(true)} aria-label={`Play video: ${video.title}`}>
            <img src={poster(video)} alt="" loading="lazy" />
            <span className="vp-bigplay"><i className="bi bi-play-fill" aria-hidden="true"></i></span>
          </button>
        )}
      </div>
    );
  }

  const toggle = () => { const el = ref.current; if (!el) return; if (el.paused) start(el); else el.pause(); };
  return (
    <div className={`vp ${playing ? 'is-playing' : ''} ${className}`}>
      <video ref={ref} src={imageSrc(video.videoUrl)} poster={poster(video)} muted={muted} loop playsInline
        preload={poster(video) ? 'none' : 'metadata'} aria-label={video.title}
        onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onClick={toggle} />
      {!playing && (
        <button type="button" className="vp-bigplay" onClick={toggle} aria-label={`Play video: ${video.title}`}>
          <i className="bi bi-play-fill" aria-hidden="true"></i>
        </button>
      )}
      <div className="vp-bar">
        <button type="button" onClick={toggle} aria-label={playing ? 'Pause' : 'Play'}><i className={`bi ${playing ? 'bi-pause-fill' : 'bi-play-fill'}`} aria-hidden="true"></i></button>
        <button type="button" onClick={() => setMuted(m => !m)} aria-pressed={!muted} aria-label={muted ? 'Turn sound on' : 'Turn sound off'}>
          <i className={`bi ${muted ? 'bi-volume-mute-fill' : 'bi-volume-up-fill'}`} aria-hidden="true"></i>
        </button>
      </div>
    </div>
  );
}

/** Full-screen viewer for reels and product clips, with next/previous and a shop button. */
export function ReelViewer({ videos, start = 0, onClose }) {
  const [i, setI] = useState(start);
  const close = useRef(null);
  const v = videos[i];

  useEffect(() => {
    const before = document.activeElement;
    document.body.classList.add('no-scroll');
    close.current?.focus();
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowRight') setI(n => Math.min(videos.length - 1, n + 1));
      if (e.key === 'ArrowLeft') setI(n => Math.max(0, n - 1));
    };
    document.addEventListener('keydown', onKey);
    return () => { document.body.classList.remove('no-scroll'); document.removeEventListener('keydown', onKey); before?.focus?.(); };
  }, [onClose, videos.length]);

  if (!v) return null;
  return (
    <div className="rv-root">
      <div className="rv-backdrop" onClick={onClose} aria-hidden="true"></div>
      <div className="rv" role="dialog" aria-modal="true" aria-label={v.title}>
        <button ref={close} type="button" className="rv-close" onClick={onClose} aria-label="Close video"><i className="bi bi-x-lg" aria-hidden="true"></i></button>
        <VideoPlayer key={v.id} video={v} startMuted={false} className="vp-tall" />
        <div className="rv-info">
          <strong>{v.title}</strong>
          {v.caption && <span>{v.caption}</span>}
          {v.productId && <Link className="btn btn-primary" to={`/products/${v.productId}`} onClick={onClose}>Shop {v.productName} <i className="bi bi-arrow-right" aria-hidden="true"></i></Link>}
        </div>
        {videos.length > 1 && (
          <>
            <button type="button" className="rv-nav prev" disabled={i === 0} onClick={() => setI(i - 1)} aria-label="Previous video"><i className="bi bi-chevron-left" aria-hidden="true"></i></button>
            <button type="button" className="rv-nav next" disabled={i === videos.length - 1} onClick={() => setI(i + 1)} aria-label="Next video"><i className="bi bi-chevron-right" aria-hidden="true"></i></button>
          </>
        )}
      </div>
    </div>
  );
}

/** Looping weave animation, shown when the owner has not added a story video yet. */
function LoomArt() {
  const rows = Array.from({ length: 11 }, (_, r) => r);
  const colours = ['#e9cf95', '#c95b6b', '#f4ede2', '#b5862f'];
  return (
    <svg className="loom" viewBox="0 0 400 500" role="img" aria-label="Threads being woven on a loom">
      <defs>
        <linearGradient id="loom-bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#7b1e2b" /><stop offset="1" stopColor="#2a0d13" /></linearGradient>
      </defs>
      <rect width="400" height="500" rx="18" fill="url(#loom-bg)" />
      {Array.from({ length: 21 }, (_, c) => <line key={c} className="warp" x1={30 + c * 17.5} y1="30" x2={30 + c * 17.5} y2="470" />)}
      {rows.map((r) => (
        <path key={r} className="weft" pathLength="1" d={`M${r % 2 ? 372 : 28} ${58 + r * 38} H${r % 2 ? 28 : 372}`}
          style={{ '--r': r, stroke: colours[r % colours.length] }} />
      ))}
    </svg>
  );
}

const STEPS = [
  ['bi-droplet-half', 'Yarn is dyed', 'Silk and cotton yarn takes its colour in the dye bath.'],
  ['bi-distribute-vertical', 'The loom is warped', 'Thousands of threads are set side by side, ready to weave.'],
  ['bi-vr', 'Woven pick by pick', 'The weft passes through again and again, building the body, border and pallu.'],
  ['bi-patch-check', 'Checked and packed', 'Each saree is inspected before it is folded and sent to you.'],
];

/** The collection process: a story video when there is one, an animated loom and the four steps always. */
export function CraftStory({ videos }) {
  const stories = videos.filter(v => v.kind === 'STORY');
  const [i, setI] = useState(0);
  const story = stories[i] || null;
  return (
    <section className="craft" aria-labelledby="craft-title">
      <div className="craft-grid">
        <Rise className="craft-media">{story ? <VideoPlayer key={story.id} video={story} className="vp-wide" /> : <LoomArt />}</Rise>
        <div className="craft-copy">
          <span className="eyebrow">Behind the weave</span>
          <h2 id="craft-title">{story ? story.title : 'How a handwoven saree comes to life'}</h2>
          {story?.caption && <p className="craft-cap">{story.caption}</p>}
          <ol className="craft-steps">
            {STEPS.map(([icon, title, text], n) => (
              <Rise as="li" key={title} delay={n * 120}>
                <i className={`bi ${icon}`} aria-hidden="true"></i>
                <div><strong>{title}</strong><span>{text}</span></div>
              </Rise>
            ))}
          </ol>
          {story?.productId && <Link className="btn btn-primary" to={`/products/${story.productId}`}>Shop {story.productName}</Link>}
          {stories.length > 1 && (
            <div className="craft-more" role="group" aria-label="More stories">
              {stories.map((s, n) => <button key={s.id} type="button" aria-pressed={n === i} onClick={() => setI(n)}>{s.title}</button>)}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function ReelCard({ video, onOpen }) {
  const btn = useRef(null);
  const [live, setLive] = useState(false);
  const auto = canAutoplay();
  // A poster image is shown until the card is centred on screen; only then is a real <video> mounted, and it is removed again
  // when the card leaves. With 19 reels that keeps a handful of video layers alive instead of 19 (much lighter to scroll past).
  useEffect(() => {
    const el = btn.current;
    if (!el || !auto || video.youtubeId || typeof IntersectionObserver === 'undefined') return undefined;
    const io = new IntersectionObserver(([e]) => setLive(e.isIntersecting), { threshold: 0.7 });
    io.observe(el);
    return () => io.disconnect();
  }, [auto, video.youtubeId]);
  const cover = poster(video);
  return (
    <button type="button" className="reel" ref={btn} onClick={onOpen} aria-label={`Watch: ${video.title}`} data-video-src={video.videoUrl}>
      {cover ? <img src={cover} alt="" loading="lazy" decoding="async" /> : <span className="reel-blank" aria-hidden="true"></span>}
      {live && <video src={imageSrc(video.videoUrl)} poster={cover || undefined} muted loop playsInline autoPlay tabIndex={-1} aria-hidden="true" />}
      <span className="reel-play"><i className="bi bi-play-fill" aria-hidden="true"></i></span>
      <span className="reel-cap"><strong>{video.title}</strong>{video.productName && video.productName !== video.title && <small>{video.productName}</small>}
        {(!video.productName || video.productName === video.title) && video.caption && <small>{video.caption}</small>}</span>
    </button>
  );
}

/** A "watch and shop" strip of short vertical clips. Tap one to open it full screen. */
export function ReelsStrip({ videos }) {
  const reels = videos.filter(v => v.kind === 'REEL');
  const [open, setOpen] = useState(null);
  const row = useRef(null);
  const nudge = (dir) => row.current?.scrollBy({ left: dir * Math.min(720, row.current.clientWidth * 0.8), behavior: reducedMotion() ? 'auto' : 'smooth' });
  if (!reels.length) return null;
  return (
    <section className="reels" aria-labelledby="reels-title">
      <div className="reels-head">
        <div><span className="eyebrow">The cinematic edit</span><h2 id="reels-title">Sarees in motion</h2></div>
        {reels.length > 3 && (
          <div className="reels-nav">
            <button type="button" onClick={() => nudge(-1)} aria-label="Previous sarees"><i className="bi bi-arrow-left" aria-hidden="true"></i></button>
            <button type="button" onClick={() => nudge(1)} aria-label="Next sarees"><i className="bi bi-arrow-right" aria-hidden="true"></i></button>
          </div>
        )}
      </div>
      <ul className="reels-row" ref={row}>
        {reels.map((v, n) => <Rise as="li" key={v.id} delay={n * 80}><ReelCard video={v} onOpen={() => setOpen(n)} /></Rise>)}
      </ul>
      {open !== null && <ReelViewer videos={reels} start={open} onClose={() => setOpen(null)} />}
    </section>
  );
}

/** "Watch it drape" button on a product page, when the owner has linked a clip to that saree. */
export function WatchButton({ productId }) {
  const { videos } = useStore();
  const [open, setOpen] = useState(false);
  const mine = videos.filter(v => v.productId === productId);
  if (!mine.length) return null;
  return (
    <>
      <button type="button" className="watch-btn" onClick={() => setOpen(true)}>
        <i className="bi bi-play-circle-fill" aria-hidden="true"></i> Watch it drape
      </button>
      {open && <ReelViewer videos={mine} onClose={() => setOpen(false)} />}
    </>
  );
}
