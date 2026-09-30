/**
 * Landing-page demo of a research map (SVG): pan by dragging, zoom with the
 * buttons, filter by kind, click a node to inspect it.
 *
 * The papers and quotes are real; quotes are verbatim from the named sections.
 */
import { h, icon } from '../utils.js';

const NODES = [
  {
    id: 0, type: 'paper', label: 'Vaswani ’17', title: 'Attention Is All You Need', year: 2017,
    meta: 'Vaswani, Shazeer, Parmar et al. · NeurIPS 2017', x: 0, y: 0, r: 24,
    quote: 'We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely.',
    where: 'Abstract',
  },
  {
    id: 1, type: 'paper', label: 'Devlin ’19', title: 'BERT: Pre-training of Deep Bidirectional Transformers', year: 2019,
    meta: 'Devlin, Chang, Lee, Toutanova · NAACL 2019', x: -170, y: -80, r: 19,
    quote: 'BERT is designed to pre-train deep bidirectional representations from unlabeled text by jointly conditioning on both left and right context in all layers.',
    where: 'Abstract',
  },
  {
    id: 2, type: 'paper', label: 'Dao ’22', title: 'FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness', year: 2022,
    meta: 'Dao, Fu, Ermon, Rudra, Ré · NeurIPS 2022', x: 185, y: -95, r: 18,
    quote: 'We propose FlashAttention, an IO-aware exact attention algorithm that uses tiling to reduce the number of memory reads/writes between GPU high bandwidth memory (HBM) and GPU on-chip SRAM.',
    where: 'Abstract',
  },
  {
    id: 3, type: 'paper', label: 'Liu ’19', title: 'RoBERTa: A Robustly Optimized BERT Pretraining Approach', year: 2019,
    meta: 'Liu, Ott, Goyal et al. · arXiv 2019', x: -275, y: 40, r: 15,
    quote: 'We find that BERT was significantly undertrained, and can match or exceed the performance of every model published after it.',
    where: 'Abstract',
  },
  {
    id: 4, type: 'paper', label: 'Brown ’20', title: 'Language Models are Few-Shot Learners', year: 2020,
    meta: 'Brown, Mann, Ryder et al. · NeurIPS 2020', x: 150, y: 120, r: 20,
    quote: 'Here we show that scaling up language models greatly improves task-agnostic, few-shot performance, sometimes even reaching competitiveness with prior state-of-the-art fine-tuning approaches.',
    where: 'Abstract',
  },
  {
    id: 5, type: 'gap', label: 'Large inputs', title: 'Attention over images, audio and video', year: 2017,
    meta: 'Stated by Vaswani et al. as future work', x: 300, y: 10, r: 14,
    quote: 'to investigate local, restricted attention mechanisms to efficiently handle large inputs and outputs such as images, audio and video.',
    where: 'Vaswani ’17 · §7 Conclusion',
  },
  {
    id: 6, type: 'finding', label: 'Path length', title: 'Self-attention links any two positions in a constant number of steps', year: 2017,
    meta: 'Finding from Vaswani et al.', x: -70, y: 150, r: 14,
    quote: 'a self-attention layer connects all positions with a constant number of sequentially executed operations, whereas a recurrent layer requires O(n) sequential operations.',
    where: 'Vaswani ’17 · §4 Why Self-Attention',
  },
  {
    id: 7, type: 'gap', label: 'Kernel per variant', title: 'Each attention variant needs its own CUDA kernel', year: 2022,
    meta: 'Stated by Dao et al. as a limitation', x: 310, y: -175, r: 13,
    quote: 'Our current approach to building IO-aware implementations of attention requires writing a new CUDA kernel for each new attention implementation.',
    where: 'Dao ’22 · §5 Limitations',
  },
];

const EDGES = [
  [1, 0], [2, 0], [4, 0], [3, 1], [0, 6], [0, 5], [2, 5], [2, 7],
];

const KIND = { paper: 'Paper', gap: 'Author-stated gap', finding: 'Finding' };
const NS = 'http://www.w3.org/2000/svg';
const s = (tag, attrs = {}) => {
  const el = document.createElementNS(NS, tag);
  Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
  return el;
};

export function mountDemoMap(root, { onCta }) {
  let filter = 'all';
  let selected = NODES[0];
  let zoom = 1, panX = 0, panY = 0;

  const counts = { paper: 0, finding: 0, gap: 0 };
  NODES.forEach(n => counts[n.type]++);

  // Top bar
  const tabs = [
    ['all', 'Everything'],
    ['paper', `Papers (${counts.paper})`],
    ['finding', `Findings (${counts.finding})`],
    ['gap', `Gaps (${counts.gap})`],
  ].map(([id, text]) => h('button', {
    className: `map-demo__tab ${id === filter ? 'active' : ''}`, type: 'button', 'aria-pressed': String(id === filter),
    onClick: () => { filter = id; tabs.forEach(t => { const on = t.dataset.id === id; t.classList.toggle('active', on); t.setAttribute('aria-pressed', String(on)); }); render(); },
    'data-id': id,
  }, text));

  const zoomBy = k => { zoom = Math.min(1.8, Math.max(0.55, zoom * k)); applyView(); };
  const top = h('div', { className: 'map-demo__topbar' },
    h('div', { className: 'map-demo__query' }, icon('search'), h('span', { className: 'map-demo__query-text' }, 'attention mechanisms in transformers')),
    h('div', { className: 'map-demo__tabs', role: 'group', 'aria-label': 'Show' }, ...tabs),
    h('div', { className: 'map-demo__controls' },
      h('button', { className: 'map-control-btn', type: 'button', title: 'Zoom in', 'aria-label': 'Zoom in', onClick: () => zoomBy(1.15) }, icon('add')),
      h('button', { className: 'map-control-btn', type: 'button', title: 'Zoom out', 'aria-label': 'Zoom out', onClick: () => zoomBy(1 / 1.15) }, icon('remove')),
      h('button', { className: 'map-control-btn', type: 'button', title: 'Reset view', 'aria-label': 'Reset view', onClick: () => { zoom = 1; panX = panY = 0; applyView(); } }, icon('center_focus_weak')),
    ),
  );

  // Canvas
  const svg = s('svg', { class: 'map-demo__svg', viewBox: '-360 -230 720 460', role: 'group', 'aria-label': 'Example research map' });
  const defs = s('defs');
  const pattern = s('pattern', { id: 'demo-grid', width: 40, height: 40, patternUnits: 'userSpaceOnUse' });
  pattern.appendChild(s('path', { d: 'M40 0 L0 0 0 40', class: 'map-demo__grid' }));
  defs.appendChild(pattern);
  svg.appendChild(defs);
  svg.appendChild(s('rect', { x: -2000, y: -2000, width: 4000, height: 4000, fill: 'url(#demo-grid)' }));
  const world = s('g');
  svg.appendChild(world);
  const edgeLayer = s('g');
  const nodeLayer = s('g');
  world.append(edgeLayer, nodeLayer);

  const edgeEls = EDGES.map(([a, b]) => {
    const A = NODES[a], B = NODES[b];
    const line = s('line', { x1: A.x, y1: A.y, x2: B.x, y2: B.y, class: 'map-demo__edge' });
    if (A.type === 'gap' || B.type === 'gap') line.classList.add('map-demo__edge--gap');
    edgeLayer.appendChild(line);
    return { line, a: A, b: B };
  });

  const nodeEls = NODES.map(n => {
    const g = s('g', { class: `map-demo__node map-demo__node--${n.type}`, transform: `translate(${n.x} ${n.y})`, tabindex: '0', role: 'button', 'aria-label': `${KIND[n.type]}: ${n.title}` });
    g.appendChild(s('circle', { r: n.r + 7, class: 'map-demo__halo' }));
    g.appendChild(s('circle', { r: n.r, class: 'map-demo__dot' }));
    const inner = s('text', { class: 'map-demo__inner', 'text-anchor': 'middle', dy: '0.35em' });
    inner.textContent = n.type === 'paper' ? String(n.year) : n.type === 'gap' ? '?' : '“';
    const caption = s('text', { class: 'map-demo__caption', 'text-anchor': 'middle', y: n.r + 16 });
    caption.textContent = n.label;
    g.append(inner, caption);
    const pick = () => { selected = n; render(); };
    g.addEventListener('click', e => { if (!moved) pick(); e.stopPropagation(); });
    g.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });
    nodeLayer.appendChild(g);
    return { g, n };
  });

  const canvasWrap = h('div', { className: 'map-demo__canvas-wrap' }, svg,
    h('div', { className: 'map-demo__hint' }, 'Drag to pan · click a node'));
  const inspector = h('aside', { className: 'map-demo__inspector', 'aria-live': 'polite' });

  root.append(top, h('div', { className: 'map-demo__body' }, canvasWrap, inspector));

  // Pan
  let dragging = false, moved = false, sx = 0, sy = 0, startPan = [0, 0];
  const unitsPerPx = () => 720 / (svg.clientWidth || 720) / zoom;
  svg.addEventListener('pointerdown', e => {
    dragging = true; moved = false; sx = e.clientX; sy = e.clientY; startPan = [panX, panY];
  });
  svg.addEventListener('pointermove', e => {
    if (!dragging) return;
    const dx = e.clientX - sx, dy = e.clientY - sy;
    if (!moved && Math.abs(dx) + Math.abs(dy) > 4) {
      // Capture only once it's a drag, so a plain click still reaches the node.
      moved = true;
      svg.setPointerCapture(e.pointerId);
      svg.classList.add('dragging');
    }
    if (!moved) return;
    panX = startPan[0] + dx * unitsPerPx();
    panY = startPan[1] + dy * unitsPerPx();
    applyView();
  });
  const end = () => { dragging = false; svg.classList.remove('dragging'); setTimeout(() => { moved = false; }, 0); };
  svg.addEventListener('pointerup', end);
  svg.addEventListener('pointercancel', end);

  function applyView() {
    world.setAttribute('transform', `translate(${panX} ${panY}) scale(${zoom})`);
  }

  const shown = n => filter === 'all' || n.type === filter || (filter !== 'paper' && n.id === 0);

  function render() {
    nodeEls.forEach(({ g, n }) => {
      g.classList.toggle('is-dim', !shown(n));
      g.classList.toggle('is-selected', n === selected);
    });
    edgeEls.forEach(({ line, a, b }) => {
      line.classList.toggle('is-dim', !(shown(a) && shown(b)));
      line.classList.toggle('is-active', a === selected || b === selected);
    });

    const n = selected;
    const related = EDGES.filter(([a, b]) => a === n.id || b === n.id).map(([a, b]) => NODES[a === n.id ? b : a]);
    inspector.replaceChildren(...[
      h('div', { className: `map-inspector__badge map-inspector__badge--${n.type}` }, KIND[n.type]),
      h('h3', { className: 'map-inspector__title' }, n.title),
      h('div', { className: 'map-inspector__meta' }, n.meta),
      h('figure', { className: 'map-inspector__excerpt' },
        h('figcaption', { className: 'excerpt-label' }, `Source sentence · ${n.where}`),
        h('blockquote', { className: 'excerpt-quote' }, `“${n.quote}”`),
      ),
      related.length ? h('div', { className: 'map-inspector__related' },
        h('div', { className: 'excerpt-label' }, 'Connected to'),
        ...related.map(r => h('button', {
          className: 'map-inspector__link', type: 'button',
          onClick: () => { selected = r; render(); },
        }, h('span', { className: `map-inspector__pip map-inspector__pip--${r.type}` }), r.label)),
      ) : null,
      h('button', { className: 'btn-editorial-primary map-inspector__cta', type: 'button', onClick: onCta },
        'Map your own topic', icon('arrow_forward', 'btn-icon-right')),
    ].filter(Boolean));
  }

  applyView();
  render();
  return () => {};
}
