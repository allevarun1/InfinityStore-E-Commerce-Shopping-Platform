import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';
import { inr } from '../lib/format';

/**
 * Product Studio helper inside the product form. It only suggests: the owner chooses what to apply,
 * and the product is still saved as a draft that needs review.
 */
export function StudioAssist({ product, onApply }) {
  const toast = useToast();
  const [status, setStatus] = useState(null);
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState(null);

  useEffect(() => {
    ApiService.studioStatus().then(setStatus).catch(() => setStatus(null));
  }, []);

  const generate = () => {
    if (!product.categoryId) {
      toast.info('Choose a category first');
      return;
    }
    setBusy(true);
    ApiService.studioDraft({
      categoryId: Number(product.categoryId),
      fabric: product.fabric, weave: product.weave, occasion: product.occasion, colour: product.colour,
      region: product.region, lengthMeters: product.lengthMeters === '' ? null : product.lengthMeters,
      blouseIncluded: product.blouseIncluded, careInstructions: product.careInstructions,
      costPrice: product.costPrice === '' ? null : product.costPrice, notes,
    })
      .then(setDraft)
      .catch(err => toast.error(err.response?.data?.message || 'Could not draft the text'))
      .finally(() => setBusy(false));
  };

  return (
    <fieldset className="admin-fieldset studio">
      <legend>Draft the text for me</legend>
      <p className="admin-hint">
        Fill in the saree details above, then get a suggested name, description and starting price.
        {status && (status.external
          ? ' The facts you typed (never your cost) are sent to the AI provider to write the text.'
          : ' This uses an offline writer that only rearranges the details you entered, so it cannot invent claims.')}
      </p>
      <div className="field-group">
        <label htmlFor="studio-notes">Anything to work in (optional)</label>
        <input id="studio-notes" maxLength="500" placeholder="e.g. woven by a family in Kanchipuram for three generations"
          value={notes} onChange={e => setNotes(e.target.value)} />
      </div>
      <button type="button" disabled={busy} onClick={generate}>{busy ? 'Drafting…' : 'Suggest name and description'}</button>

      {draft && (
        <div className="studio-result" aria-live="polite">
          {draft.warning && <p className="admin-warn" role="alert">{draft.warning}</p>}
          <p><strong>{draft.name}</strong></p>
          <p>{draft.description}</p>
          {draft.suggestedPrice != null && (
            <p className="admin-hint">Suggested starting price {inr(draft.suggestedPrice)} before GST. {draft.priceReasoning}</p>
          )}
          {draft.marketContext && <p className="admin-hint">{draft.marketContext}</p>}
          <div className="admin-actions">
            <button type="button" className="primary" onClick={() => { onApply({ name: draft.name, description: draft.description, aiAssisted: draft.sentToExternalService }); setDraft(null); }}>Use this text</button>
            {draft.suggestedPrice != null && (
              <button type="button" onClick={() => onApply({ price: draft.suggestedPrice })}>Use suggested price</button>
            )}
            <button type="button" onClick={() => setDraft(null)}>Discard</button>
          </div>
          <p className="admin-hint">
            {draft.generatedBy === 'template' ? 'Written by the offline template.' : 'Written by an AI model, so read it carefully before publishing.'}
          </p>
        </div>
      )}
    </fieldset>
  );
}
