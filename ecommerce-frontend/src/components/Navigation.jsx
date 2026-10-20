import React, { useEffect, useId, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useStore } from '../contexts/StoreContext';
import { inrCompact, activePrice, imageSrc } from '../lib/format';

let productsPromise = null;
const loadProducts = () => (productsPromise ||= ApiService.products().catch(() => { productsPromise = null; return []; }));

/** Header search with live suggestions (thumbnail, name, price). Enter still runs a full search. */
export function SearchBox() {
  const [params] = useSearchParams();
  const [term, setTerm] = useState(params.get('q') || '');
  const [all, setAll] = useState([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const navigate = useNavigate();
  const listId = useId();
  const box = useRef(null);

  useEffect(() => { setTerm(params.get('q') || ''); }, [params]);
  useEffect(() => {
    const away = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false); };
    document.addEventListener('pointerdown', away);
    return () => document.removeEventListener('pointerdown', away);
  }, []);

  const q = term.trim().toLowerCase();
  const matches = q.length < 2 ? [] : all.filter(p =>
    [p.name, p.fabric, p.weave, p.colour, p.occasion, p.category?.name].some(v => v && v.toLowerCase().includes(q))).slice(0, 5);
  const showList = open && matches.length > 0;

  const go = (p) => { setOpen(false); setActive(-1); navigate(`/products/${p.id}`); };
  const submit = (e) => {
    e.preventDefault();
    if (showList && active >= 0) return go(matches[active]);
    setOpen(false);
    const t = term.trim();
    navigate(t ? `/?q=${encodeURIComponent(t)}#products` : '/#products');
  };
  const onKey = (e) => {
    if (e.key === 'Escape') { setOpen(false); setActive(-1); return; }
    if (!showList) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(i => (i + 1) % matches.length); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActive(i => (i <= 0 ? matches.length - 1 : i - 1)); }
  };

  return (
    <form className="header-search" role="search" onSubmit={submit} ref={box}>
      <i className="bi bi-search" aria-hidden="true"></i>
      <input
        type="search" value={term} autoComplete="off" placeholder="Search silk, kasavu, bridal…" aria-label="Search sarees"
        role="combobox" aria-expanded={showList} aria-controls={listId} aria-autocomplete="list"
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
        onFocus={() => { loadProducts().then(setAll); setOpen(true); }}
        onChange={e => { setTerm(e.target.value); setOpen(true); setActive(-1); loadProducts().then(setAll); }}
        onKeyDown={onKey}
      />
      <button type="submit" aria-label="Search"><span className="label-text">Search</span><i className="bi bi-search" aria-hidden="true"></i></button>
      {showList && (
        <ul className="suggest" id={listId} role="listbox">
          {matches.map((p, i) => (
            <li key={p.id} id={`${listId}-${i}`} role="option" aria-selected={i === active}
              className={i === active ? 'on' : ''} onPointerDown={(e) => { e.preventDefault(); go(p); }}>
              <img src={imageSrc(p.imageUrl)} alt="" width="40" height="52" loading="lazy" />
              <span><strong>{p.name}</strong><small>{[p.fabric, p.occasion].filter(Boolean).join(' · ') || p.category?.name}</small></span>
              <b>{inrCompact(activePrice(p))}</b>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}

/** Desktop category bar, like the department strip on large fashion sites. */
export function CategoryNav() {
  const { categories } = useStore();
  const [params] = useSearchParams();
  const { pathname } = useLocation();
  const onHome = pathname === '/';
  const cat = onHome ? params.get('category') : null;
  const view = onHome ? params.get('view') : null;
  if (!categories.length) return null;
  return (
    <nav className="catnav" aria-label="Shop by category">
      <ul>
        <li><Link to="/#products" aria-current={onHome && !cat && !view && !params.get('occasion') ? 'page' : undefined}>All sarees</Link></li>
        {categories.map(c => (
          <li key={c.id}><Link to={`/?category=${c.id}#products`} aria-current={cat === String(c.id) ? 'page' : undefined}>{c.name.replace(/ Sarees$/i, '')}</Link></li>
        ))}
        <li><Link className="catnav-deal" to="/?view=deals#products" aria-current={view === 'deals' ? 'page' : undefined}><i className="bi bi-tag" aria-hidden="true"></i> Deals</Link></li>
      </ul>
    </nav>
  );
}

/** Thumb-reach tab bar for phones. Hidden where a sticky buy bar already owns the bottom edge. */
export function BottomNav() {
  const { isAdmin, isLoggedIn } = useAuth();
  const { cartCount, cartPulse, wishIds } = useStore();
  const { pathname } = useLocation();
  if (isAdmin() || pathname.startsWith('/products/') || pathname === '/checkout' || pathname.startsWith('/admin')) return null;
  return (
    <nav className="bottomnav" aria-label="Quick navigation">
      <NavLink to="/" end><i className="bi bi-house" aria-hidden="true"></i><span>Home</span></NavLink>
      <Link to="/#products"><i className="bi bi-grid" aria-hidden="true"></i><span>Shop</span></Link>
      <NavLink to={isLoggedIn() ? '/wishlist' : '/login'} state={isLoggedIn() ? undefined : { from: { pathname: '/wishlist' } }}>
        <i className="bi bi-heart" aria-hidden="true"></i><span>Saved</span>
        {wishIds.length > 0 && <b className="bn-badge">{wishIds.length}</b>}
      </NavLink>
      <NavLink to="/cart">
        <i key={cartPulse} className={`bi bi-bag ${cartPulse ? 'bump' : ''}`} aria-hidden="true"></i><span>Cart</span>
        {cartCount > 0 && <b className="bn-badge">{cartCount > 99 ? '99+' : cartCount}</b>}
      </NavLink>
      <NavLink to={isLoggedIn() ? '/profile' : '/login'}><i className="bi bi-person" aria-hidden="true"></i><span>{isLoggedIn() ? 'Account' : 'Sign in'}</span></NavLink>
    </nav>
  );
}
