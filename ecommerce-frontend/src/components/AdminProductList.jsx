import React, { useMemo, useState } from 'react';
import { imageSrc, inr } from '../lib/format';

const STATUS_LABEL = { DRAFT: 'Draft', READY_FOR_REVIEW: 'Ready for review', APPROVED: 'Approved', PUBLISHED: 'Published' };

/** Lifecycle buttons for a product. The server enforces the same rules and is the authority. */
function nextSteps(p) {
  const manual = p.source !== 'AI_ASSISTED';
  switch (p.status) {
    case 'DRAFT': return [['READY_FOR_REVIEW', 'Send for review'], ...(manual ? [['PUBLISHED', 'Publish']] : [])];
    case 'READY_FOR_REVIEW': return [['APPROVED', 'Approve'], ...(manual ? [['PUBLISHED', 'Publish']] : []), ['DRAFT', 'Back to draft']];
    case 'APPROVED': return [['PUBLISHED', 'Publish'], ['DRAFT', 'Back to draft']];
    case 'PUBLISHED': return [['DRAFT', 'Unpublish']];
    default: return [];
  }
}

const FILTERS = [['ALL', 'All'], ['PUBLISHED', 'Published'], ['DRAFT', 'Drafts'], ['REVIEW', 'In review'], ['STOCK', 'Needs stock']];

/** Every saree as a card, with search and quick filters. */
export function AdminProductList({ products, onStep, onEdit, onStock, onDelete, colourFor, setColourFor, onAddColour, onStudio, onNew }) {
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [sure, setSure] = useState(null);

  const shown = useMemo(() => products.filter(p => {
    if (q && !`${p.name} ${p.colour || ''} ${p.sku || ''} ${p.category?.name || ''}`.toLowerCase().includes(q.toLowerCase())) return false;
    if (filter === 'PUBLISHED') return p.status === 'PUBLISHED';
    if (filter === 'DRAFT') return p.status === 'DRAFT';
    if (filter === 'REVIEW') return p.status === 'READY_FOR_REVIEW' || p.status === 'APPROVED';
    if (filter === 'STOCK') return p.stockQuantity <= 3;
    return true;
  }), [products, q, filter]);

  return (
    <div className="tab-content">
      <div className="list-head">
        <div>
          <h2>Sarees</h2>
          <p className="admin-hint">Only <strong>Published</strong> sarees are visible to customers. Cost and supplier are never sent to the storefront.</p>
        </div>
      </div>
      <div className="toolbar">
        <div className="chips" role="group" aria-label="Show">
          {FILTERS.map(([k, l]) => <button key={k} type="button" className={filter === k ? 'on' : ''} aria-pressed={filter === k} onClick={() => setFilter(k)}>{l}</button>)}
        </div>
        <input type="search" placeholder="Search name, colour, SKU" value={q} onChange={e => setQ(e.target.value)} aria-label="Search sarees" />
      </div>

      {products.length === 0 && <p className="ap-empty">No sarees yet. Add your first one with the button above.</p>}
      {products.length > 0 && shown.length === 0 && <p className="ap-empty">No sarees match that.</p>}

      <div className="pgrid-admin">
        {shown.map(p => {
          const img = p.previewImage || p.imageUrl;
          return (
            <div className="list-row admin-product" key={p.id}>
              <div className="ap-thumb">{img ? <img src={imageSrc(img)} alt="" loading="lazy" /> : <i className="bi bi-image" aria-hidden="true"></i>}</div>
              <div className="ap-info">
                <strong>{p.name}</strong>
                <p>
                  <span className={`status-pill status-${p.status}`}>{STATUS_LABEL[p.status] || p.status}</span>
                  {p.source === 'AI_ASSISTED' && <span className="status-pill status-ai">AI-assisted</span>}
                  {' '}{p.category?.name} · Stock <b className={p.stockQuantity === 0 ? 'out' : p.stockQuantity <= 3 ? 'low' : ''}>{p.stockQuantity}</b>
                  {' '}· {inr(p.deal && p.dealPrice ? p.dealPrice : p.price, { decimals: 0 })}
                  {p.marginPercent != null && <> · Margin {p.marginPercent}%</>}
                </p>
                {p.status !== 'PUBLISHED' && p.readinessIssues?.length > 0 && <p className="admin-warn">To publish: {p.readinessIssues.join('; ')}</p>}
              </div>
              <div className="admin-row-actions">
                {nextSteps(p).map(([to, label]) => <button key={to} className={to === 'PUBLISHED' ? 'primary' : ''} onClick={() => onStep(p, to)}>{label}</button>)}
                <button onClick={() => onEdit(p)}>Edit</button>
                <button onClick={() => onStudio(p)}>Studio</button>
                <button onClick={() => setColourFor({ id: p.id, colour: '' })}>Add colour</button>
                <button onClick={() => onStock(p)}>{p.stockQuantity === 0 ? 'Mark in-stock' : 'Mark out-of-stock'}</button>
                {sure === p.id
                  ? <><button className="danger-btn" onClick={() => { setSure(null); onDelete(p); }}>Yes, delete</button><button onClick={() => setSure(null)}>Keep</button></>
                  : <button className="danger-btn" onClick={() => setSure(p.id)}>Delete</button>}
              </div>
              {colourFor?.id === p.id && (
                <form className="add-colour" onSubmit={onAddColour}>
                  <label htmlFor={`nc-${p.id}`}>New colour of this design</label>
                  <input id={`nc-${p.id}`} autoFocus maxLength={40} placeholder="e.g. Emerald green" value={colourFor.colour}
                    onChange={e => setColourFor({ ...colourFor, colour: e.target.value })} />
                  <button type="submit" className="primary">Create draft</button>
                  <button type="button" onClick={() => setColourFor(null)}>Cancel</button>
                </form>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
