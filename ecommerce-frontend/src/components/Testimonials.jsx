import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiService } from '../services/api';
import { reducedMotion } from '../lib/motion';
import { Rise } from './Motion';

const initials = (name) => name.trim().split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase();

/**
 * Featured customer quotes, one at a time. Quotes marked as samples (local demo data) carry a visible
 * "Sample" label so placeholder copy can never pass as a real customer. Hidden when there are none.
 */
export function Testimonials() {
  const [list, setList] = useState([]);
  const [i, setI] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => { ApiService.testimonials().then(setList).catch(() => setList([])); }, []);
  useEffect(() => {
    if (list.length < 2 || paused || reducedMotion()) return undefined;
    const t = setInterval(() => setI(n => (n + 1) % list.length), 6500);
    return () => clearInterval(t);
  }, [list.length, paused]);

  if (!list.length) return null;
  const q = list[i % list.length];
  return (
    <Rise as="section" className="voices" aria-labelledby="voices-title">
      <div className="voices-head"><span className="eyebrow">Kind words</span><h2 id="voices-title">Loved by saree lovers</h2></div>
      <div className="voices-card" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
        onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
        <span className="voices-mark" aria-hidden="true">&ldquo;</span>
        <figure key={q.id} className="voices-fig">
          <div className="voices-stars" role="img" aria-label={`${q.rating} out of 5 stars`}>
            {[1, 2, 3, 4, 5].map(n => <i key={n} className={`bi ${n <= q.rating ? 'bi-star-fill' : 'bi-star'}`} style={{ '--n': n }} aria-hidden="true"></i>)}
          </div>
          <blockquote>{q.quote}</blockquote>
          <figcaption>
            <span className="voices-avatar" aria-hidden="true">{initials(q.name)}</span>
            <span><strong>{q.name}</strong>{q.location && <small>{q.location}</small>}</span>
            {q.sample && <span className="voices-sample" title="Placeholder text for the local demo, not a real customer">Sample</span>}
            {q.productId && <Link className="voices-prod" to={`/products/${q.productId}`}>{q.productName} <i className="bi bi-arrow-right" aria-hidden="true"></i></Link>}
          </figcaption>
        </figure>
        {list.length > 1 && (
          <div className="voices-dots" role="group" aria-label="Choose a testimonial">
            {list.map((t, n) => <button key={t.id} type="button" className={n === i % list.length ? 'on' : ''} aria-label={`Testimonial from ${t.name}`} aria-current={n === i % list.length} onClick={() => setI(n)}></button>)}
          </div>
        )}
      </div>
    </Rise>
  );
}
