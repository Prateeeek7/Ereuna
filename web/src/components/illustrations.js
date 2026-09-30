/**
 * Landing-page illustrations, drawn as inline SVG.
 *
 * They use the page's color tokens through CSS classes (.ill-*), so they follow
 * light/dark mode without separate image files, stay sharp at any size, and
 * animate when scrolled into view (see .in-view rules in index.css).
 */

const f = n => Math.round(n * 10) / 10;

/** A paper page centred on (x, y): outline, text lines, optional highlighted line. */
function doc(x, y, { rot = 0, cls = '', lines = 6, mark = -1, w = 40, hgt = 52 } = {}) {
  const widths = [26, 21, 26, 17, 24, 13, 22];
  const left = -w / 2 + 7;
  let out = `<g class="ill-doc ${cls}" transform="translate(${f(x)} ${f(y)}) rotate(${rot})">`;
  out += `<rect class="ill-doc__page" x="${-w / 2}" y="${-hgt / 2}" width="${w}" height="${hgt}" rx="1.5"/>`;
  out += `<rect class="ill-doc__title" x="${left}" y="${-hgt / 2 + 7}" width="${w * 0.55}" height="3" rx="1"/>`;
  for (let i = 0; i < lines; i++) {
    const yy = -hgt / 2 + 16 + i * 5.2;
    out += `<rect class="${i === mark ? 'ill-doc__mark' : 'ill-doc__line'}" x="${left}" y="${f(yy)}" width="${widths[i % widths.length] * (w / 40)}" height="1.6" rx=".8"/>`;
  }
  return out + '</g>';
}

function label(x, y, text, { anchor = 'start', cls = '' } = {}) {
  return `<text class="ill-label ${cls}" x="${f(x)}" y="${f(y)}" text-anchor="${anchor}">${text}</text>`;
}

function svg(viewBox, title, body) {
  return `<svg class="ill" viewBox="${viewBox}" role="img" aria-label="${title}" xmlns="http://www.w3.org/2000/svg"><title>${title}</title>${body}</svg>`;
}

/** Scattered sources on the left; the same sources connected into a map on the right. */
export function problemIllustration() {
  let b = '';

  // Left: scattered, unconnected sources.
  const scattered = [
    [72, 112, -16], [162, 84, 11], [236, 140, -7], [96, 218, 21],
    [184, 206, -24], [64, 306, 8], [152, 300, -12], [238, 272, 15],
  ];
  // Links that stop short: the connection exists but nobody has drawn it.
  const broken = [[0, 1], [3, 4], [5, 6], [2, 7]];
  broken.forEach(([a, c], i) => {
    const [x1, y1] = scattered[a];
    const [x2, y2] = scattered[c];
    const mx = x1 + (x2 - x1) * 0.48;
    const my = y1 + (y2 - y1) * 0.48;
    b += `<line class="ill-edge ill-edge--broken" x1="${x1}" y1="${y1}" x2="${f(mx)}" y2="${f(my)}"/>`;
    b += `<circle class="ill-open-end" cx="${f(mx)}" cy="${f(my)}" r="2.2" style="--i:${i}"/>`;
  });
  scattered.forEach(([x, y, r], i) => {
    b += `<g class="ill-float" style="--i:${i}">${doc(x, y, { rot: r, lines: i % 2 ? 5 : 6 })}</g>`;
  });
  b += label(162, 44, 'PREPRINT', { anchor: 'middle' });
  b += `<line class="ill-leader" x1="162" y1="48" x2="162" y2="56"/>`;
  b += label(28, 176, 'PDF · P.9', {});
  b += label(254, 102, 'JOURNAL', {});
  b += label(236, 318, 'PROCEEDINGS', { anchor: 'middle' });

  // Centre: the radar that does the reading.
  b += `<line class="ill-divider" x1="300" y1="40" x2="300" y2="340"/>`;
  b += `<g class="ill-radar">
    <circle class="ill-radar__disc" cx="300" cy="190" r="30"/>
    <circle class="ill-radar__ring" cx="300" cy="190" r="20"/>
    <circle class="ill-radar__ring" cx="300" cy="190" r="10"/>
    <g class="ill-sweep"><path class="ill-sweep__wedge" d="M300 190 L330 190 A30 30 0 0 0 321.2 168.8 Z"/>
    <line class="ill-sweep__line" x1="300" y1="190" x2="330" y2="190"/></g>
    <circle class="ill-radar__dot" cx="300" cy="190" r="2.4"/>
  </g>`;

  // Right: the same sources, connected.
  const nodes = {
    A: [372, 122], B: [468, 84], C: [556, 142], D: [420, 222], E: [532, 238], F: [466, 318], G: [562, 320],
  };
  const edges = [['A', 'B'], ['A', 'D'], ['B', 'C'], ['B', 'D', 1], ['D', 'E', 1], ['C', 'E'], ['D', 'F'], ['E', 'F'], ['E', 'G', 1]];
  edges.forEach(([p, q, hot], i) => {
    const [x1, y1] = nodes[p];
    const [x2, y2] = nodes[q];
    const len = f(Math.hypot(x2 - x1, y2 - y1));
    b += `<line class="ill-edge ill-draw ${hot ? 'ill-edge--hot' : ''}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" style="--len:${len};--i:${i}"/>`;
  });
  Object.entries(nodes).forEach(([k, [x, y]]) => {
    const cls = k === 'G' ? 'ill-doc--gap' : k === 'D' ? 'ill-doc--hot' : '';
    b += doc(x, y, { cls, mark: k === 'D' ? 2 : -1, lines: 6 });
  });
  b += label(420, 264, 'FINDING', { anchor: 'middle', cls: 'ill-label--accent' });
  b += label(562, 362, 'OPEN GAP', { anchor: 'middle', cls: 'ill-label--accent' });
  b += label(468, 46, 'METHOD', { anchor: 'middle' });

  b += label(150, 382, 'BEFORE — SOURCES IN SILOS', { anchor: 'middle', cls: 'ill-caption' });
  b += label(466, 382, 'AFTER — ONE CONNECTED MAP', { anchor: 'middle', cls: 'ill-caption' });

  return svg('0 0 600 400', 'Scattered papers on one side are connected into a single map on the other', b);
}

/** A paper page with the quoted sentence highlighted, traced to the finding card built from it. */
export function evidenceIllustration() {
  let b = '';
  // The page
  b += `<rect class="ill-page" x="36" y="34" width="236" height="324" rx="2"/>`;
  b += label(56, 58, 'ARXIV:1706.03762', { cls: 'ill-label--tiny' });
  b += label(252, 58, '6', { anchor: 'end', cls: 'ill-label--tiny' });
  b += `<text class="ill-page__heading" x="56" y="90">4  Why Self-Attention</text>`;
  const lineW = [196, 188, 198, 170, 194, 190, 198, 182, 120, 0, 196, 186, 194, 160, 190, 198, 176, 110];
  let y = 108;
  const markRows = [5, 6];
  lineW.forEach((w, i) => {
    if (!w) { y += 8; return; }
    if (markRows.includes(i)) {
      b += `<rect class="ill-highlight" x="52" y="${y - 4.6}" width="${w + 8}" height="9.2" rx="1" style="--d:${i - 5}"/>`;
      b += `<rect class="ill-doc__mark" x="56" y="${y - 1}" width="${w}" height="2.2" rx="1"/>`;
    } else {
      b += `<rect class="ill-doc__line" x="56" y="${y - 1}" width="${w}" height="2" rx="1"/>`;
    }
    y += 12;
  });
  b += label(56, 338, 'TABLE 1 — MAX PATH LENGTH', { cls: 'ill-label--tiny' });
  b += `<rect class="ill-doc__line" x="56" y="344" width="120" height="2" rx="1"/>`;

  // The trace from the quote to the finding
  b += `<circle class="ill-anchor" cx="262" cy="175" r="3"/>`;
  b += `<path class="ill-trace" d="M262 175 C 300 175, 300 150, 336 150" style="--len:90"/>`;
  b += `<circle class="ill-anchor ill-anchor--end" cx="336" cy="150" r="3"/>`;

  // The finding card
  b += `<g class="ill-card">
    <rect class="ill-card__box" x="336" y="92" width="232" height="178" rx="2"/>
    <rect class="ill-card__rule" x="336" y="92" width="2" height="178"/>
    ${label(354, 116, 'FINDING', { cls: 'ill-label--accent' })}
    <g class="ill-conf"><rect x="512" y="110" width="12" height="4" rx="1"/><rect x="527" y="110" width="12" height="4" rx="1"/><rect x="542" y="110" width="12" height="4" rx="1"/></g>
    <text class="ill-card__text" x="354" y="144">Self-attention links every</text>
    <text class="ill-card__text" x="354" y="163">position in a constant number</text>
    <text class="ill-card__text" x="354" y="182">of sequential steps.</text>
    <line class="ill-card__divider" x1="354" y1="202" x2="550" y2="202"/>
    ${label(354, 222, 'VASWANI ’17 · §4 · P.6', { cls: 'ill-label--tiny' })}
    <g class="ill-check" transform="translate(354 238)">
      <circle cx="6" cy="6" r="6"/><path d="M3 6.2 L5.2 8.4 L9 4.2"/>
    </g>
    ${label(368, 247.5, 'QUOTE FOUND IN THE PAPER', { cls: 'ill-label--tiny ill-label--ok' })}
  </g>`;

  b += label(452, 310, 'THE SENTENCE BEHIND IT,', { anchor: 'middle', cls: 'ill-caption' });
  b += label(452, 326, 'ONE CLICK AWAY', { anchor: 'middle', cls: 'ill-caption' });

  return svg('0 0 600 392', 'A highlighted sentence on a paper page traced to the finding built from it', b);
}

/** A star chart of attention research: rings are publication years, lines are citations. */
export function constellationIllustration() {
  const cx = 300, cy = 212;
  let b = '';
  const pt = (deg, r) => [cx + r * Math.cos(deg * Math.PI / 180), cy + r * Math.sin(deg * Math.PI / 180)];

  // Graticule
  for (let a = 0; a < 180; a += 30) {
    const [x1, y1] = pt(a, 190);
    const [x2, y2] = pt(a + 180, 190);
    b += `<line class="ill-grid" x1="${f(x1)}" y1="${f(y1)}" x2="${f(x2)}" y2="${f(y2)}"/>`;
  }
  const rings = [[45, '1997'], [100, '2014'], [150, '2018'], [182, '2022']];
  rings.forEach(([r, year], i) => {
    b += `<circle class="ill-ring ${i === 1 ? 'ill-ring--dash' : ''}" cx="${cx}" cy="${cy}" r="${r}"/>`;
    b += label(cx + 4, cy + r - 4, year, { cls: 'ill-label--tiny' });
  });
  for (let a = 0; a < 360; a += 10) {
    const [x1, y1] = pt(a, 182);
    const [x2, y2] = pt(a, a % 30 === 0 ? 190 : 186);
    b += `<line class="ill-tick" x1="${f(x1)}" y1="${f(y1)}" x2="${f(x2)}" y2="${f(y2)}"/>`;
  }

  // Background stars (fixed positions, so the drawing is the same every visit)
  const dust = [[86, 64], [132, 330], [520, 58], [548, 300], [70, 190], [210, 40], [410, 392], [560, 190], [120, 120], [486, 360], [44, 280], [372, 30], [256, 390], [530, 120], [180, 300], [430, 70]];
  dust.forEach(([x, y], i) => { b += `<circle class="ill-dust" cx="${x}" cy="${y}" r="${i % 3 ? 1 : 1.5}" style="--i:${i}"/>`; });

  // The slow sweep
  b += `<g class="ill-sweep ill-sweep--slow"><path class="ill-sweep__wedge ill-sweep__wedge--faint" d="M${cx} ${cy} L${cx + 182} ${cy} A182 182 0 0 0 ${f(pt(-28, 182)[0])} ${f(pt(-28, 182)[1])} Z"/></g>`;

  const stars = {
    lstm: { at: pt(200, 45), name: 'LSTM', year: 1997, dx: -8, anchor: 'end' },
    seq: { at: pt(150, 100), name: 'SEQ2SEQ', year: 2014, dx: -10, anchor: 'end' },
    att: { at: pt(248, 100), name: 'NEURAL ATTENTION', year: 2014, dx: -10, anchor: 'end' },
    tr: { at: pt(318, 135), name: 'TRANSFORMER', year: 2017, dx: 12, anchor: 'start', hot: true },
    bert: { at: pt(22, 150), name: 'BERT', year: 2018, dx: 10, anchor: 'start' },
    gpt: { at: pt(348, 168), name: 'GPT-3', year: 2020, dx: 10, anchor: 'start' },
    flash: { at: pt(62, 182), name: 'FLASHATTENTION', year: 2022, dx: 10, anchor: 'start' },
  };
  const cites = [['seq', 'lstm'], ['att', 'seq'], ['tr', 'att'], ['tr', 'seq'], ['bert', 'tr'], ['gpt', 'tr'], ['flash', 'tr'], ['gpt', 'bert']];
  cites.forEach(([p, q], i) => {
    const [x1, y1] = stars[p].at;
    const [x2, y2] = stars[q].at;
    const hot = p === 'tr' || q === 'tr';
    b += `<line class="ill-edge ill-draw ${hot ? 'ill-edge--hot' : ''}" x1="${f(x1)}" y1="${f(y1)}" x2="${f(x2)}" y2="${f(y2)}" style="--len:${f(Math.hypot(x2 - x1, y2 - y1))};--i:${i}"/>`;
  });
  Object.values(stars).forEach((s, i) => {
    const [x, y] = s.at;
    const r = s.hot ? 5.5 : 3.6;
    if (s.hot) b += `<circle class="ill-halo" cx="${f(x)}" cy="${f(y)}" r="12"/>`;
    b += `<circle class="ill-star ${s.hot ? 'ill-star--hot' : ''}" cx="${f(x)}" cy="${f(y)}" r="${r}" style="--i:${i}"/>`;
    b += label(x + s.dx, y - 2, s.name, { anchor: s.anchor, cls: s.hot ? 'ill-label--accent' : '' });
    b += label(x + s.dx, y + 9, String(s.year), { anchor: s.anchor, cls: 'ill-label--tiny' });
  });

  b += label(24, 404, 'RINGS — YEAR PUBLISHED', { cls: 'ill-caption' });
  b += label(576, 404, 'LINES — CITATIONS', { anchor: 'end', cls: 'ill-caption' });

  return svg('0 0 600 412', 'A star chart of attention research, with rings for publication years and lines for citations', b);
}

/** Adds .in-view to each element once it scrolls into view (runs the SVG animations). */
export function revealOnScroll(root) {
  const targets = root.querySelectorAll('[data-reveal]');
  if (!('IntersectionObserver' in window)) {
    targets.forEach(t => t.classList.add('in-view'));
    return () => {};
  }
  const io = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        e.target.classList.add('in-view');
        io.unobserve(e.target);
      }
    });
  }, { threshold: 0.18, rootMargin: '0px 0px -40px 0px' });
  targets.forEach(t => io.observe(t));
  return () => io.disconnect();
}
