/**
 * Visual views of a research map (Graph tab): Connections, Citations, Timeline
 * and Values. All but Citations are built from the map itself, so they
 * show structure even when the papers barely cite each other.
 */
import { h, icon, formatValue, paperLabel, capitalize, citations as citationText, prefersReducedMotion } from '../utils.js';
import { buildCompareColumns, compareCell } from '../compare.js';
import { renderGraph } from './graph.js';

const NS = 'http://www.w3.org/2000/svg';
const s = (tag, attrs = {}, ...kids) => {
  const el = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) if (v !== undefined && v !== null) el.setAttribute(k, v);
  kids.flat().forEach(k => k != null && el.appendChild(typeof k === 'string' ? document.createTextNode(k) : k));
  return el;
};
const trunc = (t, n) => (t.length > n ? `${t.slice(0, n - 1)}…` : t);

const VIEWS = [
  ['connections', 'hub', 'Connections', 'Papers linked by the findings, gaps, conflicts, tools and quantities they share.'],
  ['citations', 'account_tree', 'Citations', 'Which papers cite which, using the server’s layout.'],
  ['timeline', 'timeline', 'Timeline', 'When each paper was published and how often it is cited.'],
  ['values', 'straighten', 'Values', 'Every reported value for the same quantity, side by side.'],
];

export function renderMapViews(container, map, { graphData, onOpenPaper, initial = 'connections', onViewChange } = {}) {
  let cleanup = null;
  const stage = h('div', { className: 'viz-stage' });
  const caption = h('p', { className: 'viz-caption' });
  const btns = VIEWS.map(([id, ic, label]) => h('button', {
    className: 'viz-switch__btn', type: 'button', role: 'tab', 'data-view': id,
    onClick: () => show(id),
  }, icon(ic), h('span', {}, label)));
  container.append(h('div', { className: 'viz-head' }, h('div', { className: 'viz-switch', role: 'tablist', 'aria-label': 'Visualisation' }, ...btns), caption), stage);

  function show(id) {
    if (!VIEWS.some(v => v[0] === id)) id = 'connections';
    if (cleanup) { cleanup(); cleanup = null; }
    btns.forEach(b => { const on = b.dataset.view === id; b.classList.toggle('active', on); b.setAttribute('aria-selected', String(on)); });
    caption.textContent = VIEWS.find(v => v[0] === id)[3];
    stage.replaceChildren();
    const ctx = { map, onOpenPaper };
    if (id === 'connections') cleanup = connectionsView(stage, ctx);
    else if (id === 'citations') {
      if (graphData?.nodes?.length) cleanup = renderGraph(stage, graphData, map.papers, { onOpen: onOpenPaper });
      else stage.appendChild(empty('account_tree', 'No citation links', 'The databases returned no references between these papers. Try Connections or Overlap.'));
    } else if (id === 'timeline') cleanup = timelineView(stage, ctx);
    else valuesView(stage, ctx);
    onViewChange?.(id);
  }
  show(initial);
  return () => cleanup && cleanup();
}

function empty(ic, title, body) {
  return h('div', { className: 'empty-state' }, h('div', { className: 'empty-state__icon' }, icon(ic)),
    h('div', { className: 'empty-state__title' }, title), h('p', { className: 'empty-state__body' }, body));
}

// ════════════════════════════════════════════════════════
// Connections: a force-directed network of papers and what links them
// ════════════════════════════════════════════════════════
const KINDS = {
  paper: { label: 'Papers', r: 15 },
  finding: { label: 'Findings', r: 6 },
  gap: { label: 'Gaps', r: 9 },
  conflict: { label: 'Conflicts', r: 9 },
  tool: { label: 'Tools', r: 6.5 },
  metric: { label: 'Quantities', r: 7.5 },
};

function buildNetwork(map) {
  const nodes = [];
  const links = [];
  const byId = new Map();
  const add = n => { byId.set(n.id, n); nodes.push(n); return n; };
  const inMap = new Set(map.papers.map(p => p.id));
  const link = (a, b) => { if (byId.has(a) && byId.has(b)) links.push({ source: byId.get(a), target: byId.get(b) }); };

  map.papers.forEach(p => {
    const e = p.extraction || {};
    const weight = (e.findings?.length || 0) + (e.metrics?.length || 0) + (e.limitations?.length || 0);
    add({ id: p.id, kind: 'paper', label: paperLabel(p), title: p.title, paper: p, r: 12 + Math.min(10, Math.sqrt(weight) * 2.2) });
  });
  (map.findings || []).forEach(f => {
    const ps = (f.paper_ids || []).filter(id => inMap.has(id));
    if (!ps.length) return;
    add({ id: `f:${f.id}`, kind: 'finding', label: trunc(f.text, 40), title: f.text, item: f, quote: f.evidence, papers: ps });
    ps.forEach(pid => link(pid, `f:${f.id}`));
  });
  (map.gaps || []).forEach((g, i) => {
    const ps = (g.supporting_paper_ids || []).filter(id => inMap.has(id));
    add({ id: `g:${g.id}`, kind: 'gap', label: `G${i + 1}`, title: g.statement, item: g, papers: ps, sub: g.pattern });
    ps.forEach(pid => link(pid, `g:${g.id}`));
  });
  (map.contradictions || []).forEach(c => {
    const ps = [...new Set(c.entries.map(e => e.paper_id))].filter(id => inMap.has(id));
    add({ id: `c:${c.id}`, kind: 'conflict', label: trunc(capitalize(c.metric), 22), title: capitalize(c.metric), item: c, papers: ps, sub: c.likely_reason });
    ps.forEach(pid => link(pid, `c:${c.id}`));
  });
  // Tools: shared ones first; keep the network readable.
  const tools = [...(map.tools || [])].filter(t => (t.paper_ids || []).some(id => inMap.has(id)))
    .sort((a, b) => b.count - a.count).slice(0, 14);
  tools.forEach(t => {
    const ps = t.paper_ids.filter(id => inMap.has(id));
    add({ id: `t:${t.name}`, kind: 'tool', label: trunc(t.name, 24), title: t.name, papers: ps, sub: t.category ? capitalize(t.category) : '' });
    ps.forEach(pid => link(pid, `t:${t.name}`));
  });
  // Quantities measured by the papers (grouped the way the Compare table groups them).
  const cols = buildCompareColumns(map.papers.filter(p => p.extraction)).filter(c => c.id.startsWith('metric:')).slice(0, 10);
  cols.forEach(c => {
    const ps = map.papers.filter(p => compareCell(p, c).value !== undefined).map(p => p.id);
    if (!ps.length) return;
    add({ id: c.id, kind: 'metric', label: trunc(c.title, 24), title: c.title, papers: ps, sub: c.unit, col: c });
    ps.forEach(pid => link(pid, c.id));
  });
  return { nodes, links };
}

function simulate(nodes, links, w, hgt, ticks = 360) {
  const cx = w / 2, cy = hgt / 2;
  const papers = nodes.filter(n => n.kind === 'paper');
  papers.forEach((n, i) => {
    const a = (i / Math.max(1, papers.length)) * Math.PI * 2;
    const R = Math.min(w, hgt) * (papers.length > 1 ? 0.28 : 0);
    n.x = cx + Math.cos(a) * R; n.y = cy + Math.sin(a) * R;
  });
  const pos = new Map(papers.map(p => [p.id, p]));
  nodes.filter(n => n.kind !== 'paper').forEach((n, i) => {
    const ps = (n.papers || []).map(id => pos.get(id)).filter(Boolean);
    const mx = ps.length ? ps.reduce((a, p) => a + p.x, 0) / ps.length : cx;
    const my = ps.length ? ps.reduce((a, p) => a + p.y, 0) / ps.length : cy;
    const a = i * 2.39996; // golden angle spreads leaves around their paper
    n.x = mx + Math.cos(a) * 40; n.y = my + Math.sin(a) * 40;
  });
  nodes.forEach(n => { n.vx = 0; n.vy = 0; n.r = n.r || KINDS[n.kind].r; });

  for (let t = 0; t < ticks; t++) {
    const alpha = 1 - t / ticks;
    for (let i = 0; i < nodes.length; i++) {
      const a = nodes[i];
      for (let j = i + 1; j < nodes.length; j++) {
        const b = nodes[j];
        let dx = b.x - a.x, dy = b.y - a.y;
        let d2 = dx * dx + dy * dy || 0.01;
        const d = Math.sqrt(d2);
        const rep = (900 * alpha) / d2;
        dx /= d; dy /= d;
        a.vx -= dx * rep; a.vy -= dy * rep; b.vx += dx * rep; b.vy += dy * rep;
        const min = a.r + b.r + 6;
        if (d < min) {
          const push = (min - d) / 2;
          a.x -= dx * push; a.y -= dy * push; b.x += dx * push; b.y += dy * push;
        }
      }
    }
    links.forEach(({ source: a, target: b }) => {
      const dx = b.x - a.x, dy = b.y - a.y;
      const d = Math.sqrt(dx * dx + dy * dy) || 0.01;
      const rest = b.kind === 'finding' ? 55 : 95;
      const k = ((d - rest) / d) * 0.06 * alpha;
      a.vx += dx * k; a.vy += dy * k; b.vx -= dx * k; b.vy -= dy * k;
    });
    nodes.forEach(n => {
      n.vx += (cx - n.x) * 0.004 * alpha; n.vy += (cy - n.y) * 0.004 * alpha;
      n.x += n.vx; n.y += n.vy; n.vx *= 0.55; n.vy *= 0.55;
    });
  }
}

function connectionsView(stage, { map, onOpenPaper }) {
  const { nodes, links } = buildNetwork(map);
  if (nodes.length < 2) {
    stage.appendChild(empty('hub', 'Not enough to connect', 'This map has too little extracted data to draw a network.'));
    return null;
  }
  const W = 1000, H = 620;
  simulate(nodes, links, W, H);
  const xs = nodes.map(n => n.x), ys = nodes.map(n => n.y);
  const pad = 60;
  let vb = { x: Math.min(...xs) - pad, y: Math.min(...ys) - pad, w: Math.max(...xs) - Math.min(...xs) + pad * 2, h: Math.max(...ys) - Math.min(...ys) + pad * 2 };
  const home = { ...vb };

  const hidden = new Set();
  const counts = {};
  nodes.forEach(n => { counts[n.kind] = (counts[n.kind] || 0) + 1; });

  const svg = s('svg', { class: 'net', role: 'img', 'aria-label': `Network of ${counts.paper} papers and what connects them` });
  const edgeLayer = s('g', { class: 'net__edges' });
  const nodeLayer = s('g', { class: 'net__nodes' });
  svg.append(edgeLayer, nodeLayer);

  const edgeEls = links.map(l => {
    const el = s('line', { class: `net__edge net__edge--${l.target.kind}` });
    edgeLayer.appendChild(el);
    return { el, l };
  });
  const glyph = { finding: '', gap: '?', conflict: '!', tool: '', metric: '#' };
  const nodeEls = nodes.map(n => {
    const g = s('g', { class: `net__node net__node--${n.kind}${n.paper?.source === 'upload' ? ' is-upload' : ''}`, tabindex: '0', role: 'button', 'aria-label': `${KINDS[n.kind].label.replace(/s$/, '')}: ${n.title}` });
    if (n.kind === 'tool') g.appendChild(s('rect', { x: -n.r, y: -n.r, width: n.r * 2, height: n.r * 2, rx: 2, class: 'net__shape' }));
    else if (n.kind === 'conflict') g.appendChild(s('path', { d: `M0 ${-n.r} L${n.r} 0 L0 ${n.r} L${-n.r} 0 Z`, class: 'net__shape' }));
    else g.appendChild(s('circle', { r: n.r, class: 'net__shape' }));
    if (n.kind === 'paper' && n.paper?.source === 'upload') g.appendChild(s('circle', { r: n.r + 4, class: 'net__upload-ring' }));
    if (glyph[n.kind]) g.appendChild(s('text', { class: 'net__glyph', 'text-anchor': 'middle', dy: '0.35em' }, glyph[n.kind]));
    const crowded = nodes.length > 45;
    const showLabel = n.kind === 'paper' || n.kind === 'metric' || n.kind === 'gap' || n.kind === 'conflict' || (n.kind === 'tool' && !crowded);
    const text = s('text', { class: `net__label${showLabel ? '' : ' net__label--hover'}`, 'text-anchor': 'middle', y: n.r + 13 }, n.kind === 'gap' ? n.label : n.label);
    g.appendChild(text);
    nodeLayer.appendChild(g);
    return { g, n };
  });

  function place() {
    edgeEls.forEach(({ el, l }) => {
      el.setAttribute('x1', l.source.x.toFixed(1)); el.setAttribute('y1', l.source.y.toFixed(1));
      el.setAttribute('x2', l.target.x.toFixed(1)); el.setAttribute('y2', l.target.y.toFixed(1));
    });
    nodeEls.forEach(({ g, n }) => g.setAttribute('transform', `translate(${n.x.toFixed(1)} ${n.y.toFixed(1)})`));
  }
  const applyView = () => svg.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.w} ${vb.h}`);

  // Neighbourhood highlight
  const nbrs = new Map(nodes.map(n => [n, new Set([n])]));
  links.forEach(({ source, target }) => { nbrs.get(source).add(target); nbrs.get(target).add(source); });
  let selected = null, hover = null;
  function paint() {
    const focus = hover || selected;
    const set = focus ? nbrs.get(focus) : null;
    nodeEls.forEach(({ g, n }) => {
      const off = hidden.has(n.kind);
      g.classList.toggle('is-hidden', off);
      g.classList.toggle('is-dim', !!set && !set.has(n));
      g.classList.toggle('is-focus', n === focus);
      g.classList.toggle('is-selected', n === selected);
    });
    edgeEls.forEach(({ el, l }) => {
      const off = hidden.has(l.source.kind) || hidden.has(l.target.kind);
      el.classList.toggle('is-hidden', off);
      el.classList.toggle('is-dim', !!focus && l.source !== focus && l.target !== focus);
      el.classList.toggle('is-hot', !!focus && (l.source === focus || l.target === focus));
    });
  }

  // Detail panel
  const panel = h('aside', { className: 'net-panel' });
  const papersById = new Map(map.papers.map(p => [p.id, p]));
  const chip = id => h('button', { className: 'paper-chip', type: 'button', onClick: () => onOpenPaper?.(id), title: papersById.get(id)?.title || '' }, paperLabel(papersById.get(id)) || id);
  function showPanel(n) {
    if (!n) {
      panel.replaceChildren(
        h('div', { className: 'net-panel__kicker' }, 'Explore'),
        h('p', { className: 'net-panel__hint' }, 'Click any node to see what it is and which papers it links. Hover to light up its neighbours. Drag nodes to untangle them; scroll or use the buttons to zoom.'),
        h('div', { className: 'net-panel__stats' },
          ...Object.entries(counts).map(([k, v]) => h('div', { className: 'net-panel__stat' }, h('b', {}, String(v)), ` ${KINDS[k].label.toLowerCase()}`))),
        (() => {
          const bridges = nodes.filter(x => x.kind !== 'paper' && (x.papers?.length || 0) > 1).sort((a, b) => b.papers.length - a.papers.length).slice(0, 4);
          return bridges.length ? h('div', {}, h('div', { className: 'net-panel__kicker' }, 'Strongest links'),
            h('ul', { className: 'net-panel__list' }, ...bridges.map(b => h('li', {}, h('button', { className: 'link-btn', type: 'button', onClick: () => select(b) }, trunc(b.title, 60)), h('span', { className: 'net-panel__count' }, ` · ${b.papers.length} papers`))))) : null;
        })(),
      );
      return;
    }
    const kindLabel = { paper: 'Paper', finding: 'Finding', gap: 'Author-stated gap', conflict: 'Conflict', tool: 'Tool or dataset', metric: 'Measured quantity' }[n.kind];
    const kids = [
      h('div', { className: `net-panel__kicker net-panel__kicker--${n.kind}` }, kindLabel),
      h('h3', { className: 'net-panel__title' }, n.title),
    ];
    if (n.kind === 'paper') {
      const p = n.paper;
      kids.push(h('div', { className: 'net-panel__meta' }, [p.year > 0 ? p.year : null, p.venue || null, p.source === 'upload' ? 'Your PDF' : citationText(p.citation_count)].filter(Boolean).join(' · ')));
      const linked = [...nbrs.get(n)].filter(x => x !== n);
      const by = k => linked.filter(x => x.kind === k).length;
      kids.push(h('div', { className: 'net-panel__stats' }, ...['finding', 'gap', 'conflict', 'tool', 'metric'].filter(k => by(k)).map(k => h('div', { className: 'net-panel__stat' }, h('b', {}, String(by(k))), ` ${KINDS[k].label.toLowerCase()}`))));
      const partners = new Map();
      linked.forEach(x => (x.papers || []).forEach(pid => { if (pid !== n.id) partners.set(pid, (partners.get(pid) || 0) + 1); }));
      if (partners.size) kids.push(h('div', { className: 'net-panel__kicker' }, 'Shares most with'),
        h('div', { className: 'finding-item__papers' }, ...[...partners.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([pid]) => chip(pid))));
      kids.push(h('button', { className: 'btn btn-primary btn-sm net-panel__open', type: 'button', onClick: () => onOpenPaper?.(n.id) }, 'Open paper', icon('arrow_forward')));
    } else {
      if (n.sub) kids.push(h('div', { className: 'net-panel__meta' }, n.sub));
      if (n.quote?.quote) kids.push(h('blockquote', { className: 'net-panel__quote' }, `“${n.quote.quote}”`, n.quote.section ? h('span', {}, ` — ${n.quote.section}`) : null));
      if (n.kind === 'metric') {
        const vals = map.papers.map(p => [p, compareCell(p, n.col)]).filter(([, c]) => c.value !== undefined);
        kids.push(h('ul', { className: 'net-panel__values' }, ...vals.map(([p, c]) => h('li', {}, h('span', {}, paperLabel(p)), h('b', {}, `${c.text}${c.text.includes(' ') ? '' : ` ${n.col.unit}`}`)))));
      }
      if (n.papers?.length) kids.push(h('div', { className: 'net-panel__kicker' }, n.papers.length > 1 ? `Linked papers · ${n.papers.length}` : 'From'),
        h('div', { className: 'finding-item__papers' }, ...n.papers.map(chip)));
    }
    kids.push(h('button', { className: 'link-btn net-panel__back', type: 'button', onClick: () => select(null) }, 'Clear selection'));
    panel.replaceChildren(...kids);
  }
  function select(n) { selected = n; paint(); showPanel(n); }

  // Filters
  const filters = h('div', { className: 'net-filters', role: 'group', 'aria-label': 'Show' },
    ...Object.keys(KINDS).filter(k => counts[k]).map(k => {
      const b = h('button', {
        className: `net-filter net-filter--${k} active`, type: 'button', 'aria-pressed': 'true',
        onClick: () => {
          if (k === 'paper') return;
          if (hidden.has(k)) hidden.delete(k); else hidden.add(k);
          b.classList.toggle('active', !hidden.has(k)); b.setAttribute('aria-pressed', String(!hidden.has(k)));
          paint();
        },
      }, h('span', { className: 'net-filter__swatch', 'aria-hidden': 'true' }), `${KINDS[k].label} `, h('b', {}, String(counts[k])));
      if (k === 'paper') b.disabled = true;
      return b;
    }));

  // Zoom & pan
  const zoom = (k, px = 0.5, py = 0.5) => {
    const nw = Math.max(home.w * 0.25, Math.min(home.w * 3, vb.w * k));
    const nh = nw * (vb.h / vb.w);
    vb = { x: vb.x + (vb.w - nw) * px, y: vb.y + (vb.h - nh) * py, w: nw, h: nh };
    applyView();
  };
  const controls = h('div', { className: 'graph-controls' },
    h('button', { className: 'map-control-btn', type: 'button', 'aria-label': 'Zoom in', onClick: () => zoom(0.8) }, icon('add')),
    h('button', { className: 'map-control-btn', type: 'button', 'aria-label': 'Zoom out', onClick: () => zoom(1.25) }, icon('remove')),
    h('button', { className: 'map-control-btn', type: 'button', 'aria-label': 'Fit to view', onClick: () => { vb = { ...home }; applyView(); } }, icon('fit_screen')));
  const box = h('div', { className: 'net-box' }, svg, controls);

  const toSvg = e => {
    const r = svg.getBoundingClientRect();
    const scale = Math.max(vb.w / r.width, vb.h / r.height);
    const ox = (r.width * scale - vb.w) / 2, oy = (r.height * scale - vb.h) / 2;
    return [vb.x - ox + (e.clientX - r.left) * scale, vb.y - oy + (e.clientY - r.top) * scale, scale];
  };
  let drag = null;
  svg.addEventListener('pointerdown', e => {
    const g = e.target.closest('.net__node');
    const node = g ? nodeEls.find(x => x.g === g)?.n : null;
    const [x, y, scale] = toSvg(e);
    drag = { node, x, y, sx: e.clientX, sy: e.clientY, vb: { ...vb }, scale, moved: false };
  });
  svg.addEventListener('pointermove', e => {
    if (!drag) {
      const g = e.target.closest('.net__node');
      const n = g ? nodeEls.find(x => x.g === g)?.n : null;
      if (n !== hover) { hover = n; paint(); }
      return;
    }
    const dx = e.clientX - drag.sx, dy = e.clientY - drag.sy;
    if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 4) return;
    if (!drag.moved) { drag.moved = true; svg.setPointerCapture(e.pointerId); svg.classList.add('is-dragging'); }
    if (drag.node) {
      const [x, y] = toSvg(e);
      drag.node.x = x; drag.node.y = y;
      place();
    } else {
      vb = { ...drag.vb, x: drag.vb.x - dx * drag.scale, y: drag.vb.y - dy * drag.scale };
      applyView();
    }
  });
  const end = e => {
    if (drag && !drag.moved) select(drag.node && drag.node === selected ? null : drag.node);
    drag = null; svg.classList.remove('is-dragging');
  };
  svg.addEventListener('pointerup', end);
  svg.addEventListener('pointercancel', () => { drag = null; svg.classList.remove('is-dragging'); });
  svg.addEventListener('pointerleave', () => { if (!drag && hover) { hover = null; paint(); } });
  svg.addEventListener('keydown', e => {
    const g = e.target.closest?.('.net__node');
    if (g && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); select(nodeEls.find(x => x.g === g)?.n || null); }
  });
  svg.addEventListener('wheel', e => {
    e.preventDefault();
    const r = svg.getBoundingClientRect();
    zoom(e.deltaY < 0 ? 0.88 : 1.14, (e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height);
  }, { passive: false });

  stage.append(filters, h('div', { className: 'net-layout' }, box, panel));
  applyView();
  place();
  paint();
  showPanel(null);
  if (!prefersReducedMotion()) {
    svg.classList.add('is-entering');
    requestAnimationFrame(() => requestAnimationFrame(() => svg.classList.remove('is-entering')));
  }
  return null;
}

// ════════════════════════════════════════════════════════
// Timeline: year × citations, bubble size = what was extracted
// ════════════════════════════════════════════════════════
function timelineView(stage, { map, onOpenPaper }) {
  const papers = map.papers.filter(p => p.year > 0);
  if (!papers.length) { stage.appendChild(empty('timeline', 'No publication years', 'None of these papers has a known year.')); return null; }
  const W = 1000, H = 480, L = 70, R = 40, T = 64, B = 60;
  const years = papers.map(p => p.year);
  let y0 = Math.min(...years), y1 = Math.max(...years);
  if (y1 - y0 < 4) { const mid = (y0 + y1) / 2; y0 = Math.floor(mid - 2); y1 = Math.ceil(mid + 2); }
  const maxC = Math.max(1, ...papers.map(p => p.citation_count || 0));
  const top = 10 ** Math.ceil(Math.log10(maxC + 1));
  const logMax = Math.log10(top + 1);
  const X = y => L + ((y - y0) / (y1 - y0)) * (W - L - R);
  const Y = c => H - B - (Math.log10((c || 0) + 1) / (logMax || 1)) * (H - T - B);

  const svg = s('svg', { class: 'tl', viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': 'Papers by year and citations' });
  // Grid
  for (let y = y0; y <= y1; y++) {
    svg.appendChild(s('line', { x1: X(y), y1: T, x2: X(y), y2: H - B, class: 'tl__grid' }));
    svg.appendChild(s('text', { x: X(y), y: H - B + 22, class: 'tl__tick', 'text-anchor': 'middle' }, String(y)));
  }
  [0, 1, 10, 100, 1000, 10000, 100000].filter(c => c <= top).forEach(c => {
    svg.appendChild(s('line', { x1: L, y1: Y(c), x2: W - R, y2: Y(c), class: 'tl__grid tl__grid--h' }));
    svg.appendChild(s('text', { x: L - 10, y: Y(c) + 4, class: 'tl__tick', 'text-anchor': 'end' }, c >= 1000 ? `${c / 1000}k` : String(c)));
  });
  svg.appendChild(s('text', { x: 16, y: 24, class: 'tl__axis' }, 'CITATIONS (LOG SCALE)'));
  svg.appendChild(s('text', { x: W - R, y: H - 12, class: 'tl__axis', 'text-anchor': 'end' }, 'YEAR PUBLISHED'));

  // Spread papers that share a year
  const perYear = new Map();
  const tip = h('div', { className: 'viz-tip', hidden: true });
  papers.sort((a, b) => (b.citation_count || 0) - (a.citation_count || 0)).forEach(p => {
    const k = perYear.get(p.year) || 0; perYear.set(p.year, k + 1);
    const e = p.extraction || {};
    const items = (e.findings?.length || 0) + (e.metrics?.length || 0) + (e.limitations?.length || 0);
    const r = 7 + Math.sqrt(items) * 4;
    const x = X(p.year) + (k % 2 ? 1 : -1) * Math.ceil(k / 2) * 16;
    const y = Y(p.citation_count);
    const g = s('g', { class: `tl__dot${p.source === 'upload' ? ' is-upload' : ''}${p.has_full_text ? ' is-full' : ''}`, transform: `translate(${x} ${y})`, tabindex: '0', role: 'button', 'aria-label': `${p.title}, ${p.year}` });
    g.append(s('circle', { r, class: 'tl__bubble' }), s('text', { y: -r - 6, 'text-anchor': 'middle', class: 'tl__label' }, paperLabel(p)));
    g.addEventListener('click', () => onOpenPaper?.(p.id));
    g.addEventListener('pointerenter', ev => {
      tip.replaceChildren(h('b', {}, p.title), h('span', {}, [p.year, p.venue || null, p.source === 'upload' ? 'Your PDF' : citationText(p.citation_count), `${items} items extracted`, p.has_full_text ? 'full text' : 'abstract only'].filter(Boolean).join(' · ')));
      tip.hidden = false;
      const rr = wrap.getBoundingClientRect();
      tip.style.left = `${Math.min(ev.clientX - rr.left + 14, rr.width - 280)}px`;
      tip.style.top = `${ev.clientY - rr.top + 14}px`;
    });
    g.addEventListener('pointerleave', () => { tip.hidden = true; });
    svg.appendChild(g);
  });

  const wrap = h('div', { className: 'viz-box' }, svg, tip);
  stage.append(wrap, h('div', { className: 'viz-legend' },
    h('span', {}, h('i', { className: 'swatch swatch--new' }), 'read in full'),
    h('span', {}, h('i', { className: 'swatch swatch--abs' }), 'abstract only'),
    h('span', {}, h('i', { className: 'swatch swatch--upload' }), 'your PDF'),
    h('span', {}, 'Bubble size = findings, values and limitations extracted')));
  return null;
}

// ════════════════════════════════════════════════════════
// Values: each quantity as a dot plot across papers
// ════════════════════════════════════════════════════════
function valuesView(stage, { map, onOpenPaper }) {
  const papers = map.papers.filter(p => p.extraction);
  const cols = buildCompareColumns(papers).filter(c => c.id.startsWith('metric:'));
  const rows = cols.map(c => ({
    col: c,
    pts: papers.map(p => ({ p, cell: compareCell(p, c) })).filter(x => typeof x.cell.value === 'number' && Number.isFinite(x.cell.value)),
  })).filter(r => r.pts.length).sort((a, b) => b.pts.length - a.pts.length).slice(0, 10);
  if (!rows.length) { stage.appendChild(empty('straighten', 'No numeric values', 'None of these papers reported a measured value that could be extracted with a quote.')); return; }

  const list = h('div', { className: 'vals' });
  rows.forEach(({ col, pts }) => {
    const vals = pts.map(x => x.cell.value);
    const lo = Math.min(...vals), hi = Math.max(...vals);
    const log = lo > 0 && hi / lo > 20;
    const f = v => (log ? Math.log10(v) : v);
    const a = f(lo), b = f(hi);
    const pos = v => (a === b ? 50 : 6 + ((f(v) - a) / (b - a)) * 88);
    const track = h('div', { className: 'vals__track' },
      h('span', { className: 'vals__rail' }),
      ...pts.map(({ p, cell }) => h('button', {
        className: `vals__dot${p.source === 'upload' ? ' is-upload' : ''}`, type: 'button', style: { left: `${pos(cell.value)}%` },
        title: `${paperLabel(p)}: ${cell.text} ${col.unit}${cell.detail ? ` · ${cell.detail}` : ''}${cell.evidence?.quote ? `\n“${cell.evidence.quote}”` : ''}`,
        onClick: () => onOpenPaper?.(p.id),
      }, h('span', { className: 'vals__dot-label' }, paperLabel(p)))));
    list.appendChild(h('div', { className: 'vals__row' },
      h('div', { className: 'vals__name' }, h('span', {}, col.title), h('span', { className: 'vals__unit' }, [col.unit, log ? 'log scale' : null, `${pts.length} paper${pts.length === 1 ? '' : 's'}`].filter(Boolean).join(' · '))),
      h('div', { className: 'vals__plot' },
        track,
        h('div', { className: 'vals__range' }, h('span', {}, formatValue(lo)), a === b ? null : h('span', {}, formatValue(hi))))));
  });
  stage.append(list, h('p', { className: 'table-note' }, 'Hover a dot for the conditions and the sentence it came from; click to open the paper. Values are as reported — not ranked.'));
}
