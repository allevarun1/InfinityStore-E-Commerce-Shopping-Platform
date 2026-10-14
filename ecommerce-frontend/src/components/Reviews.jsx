import React, { useCallback, useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { StarRating } from './StarRating';

const NOT_ELIGIBLE = {
  NOT_PURCHASED: 'Reviews are open to customers whose order of this saree has been delivered.',
  ALREADY_REVIEWED: 'Thank you, you have already reviewed this saree.',
};

/** Verified-purchase reviews. Nothing here is invented: with no reviews it says so. */
export function Reviews({ productId, onChange }) {
  const { isCustomer, isAdmin } = useAuth();
  const toast = useToast();
  const [reviews, setReviews] = useState(null);
  const [eligibility, setEligibility] = useState(null);
  const [rating, setRating] = useState(5);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const customer = isCustomer();
  const admin = isAdmin();

  const load = useCallback(() => {
    ApiService.reviews(productId).then(setReviews).catch(() => setReviews([]));
    if (customer) {
      ApiService.reviewEligibility(productId).then(setEligibility).catch(() => setEligibility(null));
    }
  }, [productId, customer]);

  useEffect(() => { load(); }, [load]);

  const submit = (e) => {
    e.preventDefault();
    setBusy(true);
    ApiService.submitReview(productId, { rating, title, body })
      .then(() => {
        toast.success('Thank you for your review');
        setTitle('');
        setBody('');
        load();
        onChange?.();
      })
      .catch(err => toast.error(err.response?.data?.message || 'Could not save your review'))
      .finally(() => setBusy(false));
  };

  const hide = (r) => {
    ApiService.hideReview(r.id, true)
      .then(() => { toast.success('Review hidden'); load(); onChange?.(); })
      .catch(() => toast.error('Could not hide this review'));
  };

  return (
    <section className="reviews" id="reviews" aria-labelledby="reviews-title">
      <h2 id="reviews-title">Customer reviews</h2>

      {reviews === null && <p className="muted">Loading reviews…</p>}
      {reviews && reviews.length === 0 && (
        <p className="muted">No reviews yet. Reviews come only from customers who have received this saree.</p>
      )}
      {reviews && reviews.map(r => (
        <article className="review" key={r.id}>
          <div className="review-head">
            <StarRating rating={r.rating} />
            <strong>{r.title || `${r.rating} out of 5`}</strong>
          </div>
          {r.body && <p>{r.body}</p>}
          <small>
            {r.author} · Verified purchase · {new Date(r.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
            {admin && <> · <button type="button" className="linklike" onClick={() => hide(r)}>Hide review</button></>}
          </small>
        </article>
      ))}

      {customer && eligibility && !eligibility.eligible && NOT_ELIGIBLE[eligibility.reason] && (
        <p className="muted">{NOT_ELIGIBLE[eligibility.reason]}</p>
      )}
      {customer && eligibility?.eligible && (
        <form className="review-form" onSubmit={submit}>
          <h3>Write a review</h3>
          <fieldset className="rate-pick">
            <legend>Your rating</legend>
            {[1, 2, 3, 4, 5].map(n => (
              <label key={n}>
                <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} />
                <span aria-label={`${n} out of 5`}>{n}★</span>
              </label>
            ))}
          </fieldset>
          <div className="field-group">
            <label htmlFor="rv-title">Headline (optional)</label>
            <input id="rv-title" maxLength="120" value={title} onChange={e => setTitle(e.target.value)} />
          </div>
          <div className="field-group">
            <label htmlFor="rv-body">Your review</label>
            <textarea id="rv-body" rows="4" maxLength="2000" value={body} onChange={e => setBody(e.target.value)}
              placeholder="How was the fabric, colour and drape compared with the photos?" />
          </div>
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Submit review'}</button>
        </form>
      )}
    </section>
  );
}
