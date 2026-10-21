import React, { useRef, useState } from 'react';
import { ApiService } from '../services/api';
import { useToast } from '../contexts/ToastContext';

const download = (blob, name) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; a.click();
  URL.revokeObjectURL(url);
};

const ACTION = { CREATE: 'New draft', UPDATE: 'Update', ERROR: 'Needs fixing' };

/** Bulk catalogue work: import many sarees from a spreadsheet, export everything, and feeds for other platforms. */
export function AdminCatalogue({ onDone }) {
  const [file, setFile] = useState(null);
  const [text, setText] = useState('');
  const [report, setReport] = useState(null);
  const [busy, setBusy] = useState(false);
  const input = useRef(null);
  const toast = useToast();
  const apiBase = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');

  const pick = async (f) => {
    setReport(null);
    setFile(f || null);
    setText(f ? await f.text() : '');
  };

  const run = async (dryRun) => {
    setBusy(true);
    try {
      const r = await ApiService.importCatalogue(text, dryRun);
      setReport(r);
      if (!dryRun) {
        toast.success(`${r.created} added as drafts, ${r.updated} updated${r.failed ? `, ${r.failed} need fixing` : ''}`);
        onDone?.();
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not read that file');
    } finally {
      setBusy(false);
    }
  };

  const save = async (fn, name) => {
    try { download(await fn(), name); } catch { toast.error('Download failed'); }
  };

  const copy = (value) => navigator.clipboard?.writeText(value).then(() => toast.success('Link copied'), () => toast.error('Copy it manually'));
  const feeds = [
    ['Google Shopping and Instagram/Facebook Shops', `${apiBase}/store/feed/google.xml`, 'Paste this link into Google Merchant Center (Products, Feeds) or Meta Commerce Manager (Data sources, Data feed). They re-read it automatically.'],
    ['Sitemap for Google Search', `${apiBase}/store/sitemap.xml`, 'Submit in Google Search Console so every saree page can be found.'],
  ];

  return (
    <div className="tab-content catalogue">
      <h2>Catalogue tools</h2>
      <p className="admin-hint">Add many sarees at once, keep a backup, and share your catalogue with Google, Instagram and Facebook.</p>

      <section className="cat-card">
        <h3><i className="bi bi-file-earmark-spreadsheet" aria-hidden="true"></i> Import from a spreadsheet</h3>
        <ol className="cat-steps">
          <li>Download the template and fill one row per colour. Rows with a <strong>SKU</strong> that already exists update that product.</li>
          <li>Upload it and press <strong>Check file</strong>: nothing is saved yet.</li>
          <li>Fix anything marked, then <strong>Import</strong>. New sarees arrive as <em>drafts</em>; you publish them.</li>
        </ol>
        <div className="cat-actions">
          <button type="button" className="btn btn-outline" onClick={() => save(ApiService.catalogueTemplate, 'infinitystore-import-template.csv')}>
            <i className="bi bi-download" aria-hidden="true"></i> Template
          </button>
          <button type="button" className="btn btn-outline" onClick={() => save(ApiService.catalogueExport, 'infinitystore-catalogue.csv')}>
            <i className="bi bi-box-arrow-down" aria-hidden="true"></i> Export all products
          </button>
        </div>
        <div className={`cat-drop${file ? ' has' : ''}`}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => { e.preventDefault(); pick(e.dataTransfer.files?.[0]); }}>
          <input ref={input} type="file" accept=".csv,text/csv" hidden onChange={(e) => pick(e.target.files?.[0])} />
          <i className="bi bi-cloud-arrow-up" aria-hidden="true"></i>
          {file ? <strong>{file.name}</strong> : <span>Drop your .csv here</span>}
          <button type="button" className="btn btn-outline" onClick={() => input.current?.click()}>{file ? 'Choose another' : 'Choose file'}</button>
        </div>
        {file && (
          <div className="cat-actions">
            <button type="button" className="btn btn-outline" disabled={busy} onClick={() => run(true)}>Check file</button>
            <button type="button" className="btn btn-primary" disabled={busy || !report || report.created + report.updated === 0}
              onClick={() => run(false)} title={report ? '' : 'Check the file first'}>
              {busy ? 'Working…' : report?.dryRun === false ? 'Imported' : 'Import now'}
            </button>
          </div>
        )}
        {report && (
          <div className="cat-report" role="status">
            <div className="cat-summary">
              <span className="ok"><strong>{report.created}</strong> new</span>
              <span><strong>{report.updated}</strong> updates</span>
              <span className={report.failed ? 'bad' : ''}><strong>{report.failed}</strong> need fixing</span>
              <small>{report.dryRun ? 'Preview only, nothing saved' : 'Saved'}</small>
            </div>
            <ul>
              {report.rows.map(r => (
                <li key={r.row} className={`r-${r.action.toLowerCase()}`}>
                  <span className="cat-row">Row {r.row}</span>
                  <span className="cat-tag">{ACTION[r.action]}</span>
                  <span className="cat-name">{r.name || r.sku}</span>
                  {r.action === 'ERROR' && <em>{r.message}</em>}
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <section className="cat-card">
        <h3><i className="bi bi-share" aria-hidden="true"></i> Sell on other platforms</h3>
        <p>Your catalogue is always up to date at these links: published sarees only, with prices, stock and colour groups.</p>
        {feeds.map(([title, url, help]) => (
          <div className="cat-feed" key={url}>
            <strong>{title}</strong>
            <div className="cat-url"><code>{url}</code><button type="button" className="btn btn-outline" onClick={() => copy(url)}>Copy</button></div>
            <small>{help}</small>
          </div>
        ))}
        <p className="cat-note"><i className="bi bi-info-circle" aria-hidden="true"></i> Shopify, Amazon and Meesho don't read a link like this; they need their own seller tools. Export the CSV above to move products between them.</p>
      </section>
    </div>
  );
}
