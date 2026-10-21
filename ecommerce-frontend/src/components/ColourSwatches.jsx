import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiService } from '../services/api';
import { imageSrc } from '../lib/format';

/** Other colours of the same design: each is its own product with its own photos and stock. */
export function ColourSwatches({ product }) {
  const [colours, setColours] = useState([]);

  useEffect(() => {
    let live = true;
    ApiService.colours(product.id).then(c => { if (live) setColours(c); }).catch(() => { if (live) setColours([]); });
    return () => { live = false; };
  }, [product.id]);

  if (colours.length < 2) return null;

  return (
    <div className="swatches" role="group" aria-label="Available colours">
      <div className="swatches-label">Colour: <strong>{product.colour}</strong></div>
      <ul>
        {colours.map(c => {
          const current = c.id === product.id;
          return (
            <li key={c.id}>
              <Link to={`/products/${c.id}`} replace className={`swatch${current ? ' on' : ''}${c.inStock ? '' : ' out'}`}
                aria-current={current ? 'page' : undefined} title={`${c.colour}${c.inStock ? '' : ' (sold out)'}`}>
                <img src={imageSrc(c.imageUrl)} alt="" loading="lazy" />
                <span>{c.colour}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
