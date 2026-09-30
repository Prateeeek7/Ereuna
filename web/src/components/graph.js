/**
 * Citation graph on a canvas, using the server's precomputed layout.
 * Node colour runs from pale (older) to accent (newer); size follows citations.
 * Drag or one-finger pan, wheel or pinch zoom, click a paper to open it.
 */
import { h, icon, citations } from '../utils.js';

const css = (name, fallback) => getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;

export function renderGraph(container, graphData, papers = [], { onOpen } = {}) {
  const { nodes, edges } = graphData;
  const papersById = new Map(papers.map(p => [p.id, p]));
  const byId = new Map(nodes.map(n => [n.paper_id, n]));

  const canvas = h('canvas', { className: 'graph-canvas', 'aria-label': `Citation graph with ${nodes.length} papers`, role: 'img' });
  const tooltip = h('div', { className: 'graph-tooltip', hidden: true });
  const controls = h('div', { className: 'graph-controls' },
    h('button', { className: 'map-control-btn', type: 'button', 'aria-label': 'Zoom in', onClick: () => zoomAt(1.25) }, icon('add')),
    h('button', { className: 'map-control-btn', type: 'button', 'aria-label': 'Zoom out', onClick: () => zoomAt(0.8) }, icon('remove')),
    h('button', { className: 'map-control-btn', type: 'button', 'aria-label': 'Fit to view', onClick: () => { touched = false; fit(); draw(); } }, icon('fit_screen')));
  const box = h('div', { className: 'graph-container' }, canvas, tooltip, controls);

  const inMap = nodes.filter(n => n.in_map).length;
  const legend = h('div', { className: 'graph-legend' },
    h('span', {}, `${inMap} papers in this map · ${nodes.length - inMap} cited by them · ${edges.length} citation link${edges.length === 1 ? '' : 's'}`),
    h('span', { className: 'graph-legend__keys' },
      h('span', { className: 'graph-legend__key' }, h('i', { className: 'swatch swatch--old' }), 'older'),
      h('span', { className: 'graph-legend__key' }, h('i', { className: 'swatch swatch--new' }), 'newer'),
      h('span', { className: 'graph-legend__key' }, h('i', { className: 'swatch swatch--ref' }), 'cited, not in map')));

  // Accessible list of the same papers
  const list = h('details', { className: 'graph-list' }, h('summary', {}, 'List the papers in the graph'),
    h('ul', {}, ...nodes.filter(n => n.in_map).sort((a, b) => b.citation_count - a.citation_count).map(n => {
      const p = papersById.get(n.paper_id);
      return h('li', {}, h('button', { className: 'link-btn', type: 'button', onClick: () => onOpen?.(n.paper_id) }, p?.title || n.label),
        ` · ${n.year || 'n.d.'} · ${citations(n.citation_count)}`);
    })));

  container.append(box, legend, list);

  const xs = nodes.map(n => n.x), ys = nodes.map(n => n.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const years = nodes.filter(n => n.year > 0).map(n => n.year);
  const y0 = years.length ? Math.min(...years) : 2000, y1 = years.length ? Math.max(...years) : 2025;

  let scale = 1, ox = 0, oy = 0, w = 0, hgt = 0, hovered = null;
  const pointers = new Map();
  let lastPinch = null, dragMoved = false, touched = false;

  function fit() {
    const pad = 48;
    const sx = (w - pad * 2) / Math.max(1, maxX - minX);
    const sy = (hgt - pad * 2) / Math.max(1, maxY - minY);
    scale = Math.min(sx, sy);
    ox = (w - (maxX - minX) * scale) / 2 - minX * scale;
    oy = (hgt - (maxY - minY) * scale) / 2 - minY * scale;
  }
  const toScreen = n => [n.x * scale + ox, n.y * scale + oy];
  const radius = n => Math.max(3.5, Math.min(16, 3 + Math.sqrt(n.citation_count || 0) * 0.35)) * (n.in_map ? 1 : 0.7);

  function colour(n) {
    if (!n.in_map) return null;
    const t = y1 > y0 && n.year ? (n.year - y0) / (y1 - y0) : 1;
    // pale sand → accent
    const a = [214, 190, 170], b = [201, 75, 31];
    return `rgb(${a.map((v, i) => Math.round(v + (b[i] - v) * t)).join(',')})`;
  }

  function resize() {
    const r = box.getBoundingClientRect();
    w = r.width; hgt = r.height;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = w * dpr; canvas.height = hgt * dpr;
    canvas.style.width = `${w}px`; canvas.style.height = `${hgt}px`;
    if (!touched) fit();
    draw();
  }

  function draw() {
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, hgt);
    const ink = css('--ink', '#171614'), ink3 = css('--ink3', '#8C8880'), accent = css('--accent', '#C94B1F'), surface = css('--surface', '#fff');

    const hot = new Set();
    if (hovered) {
      hot.add(hovered.paper_id);
      edges.forEach(e => { if (e.source === hovered.paper_id) hot.add(e.target); if (e.target === hovered.paper_id) hot.add(e.source); });
    }

    edges.forEach(e => {
      const a = byId.get(e.source), b = byId.get(e.target);
      if (!a || !b) return;
      const on = hovered && (e.source === hovered.paper_id || e.target === hovered.paper_id);
      const [x1, y1_] = toScreen(a), [x2, y2] = toScreen(b);
      ctx.strokeStyle = on ? accent : ink3;
      ctx.globalAlpha = on ? 0.9 : hovered ? 0.08 : 0.22;
      ctx.lineWidth = on ? 1.4 : 0.8;
      ctx.beginPath(); ctx.moveTo(x1, y1_); ctx.lineTo(x2, y2); ctx.stroke();
    });
    ctx.globalAlpha = 1;

    const ordered = [...nodes].sort((a, b) => Number(a.in_map) - Number(b.in_map));
    ordered.forEach(n => {
      const [x, y] = toScreen(n);
      const r = radius(n);
      ctx.globalAlpha = hovered && !hot.has(n.paper_id) ? 0.25 : 1;
      ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2);
      const fill = colour(n);
      ctx.fillStyle = fill || surface;
      ctx.fill();
      ctx.strokeStyle = n === hovered ? ink : fill ? accent : ink3;
      ctx.lineWidth = n === hovered ? 2 : fill ? 0.8 : 1;
      ctx.stroke();
      if (n.in_map && (n === hovered || !hovered || hot.has(n.paper_id))) {
        ctx.fillStyle = ink;
        ctx.font = `500 11px "IBM Plex Sans", sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText(n.label || '', x, y + r + 3);
      }
    });
    ctx.globalAlpha = 1;
  }

  function nodeAt(sx, sy) {
    for (let i = nodes.length - 1; i >= 0; i--) {
      const n = nodes[i];
      const [x, y] = toScreen(n);
      const r = radius(n) + 5;
      if ((sx - x) ** 2 + (sy - y) ** 2 <= r * r) return n;
    }
    return null;
  }

  function zoomAt(k, cx = w / 2, cy = hgt / 2) {
    touched = true;
    const ns = Math.min(40, Math.max(0.05, scale * k));
    k = ns / scale;
    ox = cx - (cx - ox) * k;
    oy = cy - (cy - oy) * k;
    scale = ns;
    draw();
  }

  function showTip(n, sx, sy) {
    if (!n) { tooltip.hidden = true; return; }
    const p = papersById.get(n.paper_id);
    tooltip.replaceChildren(
      h('div', { className: 'graph-tooltip__title' }, p?.title || n.label),
      h('div', { className: 'graph-tooltip__meta' }, [n.year || null, citations(n.citation_count), n.in_map ? 'click to open' : 'cited, not in this map'].filter(Boolean).join(' · ')));
    tooltip.hidden = false;
    const tw = tooltip.offsetWidth;
    tooltip.style.left = `${Math.min(sx + 14, w - tw - 8)}px`;
    tooltip.style.top = `${Math.max(8, sy - 12)}px`;
  }

  const local = e => { const r = canvas.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };

  canvas.addEventListener('pointerdown', e => {
    canvas.setPointerCapture(e.pointerId);
    pointers.set(e.pointerId, local(e));
    dragMoved = false;
  });
  canvas.addEventListener('pointermove', e => {
    const p = local(e);
    if (pointers.has(e.pointerId)) {
      const prev = pointers.get(e.pointerId);
      pointers.set(e.pointerId, p);
      if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        const dist = Math.hypot(a[0] - b[0], a[1] - b[1]);
        if (lastPinch) zoomAt(dist / lastPinch, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
        lastPinch = dist;
        dragMoved = true;
        return;
      }
      const dx = p[0] - prev[0], dy = p[1] - prev[1];
      if (Math.abs(dx) + Math.abs(dy) > 1) dragMoved = true;
      ox += dx; oy += dy;
      touched = true;
      showTip(null);
      draw();
      return;
    }
    const n = nodeAt(...p);
    if (n !== hovered) { hovered = n; draw(); }
    canvas.style.cursor = n?.in_map ? 'pointer' : 'grab';
    showTip(n, ...p);
  });
  const release = e => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) lastPinch = null;
  };
  canvas.addEventListener('pointerup', e => {
    const wasDrag = dragMoved;
    release(e);
    if (wasDrag) return;
    const n = nodeAt(...local(e));
    if (e.pointerType === 'touch' && n && n !== hovered) { hovered = n; draw(); showTip(n, ...local(e)); return; }
    if (n?.in_map) onOpen?.(n.paper_id);
  });
  canvas.addEventListener('pointercancel', release);
  canvas.addEventListener('pointerleave', () => { if (!pointers.size) { hovered = null; showTip(null); draw(); } });
  canvas.addEventListener('wheel', e => {
    e.preventDefault();
    zoomAt(e.deltaY < 0 ? 1.12 : 1 / 1.12, ...local(e));
  }, { passive: false });

  const ro = new ResizeObserver(resize);
  ro.observe(box);
  const mo = new MutationObserver(draw);
  mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

  return () => { ro.disconnect(); mo.disconnect(); };
}
