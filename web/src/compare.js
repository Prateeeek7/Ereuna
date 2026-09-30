/**
 * Comparison table columns, built from the metrics actually extracted for a map.
 * A port of the Android app's CompareColumns.kt so both clients group, convert
 * and label values the same way.
 *
 * No cell is marked "best": whether higher or lower is better depends on the metric.
 */
import { formatValue, capitalize } from './utils.js';

const METRIC_PREFIX = 'metric:';
const RESULTS_ID = 'results';
const MAX_METRIC_COLUMNS = 12;

const metricKey = m => (m.key || '').trim() ||
  (m.name || '').toLowerCase().replace(/[^a-z0-9%\- ]/g, ' ').split(' ').filter(Boolean).join(' ');
const displayUnit = m => (m.canonical_unit || m.unit || '').trim();
const groupUnit = m => (m.normalized_unit || displayUnit(m)).trim();
const columnId = m => `${METRIC_PREFIX}${metricKey(m)}|${groupUnit(m)}`;

/** Subject and stated conditions, e.g. "LLZO · 25 °C · after 20 cycles". */
export function metricContext(m) {
  const c = m.conditions || {};
  const other = (c.other || '').trim();
  const unlessInOther = t => (other && other.replace(/ /g, '').includes(t.replace(/ /g, '')) ? null : t);
  return [
    (m.subject || '').trim() || null,
    c.vdd != null ? unlessInOther(`${formatValue(c.vdd)} V`) : null,
    c.temp_c != null ? unlessInOther(`${formatValue(c.temp_c)} °C`) : null,
    (c.corner || '').trim() || null,
    other || null,
  ].filter(Boolean).join(' · ') || null;
}

export function buildCompareColumns(papers) {
  const cols = [];
  const count = pred => papers.filter(pred).length;
  const tech = p => p.extraction?.technology || {};

  const device = count(p => (tech(p).device || '').trim());
  if (device) cols.push({ id: 'device', title: 'Platform / device', unit: '', coverage: device, text: true });
  const variant = count(p => (tech(p).cell_type || '').trim());
  if (variant) cols.push({ id: 'variant', title: 'Configuration', unit: '', coverage: variant, text: true });
  const node = count(p => tech(p).node_nm != null);
  if (node) cols.push({ id: 'node', title: 'Process node', unit: 'nm', coverage: node });

  const groups = new Map();
  papers.forEach(p => (p.extraction?.metrics || []).forEach(m => {
    if (!metricKey(m)) return;
    const id = columnId(m);
    if (!groups.has(id)) groups.set(id, { title: capitalize((m.name || '').trim()), members: [], papers: new Set() });
    const g = groups.get(id);
    g.members.push(m);
    g.papers.add(p.id);
  }));
  [...groups.entries()]
    .sort((a, b) => b[1].papers.size - a[1].papers.size)
    .slice(0, MAX_METRIC_COLUMNS)
    .forEach(([id, g]) => {
      // Show the column in the unit most papers use, converting others via normalized values.
      const tally = {};
      g.members.forEach(m => { const u = displayUnit(m); tally[u] = (tally[u] || 0) + 1; });
      const unit = Object.entries(tally).sort((a, b) => b[1] - a[1])[0]?.[0] || '';
      const ref = g.members.find(m => displayUnit(m) === unit && m.value !== 0 && m.normalized_value != null);
      const scale = ref ? ref.normalized_value / ref.value : null;
      cols.push({ id, title: g.title, unit, coverage: g.papers.size, scale: Number.isFinite(scale) && scale ? scale : null });
    });

  const withMetrics = count(p => (p.extraction?.metrics || []).length);
  if (withMetrics) cols.push({ id: RESULTS_ID, title: 'Reported results', unit: '', coverage: withMetrics, text: true });
  cols.push({ id: 'year', title: 'Year', unit: '', coverage: count(p => p.year > 0) });
  cols.push({ id: 'citations', title: 'Citations', unit: '', coverage: papers.length });
  return cols.filter(c => c.coverage > 0);
}

/** Columns shown by default: shared technology and metric columns, plus year and citations. */
export function defaultColumnIds(cols) {
  const shared = list => { const s = list.filter(c => c.coverage >= 2); return s.length ? s : list.slice(0, 1); };
  const metricCols = cols.filter(c => c.id.startsWith(METRIC_PREFIX));
  const tech = shared(cols.filter(c => c.id === 'device' || c.id === 'node')).map(c => c.id);
  const measured = metricCols.some(c => c.coverage >= 2)
    ? shared(metricCols).slice(0, 4).map(c => c.id)
    : cols.filter(c => c.id === RESULTS_ID).map(c => c.id);
  const base = ['year', 'citations'].filter(id => cols.some(c => c.id === id));
  return new Set([...tech, ...measured, ...base]);
}

export function compareCell(paper, col) {
  const ext = paper.extraction || {};
  const t = ext.technology || {};
  switch (col.id) {
    case 'year': return { text: paper.year > 0 ? String(paper.year) : '—' };
    case 'citations': return { text: paper.source === 'upload' && !paper.citation_count ? '—' : String(paper.citation_count || 0) };
    case 'device': return { text: (t.device || '').trim() || '—' };
    case 'variant': return { text: (t.cell_type || '').trim() || '—' };
    case 'node': return { text: t.node_nm != null ? `${t.node_nm} nm` : '—' };
    case RESULTS_ID: {
      const seen = new Set();
      const shown = (ext.metrics || []).filter(m => { const k = metricKey(m); if (seen.has(k)) return false; seen.add(k); return true; })
        .slice(0, 2).map(m => `${capitalize(m.name.trim())}: ${formatValue(m.value)} ${displayUnit(m)}`.trim());
      return shown.length ? { text: shown[0], detail: shown[1] || null } : { text: '—' };
    }
    default: {
      const matching = (ext.metrics || []).filter(m => columnId(m) === col.id);
      const m = matching[0];
      if (!m) return { text: '—' };
      const converted = col.scale && m.normalized_value != null ? m.normalized_value / col.scale : null;
      const [value, unit] = converted != null ? [converted, col.unit] : [m.value, displayUnit(m)];
      const more = matching.length > 1 ? `+${matching.length - 1} more` : null;
      const detail = [metricContext(m), more].filter(Boolean).join(' · ') || null;
      const text = unit === col.unit ? formatValue(value) : `${formatValue(value)} ${unit}`.trim();
      return { text, value, detail, evidence: m.evidence };
    }
  }
}

/** Rows with the most filled cells first; ties keep ranking order. */
export function compareRowOrder(papers, cols) {
  const informative = cols.filter(c => c.id !== 'year' && c.id !== 'citations');
  const filled = p => informative.filter(c => compareCell(p, c).text !== '—').length;
  return papers.map((p, i) => [p, filled(p), i]).sort((a, b) => b[1] - a[1] || a[2] - b[2]).map(x => x[0]);
}
