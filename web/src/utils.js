/**
 * Utility functions and toast notifications.
 */

// ── HTML helpers ──────────────────────────────────────────
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'className') el.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'innerHTML') el.innerHTML = v;
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    if (typeof child === 'string' || typeof child === 'number') el.appendChild(document.createTextNode(String(child)));
    else el.appendChild(child);
  }
  return el;
}

export function icon(name, extraClass = '') {
  const el = document.createElement('span');
  el.setAttribute('aria-hidden', 'true');
  el.className = `material-symbols-outlined ${extraClass}`.trim();
  el.textContent = name;
  return el;
}

// ── Toast notifications ───────────────────────────────────
let toastContainer;

function ensureToastContainer() {
  if (!toastContainer) {
    toastContainer = h('div', { className: 'toast-container', 'aria-live': 'polite' });
    document.body.appendChild(toastContainer);
  }
}

export function toast(message, duration = 3200) {
  ensureToastContainer();
  const el = h('div', { className: 'toast', role: 'status' }, message);
  toastContainer.appendChild(el);
  setTimeout(() => {
    el.classList.add('toast--leaving');
    setTimeout(() => el.remove(), 220);
  }, duration);
}

// ── Formatting ────────────────────────────────────────────
export function formatNumber(n) {
  n = Number(n) || 0;
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
  return String(n);
}

/** A measured value as a scientist would write it: 0.52, 1.2e-4, 12 400. */
export function formatValue(v) {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '—';
  v = Number(v);
  const a = Math.abs(v);
  if (a === 0) return '0';
  if (a < 1e-3 || a >= 1e6) {
    const [m, e] = v.toExponential(2).split('e');
    const mant = m.replace(/0+$/, '').replace(/\.$/, '.0');
    const sup = String(Number(e)).replace(/[-0-9]/g, c => '⁻⁰¹²³⁴⁵⁶⁷⁸⁹'['-0123456789'.indexOf(c)]);
    return `${mant} × 10${sup}`;
  }
  if (Number.isInteger(v)) return v.toLocaleString('en-US').replace(/,/g, '\u2009');
  const digits = a >= 100 ? 1 : a >= 1 ? 2 : 3;
  return String(Number(v.toFixed(digits)));
}

/** "Moreno '23"-style label for a paper, with sensible fallbacks. */
export function paperLabel(p) {
  if (!p) return '';
  return p.short_label || p.authors?.[0]?.name?.split(' ').pop() || (p.title || '').slice(0, 18);
}

export function yearRange(papers) {
  const years = papers.filter(p => p.year > 0).map(p => p.year);
  if (!years.length) return 'n/a';
  return `${Math.min(...years)}–${Math.max(...years)}`;
}

export function shortAuthors(authors) {
  if (!authors || !authors.length) return '';
  if (authors.length === 1) return authors[0].name;
  if (authors.length === 2) return `${authors[0].name} & ${authors[1].name}`;
  return `${authors[0].name} et al.`;
}

export function confidenceTag(level) {
  const filled = level === 'HIGH' ? 3 : level === 'MED' ? 2 : 1;
  const bar = [0, 1, 2].map(i =>
    h('div', { className: `confidence-bar__seg ${i < filled ? 'filled' : ''}` })
  );
  return h('span', { className: 'confidence-tag' },
    h('span', { className: 'confidence-tag__text' }, level),
    h('span', { className: 'confidence-bar' }, ...bar)
  );
}

export function evidenceQuote(evidence, label = '') {
  const container = h('figure', { className: 'evidence-quote' });
  container.appendChild(h('blockquote', { className: 'evidence-quote__text' }, `\u201C${evidence.quote}\u201D`));
  const parts = [];
  if (label) parts.push(label);
  if (evidence.section) parts.push(evidence.section);
  if (evidence.page) parts.push(`p. ${evidence.page}`);
  if (parts.length) {
    container.appendChild(h('figcaption', { className: 'evidence-quote__source' }, parts.join(' · ')));
  }
  return container;
}

// ── Skeleton loading ──────────────────────────────────────
export function skeleton(width, height = '16px') {
  return h('div', {
    className: 'skeleton',
    style: { width, height, marginBottom: '8px' },
  });
}

export function skeletonRows(count = 5) {
  const container = h('div');
  for (let i = 0; i < count; i++) {
    const row = h('div', { style: { padding: '16px 0', borderBottom: '1px solid var(--rule)' } },
      skeleton('60%', '20px'),
      skeleton('40%', '14px'),
      skeleton('80%', '14px'),
    );
    container.appendChild(row);
  }
  return container;
}

// ── Debounce ──────────────────────────────────────────────
export function debounce(fn, ms) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

/** True when the visitor asked the OS for less motion. */
export function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

/** Title-case a pipeline label like "coulombic efficiency". */
export function capitalize(s) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

/** Empty-state block used across pages. */
export function emptyState(iconName, title, body, action) {
  return h('div', { className: 'empty-state' },
    h('div', { className: 'empty-state__icon' }, icon(iconName)),
    h('div', { className: 'empty-state__title' }, title),
    body ? h('p', { className: 'empty-state__body' }, body) : null,
    action || null,
  );
}

/** "1 citation", "1.2k citations". */
export function citations(n) {
  n = Number(n) || 0;
  return `${formatNumber(n)} citation${n === 1 ? '' : 's'}`;
}
