import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';

export function NotFound({
  title = 'We couldn’t find that page',
  message = 'The link may be out of date, or the page has moved.',
}) {
  useEffect(() => {
    document.title = 'Page not found | Rekha Shopping Mall';
  }, []);

  return (
    <section className="nf" aria-labelledby="nf-title">
      <div className="nf-code" aria-hidden="true">404</div>
      <h1 id="nf-title">{title}</h1>
      <p>{message}</p>
      <div className="hero-ctas">
        <Link className="btn btn-primary" to="/#products">Browse the collection</Link>
        <Link className="btn btn-outline" to="/?view=deals#products">See current deals</Link>
      </div>
    </section>
  );
}
