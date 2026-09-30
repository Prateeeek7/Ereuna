/**
 * Research map: header, stats and tabs — Overview, Papers, Findings, Compare,
 * Conflicts, Gaps, Tools, Graph. Tab and scroll state live in the URL (?tab=).
 */
import {
  getMap, getMapGraph, saveMap, removeSavedMap, listSavedMaps, downloadExport, refreshMap, subscribeToJob, rememberMap,
} from '../api.js';
import {
  h, icon, toast, confidenceTag, evidenceQuote, skeletonRows, formatNumber, citations, formatValue, paperLabel, capitalize, emptyState,
} from '../utils.js';
import { navigate, getParams } from '../router.js';
import { renderMapViews } from '../components/mapViews.js';
import { buildCompareColumns, defaultColumnIds, compareCell, compareRowOrder } from '../compare.js';

const TABS = [
  ['overview', 'Overview'], ['papers', 'Papers'], ['findings', 'Findings'], ['compare', 'Compare'],
  ['conflicts', 'Conflicts'], ['gaps', 'Gaps'], ['tools', 'Tools'], ['graph', 'Graph'],
];

export async function renderMap(container, params) {
  const mapId = params.id;
  const cleanups = [];
  const page = h('div', { className: 'page map-page' });
  const inner = h('div', { className: 'container' });
  inner.appendChild(h('div', { className: 'map-skeleton' }, skeletonRows(5)));
  page.appendChild(inner);
  container.appendChild(page);

  let map;
  try {
    map = await getMap(mapId);
  } catch (err) {
    if (!page.isConnected) return;
    document.title = 'Map not found · Ereuna';
    inner.replaceChildren(emptyState('travel_explore', err.status === 404 ? 'Map not found' : 'Couldn’t open this map',
      err.status === 404 ? 'It may have been built from someone else’s PDFs, or the link is wrong.' : err.message,
      h('a', { href: '#/', className: 'btn btn-primary' }, 'New search')));
    return;
  }
  if (!page.isConnected) return;
  rememberMap(map);
  document.title = `${map.topic} · Ereuna`;

  const papersById = new Map(map.papers.map(p => [p.id, p]));
  const labelOf = id => paperLabel(papersById.get(id)) || id;
  const openPaper = id => navigate(`/paper/${encodeURIComponent(id)}?map=${encodeURIComponent(map.id)}`);
  const isUpload = map.source === 'upload';
  const stats = map.stats || {};

  // ── Header ──
  let saved = false;
  const saveBtn = h('button', {
    className: 'btn btn-outline btn-sm', type: 'button', 'aria-pressed': 'false',
    onClick: async () => {
      saveBtn.disabled = true;
      try {
        if (saved) { await removeSavedMap(map.id); saved = false; toast('Removed from library'); }
        else { await saveMap(map.id); saved = true; toast('Saved to library'); }
        paintSave();
      } catch (e) { toast(e.message); }
      saveBtn.disabled = false;
    },
  });
  const paintSave = () => {
    saveBtn.setAttribute('aria-pressed', String(saved));
    saveBtn.replaceChildren(icon('bookmark', saved ? 'icon-filled' : ''), saved ? 'Saved' : 'Save');
  };
  paintSave();

  const refreshBtn = isUpload ? null : h('button', {
    className: 'btn btn-ghost btn-sm', type: 'button', title: 'Search again for newer papers',
    onClick: () => runRefresh(),
  }, icon('refresh'), 'Refresh');

  const meta = [
    `${stats.total_papers ?? map.papers.length} papers`,
    stats.full_text_papers ? `${stats.full_text_papers} read in full` : null,
    stats.year_range?.length === 2 && stats.year_range[0] ? `${stats.year_range[0]}–${stats.year_range[1]}` : null,
    map.created_at ? `built ${new Date(map.created_at).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}` : null,
  ].filter(Boolean).join(' · ');

  const header = h('header', { className: 'map-header' },
    h('div', { className: 'map-header__top' },
      h('div', { className: 'map-header__title-col' },
        h('div', { className: 'map-header__kicker' },
          isUpload ? h('span', { className: 'pill pill--accent' }, icon('lock'), 'From your PDFs · private') : 'Research map'),
        h('h1', { className: 'map-header__title' }, map.topic),
        h('div', { className: 'map-header__meta' }, meta)),
      h('div', { className: 'map-header__actions' },
        saveBtn, refreshBtn,
        h('button', { className: 'btn btn-primary btn-sm', type: 'button', onClick: () => exportDialog(map) }, icon('download'), 'Export'))),
    h('div', { className: 'stats-grid' },
      ...[
        [stats.total_papers ?? map.papers.length, 'Papers', 'papers'],
        [isUpload ? stats.uploaded_papers : stats.full_text_papers, isUpload ? 'Your PDFs' : 'Full text', 'papers'],
        [map.findings?.length || 0, 'Findings', 'findings'],
        [stats.contradictions_count ?? map.contradictions?.length ?? 0, 'Conflicts', 'conflicts'],
        [stats.gaps_count ?? map.gaps?.length ?? 0, 'Gaps', 'gaps'],
      ].map(([v, l, tab]) => h('button', { className: 'stat', type: 'button', onClick: () => select(tab) },
        h('div', { className: 'stat__value' }, String(v ?? 0)), h('div', { className: 'stat__label' }, l)))));
  inner.replaceChildren(header);

  // ── Tabs ──
  const counts = {
    papers: map.papers.length, findings: map.findings?.length || 0, conflicts: map.contradictions?.length || 0,
    gaps: map.gaps?.length || 0, tools: map.tools?.length || 0,
  };
  const tabBtns = TABS.map(([id, text]) => h('button', {
    className: 'tab', type: 'button', role: 'tab', id: `tab-${id}`, 'aria-controls': 'tab-panel',
    onClick: () => select(id),
    onKeydown: e => {
      const i = TABS.findIndex(t => t[0] === id);
      const next = e.key === 'ArrowRight' ? i + 1 : e.key === 'ArrowLeft' ? i - 1 : null;
      if (next === null) return;
      e.preventDefault();
      const t = TABS[(next + TABS.length) % TABS.length][0];
      select(t);
      document.getElementById(`tab-${t}`)?.focus();
    },
  }, text, counts[id] ? h('span', { className: 'tab__count' }, String(counts[id])) : null));
  const tabBar = h('div', { className: 'tabs', role: 'tablist', 'aria-label': 'Map sections' }, ...tabBtns);
  const panel = h('section', { className: 'tab-panel', id: 'tab-panel', role: 'tabpanel' });
  inner.append(tabBar, panel);

  let active = null;
  let panelCleanup = null;
  function select(id, { push = true } = {}) {
    if (!TABS.some(t => t[0] === id)) id = 'overview';
    if (id === active) return;
    active = id;
    tabBtns.forEach((b, i) => {
      const on = TABS[i][0] === id;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', String(on));
      b.tabIndex = on ? 0 : -1;
      if (on) b.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    });
    panel.setAttribute('aria-labelledby', `tab-${id}`);
    if (panelCleanup) { panelCleanup(); panelCleanup = null; }
    panel.replaceChildren();
    const ctx = { map, papersById, labelOf, openPaper, isUpload, select };
    const r = ({
      overview: renderOverview, papers: renderPapers, findings: renderFindings, compare: renderCompare,
      conflicts: renderConflicts, gaps: renderGaps, tools: renderTools, graph: renderGraphTab,
    })[id](panel, ctx);
    if (typeof r === 'function') panelCleanup = r;
    else if (r && typeof r.then === 'function') {
      r.then(fn => {
        if (typeof fn !== 'function') return;
        if (active === id) panelCleanup = fn; else fn();
      });
    }
    if (push) history.replaceState(null, '', `#/map/${encodeURIComponent(map.id)}${id === 'overview' ? '' : `?tab=${id}`}`);
  }
  cleanups.push(() => panelCleanup && panelCleanup());
  select(getParams().params.tab || 'overview', { push: false });

  // Saved state
  listSavedMaps().then(list => { saved = list.some(m => m.id === map.id); paintSave(); }).catch(() => {});

  async function runRefresh() {
    refreshBtn.disabled = true;
    refreshBtn.replaceChildren(h('span', { className: 'spinner' }), 'Refreshing…');
    try {
      const { job_id } = await refreshMap(map.id);
      const sub = subscribeToJob(job_id, (event, data) => {
        if (event === 'done') { toast('Map refreshed'); navigate(`/map/${data.map_id}`); }
        else if (event === 'error') {
          toast(data.message || 'Refresh failed');
          refreshBtn.disabled = false;
          refreshBtn.replaceChildren(icon('refresh'), 'Refresh');
        }
      });
      cleanups.push(() => sub.close());
    } catch (e) {
      toast(e.message);
      refreshBtn.disabled = false;
      refreshBtn.replaceChildren(icon('refresh'), 'Refresh');
    }
  }

  return () => cleanups.forEach(fn => fn());
}

// ── Shared bits ─────────────────────────────────────────
function sectionLabel(text) {
  return h('div', { className: 'section-label' }, text);
}

function paperChip(id, ctx) {
  return h('button', { className: 'paper-chip', type: 'button', onClick: () => ctx.openPaper(id), title: ctx.papersById.get(id)?.title || '' }, ctx.labelOf(id));
}

function paperRow(p, ctx, { showReason = true } = {}) {
  const uploaded = p.source === 'upload';
  const meta = [
    p.year > 0 ? String(p.year) : null,
    p.venue || null,
    uploaded && !p.citation_count ? null : citations(p.citation_count),
  ].filter(Boolean).join(' · ');
  return h('li', {},
    h('a', { className: 'paper-row', href: `#/paper/${encodeURIComponent(p.id)}?map=${encodeURIComponent(ctx.map.id)}` },
      h('div', { className: 'paper-row__top' },
        h('div', { className: 'paper-row__title' }, p.title),
        h('div', { className: 'paper-row__tags' },
          uploaded ? h('span', { className: 'pill pill--accent' }, 'Your PDF') : null,
          h('span', { className: `paper-row__tag ${p.has_full_text ? 'paper-row__tag--full' : 'paper-row__tag--abstract'}` }, p.has_full_text ? 'Full text' : 'Abstract'))),
      h('div', { className: 'paper-row__meta' }, [p.authors?.length ? authorsShort(p.authors) : null, meta].filter(Boolean).join(' · ')),
      showReason && p.relevance_reason && !uploaded ? h('div', { className: 'paper-row__reason' }, p.relevance_reason) : null));
}

function authorsShort(authors) {
  if (authors.length === 1) return authors[0].name;
  if (authors.length === 2) return `${authors[0].name} & ${authors[1].name}`;
  return `${authors[0].name} et al.`;
}

function findingItem(f, ctx) {
  const firstPaper = f.paper_ids?.[0] || f.evidence?.paper_id;
  return h('li', { className: 'finding-item' },
    h('div', { className: 'finding-item__top' },
      h('div', { className: 'finding-item__text' }, f.text),
      confidenceTag(f.confidence)),
    f.evidence?.quote ? evidenceQuote(f.evidence, firstPaper ? ctx.labelOf(firstPaper) : '') : null,
    f.paper_ids?.length ? h('div', { className: 'finding-item__papers' }, ...f.paper_ids.map(id => paperChip(id, ctx))) : null);
}

// ── Overview ────────────────────────────────────────────
function renderOverview(el, ctx) {
  const { map } = ctx;
  const nothing = !map.synthesis?.text && !map.findings?.length && !map.gaps?.length;
  if (map.synthesis?.text) {
    el.append(sectionLabel('Synthesis'),
      h('p', { className: 'synthesis' }, map.synthesis.text),
      map.synthesis.citation_ids?.length
        ? h('div', { className: 'finding-item__papers synthesis__refs' }, h('span', { className: 'synthesis__refs-label' }, 'Drawn from'), ...map.synthesis.citation_ids.filter(id => ctx.papersById.has(id)).map(id => paperChip(id, ctx)))
        : null);
  }
  if (map.findings?.length) {
    el.append(sectionLabel(`Key findings · ${Math.min(5, map.findings.length)} of ${map.findings.length}`),
      h('ul', { className: 'plain-list' }, ...map.findings.slice(0, 5).map(f => findingItem(f, ctx))));
    if (map.findings.length > 5) el.appendChild(moreLink('All findings', 'findings', ctx));
  }
  if (map.gaps?.length) {
    el.append(sectionLabel('Open questions'),
      h('ul', { className: 'plain-list' }, ...map.gaps.slice(0, 3).map((g, i) => gapItem(g, i, ctx, false))));
    if (map.gaps.length > 3) el.appendChild(moreLink('All gaps and experiments', 'gaps', ctx));
  }
  el.append(sectionLabel('Top papers'), h('ul', { className: 'plain-list' }, ...map.papers.slice(0, 5).map(p => paperRow(p, ctx, { showReason: false }))));
  if (map.papers.length > 5) el.appendChild(moreLink(`All ${map.papers.length} papers`, 'papers', ctx));
  if (nothing) el.prepend(h('div', { className: 'notice' }, icon('info'), 'No findings could be extracted for this map. The papers are listed below and their abstracts are available.'));
}

function moreLink(text, tab, ctx) {
  return h('button', { className: 'more-link', type: 'button', onClick: () => { ctx.select(tab); window.scrollTo({ top: 0, behavior: 'smooth' }); } }, text, icon('arrow_forward'));
}

// ── Papers ──────────────────────────────────────────────
function renderPapers(el, ctx) {
  const { map } = ctx;
  let sort = 'relevance';
  let query = '';
  const search = h('input', { className: 'input input-sm', type: 'search', placeholder: 'Filter by title, author or venue', 'aria-label': 'Filter papers' });
  const sortSel = h('select', { className: 'select', 'aria-label': 'Sort papers' },
    ...[['relevance', 'Most relevant'], ['year', 'Newest'], ['citations', 'Most cited'], ['fulltext', 'Full text first']].map(([v, t]) => h('option', { value: v }, t)));
  const list = h('ul', { className: 'plain-list' });
  const count = h('div', { className: 'toolbar__count' });
  const draw = () => {
    const q = query.toLowerCase();
    let ps = map.papers.filter(p => !q || [p.title, p.venue, ...(p.authors || []).map(a => a.name)].join(' ').toLowerCase().includes(q));
    if (sort === 'year') ps = [...ps].sort((a, b) => b.year - a.year);
    if (sort === 'citations') ps = [...ps].sort((a, b) => b.citation_count - a.citation_count);
    if (sort === 'fulltext') ps = [...ps].sort((a, b) => Number(b.has_full_text) - Number(a.has_full_text));
    count.textContent = `${ps.length} of ${map.papers.length} papers`;
    list.replaceChildren(...ps.map(p => paperRow(p, ctx)));
    if (!ps.length) list.appendChild(h('li', { className: 'text-ink2 empty-inline' }, 'No papers match.'));
  };
  search.addEventListener('input', () => { query = search.value; draw(); });
  sortSel.addEventListener('change', () => { sort = sortSel.value; draw(); });
  el.append(h('div', { className: 'toolbar' }, search, sortSel, count), list);
  draw();
}

// ── Findings ────────────────────────────────────────────
function renderFindings(el, ctx) {
  const { map } = ctx;
  if (!map.findings?.length) {
    el.appendChild(emptyState('lightbulb', 'No cross-paper findings', 'Findings for each paper are on its page, under Papers.'));
    return;
  }
  const themes = [...new Set(map.findings.map(f => f.theme).filter(Boolean))];
  let theme = null;
  const list = h('ul', { className: 'plain-list' });
  const draw = () => list.replaceChildren(...map.findings.filter(f => !theme || f.theme === theme).map(f => findingItem(f, ctx)));
  if (themes.length > 1) {
    const chips = h('div', { className: 'chip-group chip-group--wrap', role: 'radiogroup', 'aria-label': 'Theme' });
    [[null, 'All'], ...themes.map(t => [t, capitalize(t)])].forEach(([v, t]) => {
      const c = h('button', {
        className: `chip ${v === theme ? 'active' : ''}`, type: 'button',
        onClick: () => { theme = v; chips.querySelectorAll('.chip').forEach(x => x.classList.remove('active')); c.classList.add('active'); draw(); },
      }, t);
      chips.appendChild(c);
    });
    el.appendChild(chips);
  }
  el.appendChild(sectionLabel(`${map.findings.length} findings, each with its source sentence`));
  el.appendChild(list);
  draw();
}

// ── Compare ─────────────────────────────────────────────
function renderCompare(el, ctx) {
  const papers = ctx.map.papers.filter(p => p.extraction);
  if (papers.length < 2) {
    el.appendChild(emptyState('table_chart', 'Not enough data to compare', 'Comparison needs at least two papers with extracted results.'));
    return;
  }
  const cols = buildCompareColumns(papers);
  let visible = defaultColumnIds(cols);

  const picker = h('details', { className: 'column-picker' },
    h('summary', { className: 'btn btn-outline btn-sm' }, icon('view_column'), 'Columns'),
    h('div', { className: 'column-picker__menu' },
      ...cols.map(c => h('label', { className: 'column-picker__item' },
        h('input', {
          type: 'checkbox', checked: visible.has(c.id),
          onChange: e => { if (e.target.checked) visible.add(c.id); else visible.delete(c.id); draw(); },
        }),
        h('span', {}, c.title, c.unit ? h('span', { className: 'text-ink3' }, ` (${c.unit})`) : null),
        h('span', { className: 'column-picker__cov' }, `${c.coverage}`)))));

  const wrap = h('div', { className: 'data-table-wrapper' });
  el.append(
    h('div', { className: 'toolbar' },
      h('div', { className: 'toolbar__count' }, `${papers.length} papers with extracted data`),
      picker),
    h('p', { className: 'table-note' }, 'Values are shown as each paper reports them, with their conditions. Hover a value to see its source sentence. Whether higher or lower is better depends on the metric, so nothing is ranked.'),
    wrap);

  function draw() {
    const shown = cols.filter(c => visible.has(c.id));
    const rows = compareRowOrder(papers, shown);
    const table = h('table', { className: 'data-table' },
      h('thead', {}, h('tr', {},
        h('th', { scope: 'col', className: 'sticky-col' }, 'Paper'),
        ...shown.map(c => h('th', { scope: 'col', className: c.text ? '' : 'metric-cell' },
          h('div', {}, c.title), c.unit ? h('div', { className: 'th-unit' }, c.unit) : null)))),
      h('tbody', {}, ...rows.map(p => h('tr', {},
        h('th', { scope: 'row', className: 'sticky-col' },
          h('button', { className: 'table-paper', type: 'button', onClick: () => ctx.openPaper(p.id), title: p.title },
            paperLabel(p), p.source === 'upload' ? h('span', { className: 'pill pill--accent pill--xs' }, 'PDF') : null)),
        ...shown.map(c => {
          const cell = compareCell(p, c);
          return h('td', {
            className: c.text ? 'text-cell' : 'metric-cell',
            title: cell.evidence?.quote ? `“${cell.evidence.quote}”${cell.evidence.section ? ` — ${cell.evidence.section}` : ''}` : undefined,
          },
            h('div', { className: cell.text === '—' ? 'text-ink3' : c.text ? 'text-cell__value' : 'metric-cell__value' }, cell.text),
            cell.detail ? h('div', { className: 'metric-cell__detail' }, cell.detail) : null);
        })))));
    wrap.replaceChildren(table);
  }
  draw();
}

// ── Conflicts ───────────────────────────────────────────
function renderConflicts(el, ctx) {
  const list = ctx.map.contradictions || [];
  if (!list.length) {
    el.appendChild(emptyState('balance', 'No conflicting results', 'No two papers reported clearly different values or opposing claims for the same thing under comparable conditions.'));
    return;
  }
  el.appendChild(sectionLabel(`${list.length} places where papers disagree`));
  el.appendChild(h('ul', { className: 'plain-list' }, ...list.map(c => h('li', { className: 'conflict-card' },
    h('div', { className: 'conflict-card__head' },
      icon(c.kind === 'claim' ? 'forum' : 'compare_arrows', 'text-conflict'),
      h('h3', { className: 'conflict-card__title' }, capitalize(c.metric)),
      h('span', { className: 'pill' }, c.kind === 'claim' ? 'Opposing claims' : 'Different values'),
      confidenceTag(c.confidence)),
    h('div', { className: 'conflict-card__sides' }, ...c.entries.map(e => h('div', { className: 'conflict-side' },
      h('div', { className: 'conflict-side__top' },
        paperChip(e.paper_id, ctx),
        e.value != null ? h('span', { className: 'conflict-side__value' }, `${formatValue(e.value)} ${e.unit || ''}`.trim()) : null),
      e.subject ? h('div', { className: 'conflict-side__subject' }, e.subject) : null,
      Object.keys(e.conditions || {}).length ? h('div', { className: 'conflict-side__cond' }, Object.entries(e.conditions).map(([k, v]) => `${k}: ${v}`).join(' · ')) : null,
      e.statement ? h('p', { className: 'conflict-side__statement' }, e.statement) : null,
      e.quote ? evidenceQuote({ quote: e.quote, section: e.section, page: e.page }) : null))),
    c.likely_reason ? h('p', { className: 'conflict-card__reason' }, h('strong', {}, 'Possible reason: '), c.likely_reason) : null))));
}

// ── Gaps & experiments ──────────────────────────────────
function gapItem(g, i, ctx, withExperiments = true) {
  const experiments = withExperiments ? (ctx.map.experiments || []).filter(x => x.gap_id === g.id) : [];
  return h('li', { className: 'gap-item' },
    h('div', { className: 'gap-item__head' },
      h('span', { className: 'gap-badge' }, `G${i + 1}`),
      h('div', { className: 'gap-item__body' },
        h('div', { className: 'gap-item__statement' }, g.statement),
        g.pattern ? h('div', { className: 'gap-item__pattern' }, g.pattern) : null,
        g.why_it_matters ? h('p', { className: 'gap-item__why' }, g.why_it_matters) : null,
        g.supporting_paper_ids?.length ? h('div', { className: 'finding-item__papers' }, h('span', { className: 'synthesis__refs-label' }, 'Stated in'), ...g.supporting_paper_ids.map(id => paperChip(id, ctx))) : null),
      confidenceTag(g.confidence)),
    ...experiments.map(x => h('div', { className: 'experiment-card' },
      h('div', { className: 'experiment-card__head' },
        icon('science', 'text-accent'),
        h('div', { className: 'experiment-card__kicker' }, 'Suggested experiment'),
        h('span', { className: `experiment-card__difficulty experiment-card__difficulty--${(x.difficulty || 'MED').toLowerCase()}` }, `${capitalize((x.difficulty || 'med').toLowerCase())} effort`)),
      h('div', { className: 'experiment-card__hypothesis' }, x.hypothesis),
      x.setup ? h('p', { className: 'experiment-card__text' }, h('strong', {}, 'Setup. '), x.setup) : null,
      x.variables?.length ? h('p', { className: 'experiment-card__text' }, h('strong', {}, 'Vary. '), x.variables.join(', ')) : null,
      x.expected_result ? h('p', { className: 'experiment-card__text' }, h('strong', {}, 'Expect. '), x.expected_result) : null,
      x.tools?.length ? h('div', { className: 'tag-row' }, ...x.tools.map(t => h('span', { className: 'tool-tag' }, t))) : null)));
}

function renderGaps(el, ctx) {
  const gaps = ctx.map.gaps || [];
  if (!gaps.length) {
    el.appendChild(emptyState('explore_off', 'No gaps identified', 'None of the papers stated a limitation or open question that recurs across the set.'));
    return;
  }
  const expCount = (ctx.map.experiments || []).length;
  el.appendChild(sectionLabel(`${gaps.length} gaps stated by authors${expCount ? ` · ${expCount} suggested experiments` : ''}`));
  el.appendChild(h('ul', { className: 'plain-list' }, ...gaps.map((g, i) => gapItem(g, i, ctx))));
  if (expCount) el.appendChild(h('p', { className: 'table-note' }, 'Experiments are suggestions drafted from the stated gaps. Check them against the papers before planning work.'));
}

// ── Tools ───────────────────────────────────────────────
function renderTools(el, ctx) {
  const tools = ctx.map.tools || [];
  if (!tools.length) {
    el.appendChild(emptyState('construction', 'No tools or datasets found', 'None of the papers named the software, datasets or hardware they used in a way that could be verified.'));
    return;
  }
  const byCat = new Map();
  tools.forEach(t => {
    const k = t.category || 'other';
    if (!byCat.has(k)) byCat.set(k, []);
    byCat.get(k).push(t);
  });
  [...byCat.entries()].sort((a, b) => b[1].length - a[1].length).forEach(([cat, list]) => {
    el.append(sectionLabel(`${capitalize(cat)} · ${list.length}`),
      h('ul', { className: 'tools-list' }, ...list.sort((a, b) => b.count - a.count).map(t => h('li', { className: 'tools-row' },
        h('span', { className: 'tools-row__name' }, t.name),
        h('span', { className: 'tools-row__count' }, `${t.count} paper${t.count === 1 ? '' : 's'}`),
        h('div', { className: 'tools-row__papers' }, ...(t.paper_ids || []).slice(0, 6).map(id => paperChip(id, ctx)))))));
  });
}

// ── Graph: several views of the map ─────────────────────
async function renderGraphTab(el, ctx) {
  const holder = h('div', { className: 'viz' }, skeletonRows(2));
  el.appendChild(holder);
  let cleanup = null;
  let graphData = ctx.map.graph?.nodes?.length ? ctx.map.graph : null;
  if (!graphData) {
    try { graphData = await getMapGraph(ctx.map.id); } catch { graphData = null; }
  }
  if (!holder.isConnected) return () => {};
  holder.replaceChildren();
  let initial = 'connections';
  try { initial = sessionStorage.getItem('ereuna_viz') || initial; } catch { /* storage blocked */ }
  cleanup = renderMapViews(holder, ctx.map, {
    graphData, onOpenPaper: ctx.openPaper, initial,
    onViewChange: v => { try { sessionStorage.setItem('ereuna_viz', v); } catch { /* ignore */ } },
  });
  return () => cleanup && cleanup();
}

// ── Export ──────────────────────────────────────────────
function exportDialog(map) {
  let format = 'md';
  const formats = [
    ['md', 'Markdown', 'Obsidian, Notion, lab notebooks', 'description'],
    ['bibtex', 'BibTeX', 'LaTeX, Overleaf, Zotero', 'format_quote'],
    ['csv', 'CSV', 'Excel, Google Sheets, pandas', 'table_chart'],
  ];
  const opts = h('div', { className: 'export-options', role: 'radiogroup', 'aria-label': 'Format' });
  const drawOpts = () => opts.replaceChildren(...formats.map(([id, label, desc, ic]) => h('button', {
    className: `export-option ${id === format ? 'active' : ''}`, type: 'button', role: 'radio', 'aria-checked': String(id === format),
    onClick: () => { format = id; drawOpts(); },
  }, icon(ic), h('div', { className: 'flex-1' }, h('div', { className: 'type-title' }, label), h('div', { className: 'type-body-small text-ink2' }, desc)))));
  drawOpts();

  const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = e => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  const download = h('button', {
    className: 'btn btn-primary', type: 'button',
    onClick: async () => {
      download.disabled = true;
      const ext = format === 'bibtex' ? 'bib' : format;
      const name = `ereuna_${map.topic.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 40)}.${ext}`;
      try {
        await downloadExport(map.id, format, name);
        toast('Export downloaded');
        close();
      } catch (e) {
        toast(`Export failed: ${e.message}`);
        download.disabled = false;
      }
    },
  }, icon('download'), 'Download');
  const backdrop = h('div', { className: 'modal-backdrop', onClick: e => { if (e.target === backdrop) close(); } },
    h('div', { className: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'export-title' },
      h('h2', { className: 'type-heading', id: 'export-title' }, 'Export map'),
      opts,
      h('div', { className: 'modal__actions' }, h('button', { className: 'btn btn-ghost', type: 'button', onClick: close }, 'Cancel'), download)));
  document.body.appendChild(backdrop);
  download.focus();
}
