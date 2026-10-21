import React from 'react';
import { PhotoManager } from './PhotoManager';
import { StudioAssist } from './StudioAssist';
import { activePrice, discountPercent, imageSrc, inr, isOnDeal } from '../lib/format';

const STATUS_LABEL = { DRAFT: 'Draft', READY_FOR_REVIEW: 'Ready for review', APPROVED: 'Approved', PUBLISHED: 'Published' };

const margin = (p) => {
  const sell = p.deal && p.dealPrice ? Number(p.dealPrice) : Number(p.price);
  if (!p.costPrice || !sell) return null;
  return ((sell - Number(p.costPrice)) / sell) * 100;
};

/** What the owner still needs to fill in, so "Publish" never fails as a surprise. The server remains the authority. */
function checklist(p) {
  const photos = [p.previewImage || p.imageUrl, ...(p.gallery || [])].filter(Boolean).length;
  return {
    required: [
      ['A name', (p.name || '').trim().length >= 3],
      ['A description of at least 30 characters', (p.description || '').trim().length >= 30],
      ['A price', Number(p.price) > 0],
      ['A category', !!p.categoryId],
      ['A cover photo', photos >= 1],
    ],
    better: [
      ['Three or more photos', photos >= 3],
      ['Fabric and colour', !!(p.fabric && p.colour)],
      ['The occasion', !!p.occasion],
      ['A deal price lower than the price (if on deal)', !p.deal || (Number(p.dealPrice) > 0 && Number(p.dealPrice) < Number(p.price))],
    ],
  };
}

/** A card the way shoppers will see it, updated as the owner types. */
function PreviewCard({ product }) {
  const pv = { ...product, price: Number(product.price) || 0, dealPrice: product.dealPrice ? Number(product.dealPrice) : null };
  const cover = product.previewImage || product.imageUrl;
  return (
    <div className="pv-card" aria-label="Preview of the product card">
      <div className="pv-media">
        {cover ? <img src={imageSrc(cover)} alt="" /> : <div className="pv-empty"><i className="bi bi-image" aria-hidden="true"></i><span>Cover photo appears here</span></div>}
        {isOnDeal(pv) && <span className="pv-badge">{discountPercent(pv)}% off</span>}
      </div>
      <p className="pv-cat">{product.categoryName || 'Category'}</p>
      <strong>{product.name || 'Saree name'}</strong>
      <p className="pv-price">{inr(activePrice(pv) || 0, { decimals: 0 })}{isOnDeal(pv) && <s>{inr(pv.price, { decimals: 0 })}</s>}</p>
    </div>
  );
}

export function AdminProductEditor({ product, setProduct, categories, onSave, onNew, category, setCategory, onSaveCategory, onStudio }) {
  const set = (k) => (e) => setProduct({ ...product, [k]: e.target.value });
  const c = checklist(product);
  const ready = c.required.every(([, ok]) => ok);
  const catName = categories.find(x => String(x.id) === String(product.categoryId))?.name;

  return (
    <div className="editor">
      <form id="product-form" onSubmit={onSave} className="editor-main">
        <div className="editor-head">
          {product.id && <h2>Editing: {product.name || 'saree'}</h2>}
          {product.id && (
            <p className="admin-hint">Status: <strong>{STATUS_LABEL[product.status] || product.status}</strong>. Publish or unpublish from the Sarees page. <button type="button" className="linklike" onClick={onNew}>Start a new one instead</button></p>
          )}
        </div>

        <section className="card ed-card">
          <h3>The basics</h3>
          <div className="field-group">
            <label htmlFor="pname">Product name</label>
            <input id="pname" name="pname" placeholder="e.g., Kanchipuram Pure Silk Saree" required value={product.name || ''} onChange={set('name')} />
          </div>
          <div className="field-group">
            <label htmlFor="pdesc">Description <span className="admin-hint">(at least 30 characters, {(product.description || '').length} so far)</span></label>
            <textarea id="pdesc" name="pdesc" rows="4" placeholder="Fabric, weave, border, pallu, what is included…" required value={product.description || ''} onChange={set('description')} />
          </div>
          <div className="admin-two">
            <div className="field-group">
              <label htmlFor="cat">Category</label>
              <select id="cat" name="cat" required value={product.categoryId} onChange={set('categoryId')}>
                <option value="" disabled>Select category</option>
                {categories.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}
              </select>
            </div>
            <div className="field-group">
              <label htmlFor="stock">Stock quantity</label>
              <input id="stock" name="stock" type="number" min="0" placeholder="0" required value={product.stockQuantity ?? ''} onChange={set('stockQuantity')} />
            </div>
            <div className="field-group">
              <label htmlFor="price">Price (₹, before GST)</label>
              <input id="price" name="price" type="number" step="0.01" min="0.01" placeholder="0.00" required value={product.price ?? ''} onChange={set('price')} />
            </div>
            <div className="field-group deal-box">
              <label className="admin-check"><input name="deal" type="checkbox" checked={product.deal || false} onChange={(e) => setProduct({ ...product, deal: e.target.checked })} /> Special deal</label>
              {product.deal && (
                <>
                  <label htmlFor="dealPrice" className="sr-only">Deal price</label>
                  <input id="dealPrice" name="dealPrice" type="number" step="0.01" min="0.01" placeholder="Deal price (₹)" required value={product.dealPrice ?? ''} onChange={set('dealPrice')} />
                </>
              )}
            </div>
          </div>
        </section>

        <section className="card ed-card">
          <div className="ed-card-head">
            <h3>Photos</h3>
            <button type="button" className="btn btn-outline small" onClick={() => onStudio(product)}><i className="bi bi-stars" aria-hidden="true"></i> Open photo studio</button>
          </div>
          <PhotoManager cover={product.previewImage || product.imageUrl} gallery={product.gallery || []}
            onChange={({ imageUrl, gallery }) => setProduct({ ...product, imageUrl, gallery, previewImage: null, previewGallery: null })} />
        </section>

        <fieldset className="card ed-card admin-fieldset">
          <legend>Saree details (shown to customers)</legend>
          <div className="admin-two">
            <div className="field-group"><label htmlFor="fabric">Fabric</label>
              <input id="fabric" list="fabrics" value={product.fabric || ''} onChange={set('fabric')} />
              <datalist id="fabrics"><option>Silk</option><option>Cotton</option><option>Silk cotton</option><option>Georgette</option><option>Organza</option><option>Linen</option></datalist></div>
            <div className="field-group"><label htmlFor="weave">Weave</label>
              <input id="weave" placeholder="Kanchipuram, Banarasi…" value={product.weave || ''} onChange={set('weave')} /></div>
            <div className="field-group"><label htmlFor="occasion">Occasion</label>
              <input id="occasion" list="occasions" value={product.occasion || ''} onChange={set('occasion')} />
              <datalist id="occasions"><option>Wedding</option><option>Festive</option><option>Party</option><option>Everyday</option><option>Office</option></datalist></div>
            <div className="field-group"><label htmlFor="colour">Colour</label>
              <input id="colour" value={product.colour || ''} onChange={set('colour')} /></div>
            <div className="field-group"><label htmlFor="sku">SKU (your own code)</label>
              <input id="sku" maxLength={40} placeholder="KAN-RED-01" value={product.sku || ''} onChange={(e) => setProduct({ ...product, sku: e.target.value.replace(/[^A-Za-z0-9._-]/g, '') })} /></div>
            <div className="field-group"><label htmlFor="region">Region of origin</label>
              <input id="region" placeholder="Tamil Nadu" value={product.region || ''} onChange={set('region')} /></div>
            <div className="field-group"><label htmlFor="len">Length (metres)</label>
              <input id="len" type="number" step="0.01" min="0" max="20" value={product.lengthMeters ?? ''} onChange={set('lengthMeters')} /></div>
          </div>
          <label className="admin-check"><input type="checkbox" checked={!!product.blouseIncluded} onChange={(e) => setProduct({ ...product, blouseIncluded: e.target.checked })} /> Blouse piece included</label>
          <div className="field-group"><label htmlFor="care">Care instructions</label>
            <input id="care" maxLength="500" placeholder="Dry clean only" value={product.careInstructions || ''} onChange={set('careInstructions')} /></div>
        </fieldset>

        <StudioAssist product={product} onApply={(changes) => {
          const { aiAssisted, ...fields } = changes;
          setProduct(prev => ({ ...prev, ...fields, ...(aiAssisted ? { source: 'AI_ASSISTED' } : {}) }));
        }} />

        <fieldset className="card ed-card admin-fieldset">
          <legend>Business details (private, never shown to customers)</legend>
          <div className="admin-two">
            <div className="field-group"><label htmlFor="cost">Your cost per piece (₹)</label>
              <input id="cost" type="number" step="0.01" min="0" value={product.costPrice ?? ''} onChange={set('costPrice')} /></div>
            <div className="field-group"><label htmlFor="supplier">Supplier</label>
              <input id="supplier" maxLength="120" value={product.supplierName || ''} onChange={set('supplierName')} /></div>
          </div>
          {margin(product) !== null && (
            <p className={`admin-hint ${margin(product) < 0 ? 'admin-warn' : ''}`}>
              Gross margin before GST and shipping: <strong>{margin(product).toFixed(1)}%</strong>{margin(product) < 0 && ' — you would sell below cost'}
            </p>
          )}
        </fieldset>

        <div className="savebar">
          <span className={ready ? 'ok' : ''}>{ready ? 'Ready to publish' : `${c.required.filter(([, ok]) => !ok).length} thing(s) still needed`}</span>
          <button className="btn btn-primary" type="submit">{product.id ? 'Save changes' : 'Save as draft'}</button>
          {!product.id && product.source !== 'AI_ASSISTED' && (
            <button type="button" className="btn btn-outline" onClick={(e) => { const f = e.currentTarget.form; if (f.reportValidity()) onSave(e, true); }}>Save and publish</button>
          )}
        </div>
      </form>

      <aside className="editor-side">
        <div className="card side-card">
          <h3>Shoppers will see</h3>
          <PreviewCard product={{ ...product, categoryName: catName }} />
        </div>
        <div className="card side-card">
          <h3>Checklist</h3>
          <ul className="check">
            {c.required.map(([l, ok]) => <li key={l} className={ok ? 'ok' : ''}><i className={`bi ${ok ? 'bi-check-circle-fill' : 'bi-circle'}`} aria-hidden="true"></i>{l}</li>)}
          </ul>
          <p className="check-title">Makes it sell better</p>
          <ul className="check soft">
            {c.better.map(([l, ok]) => <li key={l} className={ok ? 'ok' : ''}><i className={`bi ${ok ? 'bi-check-circle-fill' : 'bi-dash-circle'}`} aria-hidden="true"></i>{l}</li>)}
          </ul>
        </div>
        <form onSubmit={onSaveCategory} className="card side-card">
          <h3>Add a category</h3>
          <div className="field-group"><label htmlFor="cname">Name</label>
            <input id="cname" name="cname" placeholder="e.g., Linen Sarees" required value={category.name || ''} onChange={(e) => setCategory({ ...category, name: e.target.value })} /></div>
          <div className="field-group"><label htmlFor="cdesc">Description</label>
            <input id="cdesc" name="cdesc" placeholder="Short description" value={category.description || ''} onChange={(e) => setCategory({ ...category, description: e.target.value })} /></div>
          <button className="btn btn-outline" type="submit">Save category</button>
        </form>
      </aside>
    </div>
  );
}
