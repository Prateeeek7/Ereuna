/**
 * Hero radar: a slow sweep over items Ereuna finds for a topic. Hover (or tap)
 * a blip to see what it is and the sentence it comes from.
 *
 * Every quote here is verbatim from the named paper.
 */
import { h, prefersReducedMotion } from '../utils.js';

const BLIPS = [
  {
    kind: 'Paper', title: 'Attention Is All You Need', meta: 'Vaswani et al. · NeurIPS 2017',
    quote: 'We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely.',
    where: 'Abstract', angle: 0.85, dist: 0.42, size: 6.5, links: [1, 2, 4, 6],
  },
  {
    kind: 'Stated gap', title: 'Attention over very large inputs', meta: 'Vaswani et al. · NeurIPS 2017',
    quote: 'to investigate local, restricted attention mechanisms to efficiently handle large inputs and outputs such as images, audio and video.',
    where: '§7 Conclusion', angle: 2.3, dist: 0.74, size: 5, links: [0, 3], gap: true,
  },
  {
    kind: 'Finding', title: 'Constant path length', meta: 'Vaswani et al. · NeurIPS 2017',
    quote: 'a self-attention layer connects all positions with a constant number of sequentially executed operations',
    where: '§4 Why Self-Attention', angle: 4.05, dist: 0.36, size: 5, links: [0],
  },
  {
    kind: 'Paper', title: 'FlashAttention', meta: 'Dao et al. · NeurIPS 2022',
    quote: 'We propose FlashAttention, an IO-aware exact attention algorithm that uses tiling to reduce the number of memory reads/writes between GPU high bandwidth memory (HBM) and GPU on-chip SRAM.',
    where: 'Abstract', angle: 3.15, dist: 0.82, size: 5.5, links: [1],
  },
  {
    kind: 'Paper', title: 'Language Models are Few-Shot Learners', meta: 'Brown et al. · NeurIPS 2020',
    quote: 'Here we show that scaling up language models greatly improves task-agnostic, few-shot performance',
    where: 'Abstract', angle: 5.4, dist: 0.66, size: 5, links: [0, 6],
  },
  {
    kind: 'Paper', title: 'RoBERTa', meta: 'Liu et al. · arXiv 2019',
    quote: 'We find that BERT was significantly undertrained, and can match or exceed the performance of every model published after it.',
    where: 'Abstract', angle: 1.6, dist: 0.8, size: 4.5, links: [6],
  },
  {
    kind: 'Paper', title: 'BERT', meta: 'Devlin et al. · NAACL 2019',
    quote: 'BERT is designed to pre-train deep bidirectional representations from unlabeled text by jointly conditioning on both left and right context in all layers.',
    where: 'Abstract', angle: 0.2, dist: 0.66, size: 5.5, links: [0],
  },
];

function cssVar(name, fallback) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

export function mountHeroRadar(wrapper) {
  const canvas = h('canvas', { className: 'radar-canvas', 'aria-hidden': 'true' });
  const tip = h('div', { className: 'radar-node-tooltip hidden', role: 'status' });
  const centre = h('div', { className: 'radar-centre-label' }, 'YOUR TOPIC');
  wrapper.append(canvas, tip, centre);

  // Screen-reader and keyboard access to the same content
  const list = h('ul', { className: 'sr-only' });
  BLIPS.forEach(b => list.appendChild(h('li', {}, `${b.kind}: ${b.title} (${b.meta}). “${b.quote}” — ${b.where}`)));
  wrapper.appendChild(list);

  const ctx = canvas.getContext('2d');
  const blips = BLIPS.map(b => ({ ...b }));
  const still = prefersReducedMotion();
  let size = 480, dpr = 1, sweep = 0.4, raf = 0, hovered = null, pinned = null, visible = true;

  function resize() {
    size = Math.min(wrapper.clientWidth || 480, 520);
    dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    canvas.style.width = canvas.style.height = `${size}px`;
    draw();
  }

  const pos = b => {
    const r = size * 0.44 * b.dist;
    return [size / 2 + Math.cos(b.angle) * r, size / 2 + Math.sin(b.angle) * r];
  };

  function blipAt(x, y, slop = 20) {
    let best = null, bestD = slop;
    blips.forEach(b => {
      const [bx, by] = pos(b);
      const d = Math.hypot(x - bx, y - by);
      if (d < bestD) { best = b; bestD = d; }
    });
    return best;
  }

  function showTip(b) {
    if (!b) { tip.classList.add('hidden'); return; }
    tip.replaceChildren(
      h('div', { className: `radar-tooltip__badge ${b.gap ? 'radar-tooltip__badge--gap' : ''}` }, b.kind),
      h('div', { className: 'radar-tooltip__title' }, b.title),
      h('div', { className: 'radar-tooltip__meta' }, b.meta),
      h('blockquote', { className: 'radar-tooltip__excerpt' }, `“${b.quote}”`),
      h('div', { className: 'radar-tooltip__where' }, b.where),
    );
    const [x, y] = pos(b);
    const w = Math.min(290, size - 16);
    let left = x + 18;
    if (left + w > size) left = Math.max(8, x - w - 18);
    let top = Math.min(Math.max(8, y - 40), size - 190);
    tip.style.width = `${w}px`;
    tip.style.left = `${left}px`;
    tip.style.top = `${top}px`;
    tip.classList.remove('hidden');
  }

  function draw() {
    const accent = cssVar('--accent', '#C94B1F');
    const ink = cssVar('--ink', '#171614');
    const dark = document.documentElement.dataset.theme === 'dark';
    const cx = size / 2, cy = size / 2, R = size * 0.44;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, size, size);

    const glow = ctx.createRadialGradient(cx, cy, 0, cx, cy, R);
    glow.addColorStop(0, dark ? 'rgba(201,75,31,0.07)' : 'rgba(201,75,31,0.05)');
    glow.addColorStop(1, 'rgba(201,75,31,0)');
    ctx.fillStyle = glow;
    ctx.beginPath(); ctx.arc(cx, cy, R, 0, Math.PI * 2); ctx.fill();

    // Rings and axes
    [0.25, 0.5, 0.75, 1].forEach((r, i) => {
      ctx.strokeStyle = `rgba(201,75,31,${i === 3 ? 0.34 : 0.15})`;
      ctx.lineWidth = i === 3 ? 1.2 : 0.8;
      ctx.setLineDash(i === 1 ? [3, 4] : []);
      ctx.beginPath(); ctx.arc(cx, cy, R * r, 0, Math.PI * 2); ctx.stroke();
    });
    ctx.setLineDash([]);
    ctx.strokeStyle = 'rgba(201,75,31,0.14)';
    ctx.beginPath();
    ctx.moveTo(cx - R, cy); ctx.lineTo(cx + R, cy);
    ctx.moveTo(cx, cy - R); ctx.lineTo(cx, cy + R);
    ctx.stroke();
    // Bearing ticks
    for (let a = 0; a < 72; a++) {
      const t = (a / 72) * Math.PI * 2, long = a % 6 === 0;
      ctx.strokeStyle = `rgba(201,75,31,${long ? 0.35 : 0.16})`;
      ctx.beginPath();
      ctx.moveTo(cx + Math.cos(t) * R, cy + Math.sin(t) * R);
      ctx.lineTo(cx + Math.cos(t) * (R + (long ? 7 : 4)), cy + Math.sin(t) * (R + (long ? 7 : 4)));
      ctx.stroke();
    }

    // Sweep
    if (!still) {
      const cone = ctx.createConicGradient(sweep - 0.7, cx, cy);
      cone.addColorStop(0, 'rgba(201,75,31,0)');
      cone.addColorStop(0.7 / (Math.PI * 2), dark ? 'rgba(201,75,31,0.2)' : 'rgba(201,75,31,0.14)');
      cone.addColorStop(0.7 / (Math.PI * 2) + 0.001, 'rgba(201,75,31,0)');
      ctx.fillStyle = cone;
      ctx.beginPath(); ctx.arc(cx, cy, R, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = accent;
      ctx.lineWidth = 1.4;
      ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + Math.cos(sweep) * R, cy + Math.sin(sweep) * R); ctx.stroke();
    }

    const active = pinned || hovered;
    // Links
    blips.forEach((b, i) => b.links.forEach(j => {
      if (j < i) return;
      const t = blips[j];
      const hot = active && (active === b || active === t);
      const [x1, y1] = pos(b), [x2, y2] = pos(t);
      ctx.strokeStyle = hot ? accent : 'rgba(201,75,31,0.18)';
      ctx.lineWidth = hot ? 1.6 : 0.8;
      ctx.setLineDash(b.gap || t.gap ? [3, 3] : []);
      ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
    }));
    ctx.setLineDash([]);

    // Blips
    blips.forEach(b => {
      const [x, y] = pos(b);
      const behind = ((sweep - b.angle) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
      const lit = still ? 0.4 : Math.max(0, 1 - behind / 2.2);
      if (lit > 0) {
        ctx.fillStyle = `rgba(226,101,50,${0.28 * lit})`;
        ctx.beginPath(); ctx.arc(x, y, b.size * (1.6 + lit), 0, Math.PI * 2); ctx.fill();
      }
      const on = b === active;
      ctx.fillStyle = on ? ink : b.gap ? 'rgba(0,0,0,0)' : accent;
      ctx.strokeStyle = accent;
      ctx.lineWidth = 1.4;
      ctx.beginPath(); ctx.arc(x, y, on ? b.size * 1.35 : b.size, 0, Math.PI * 2);
      ctx.fill(); ctx.stroke();
      if (on) {
        ctx.strokeStyle = accent;
        ctx.beginPath(); ctx.arc(x, y, b.size * 2.6, 0, Math.PI * 2); ctx.stroke();
      }
    });

    ctx.fillStyle = accent;
    ctx.beginPath(); ctx.arc(cx, cy, 3.5, 0, Math.PI * 2); ctx.fill();
  }

  function frame() {
    sweep = (sweep + 0.009) % (Math.PI * 2);
    draw();
    raf = visible ? requestAnimationFrame(frame) : 0;
  }

  const local = e => {
    const r = canvas.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
  };
  const onMove = e => {
    if (e.pointerType === 'touch') return;
    const b = blipAt(...local(e));
    if (b !== hovered) {
      hovered = b;
      canvas.style.cursor = b ? 'pointer' : 'default';
      if (!pinned) showTip(b);
      if (still) draw();
    }
  };
  const onLeave = () => { hovered = null; if (!pinned) showTip(null); if (still) draw(); };
  const onClick = e => {
    const b = blipAt(...local(e), 28);
    pinned = b && b !== pinned ? b : null;
    showTip(pinned || hovered);
    if (still) draw();
  };

  canvas.addEventListener('pointermove', onMove);
  canvas.addEventListener('pointerleave', onLeave);
  canvas.addEventListener('click', onClick);

  const ro = new ResizeObserver(resize);
  ro.observe(wrapper);
  const io = new IntersectionObserver(([e]) => {
    visible = e.isIntersecting;
    if (visible && !raf && !still) raf = requestAnimationFrame(frame);
  });
  io.observe(wrapper);
  const themeObserver = new MutationObserver(() => draw());
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

  resize();
  if (!still) raf = requestAnimationFrame(frame);

  return () => {
    cancelAnimationFrame(raf);
    visible = false;
    ro.disconnect();
    io.disconnect();
    themeObserver.disconnect();
  };
}
