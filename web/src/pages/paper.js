/**
 * Paper detail: metadata, what was extracted (each item with its source sentence), abstract.
 */
import { getPaper, getMap, savePaper, removeSavedPaper, listSavedPapers } from '../api.js';
import { h, icon, toast, confidenceTag, evidenceQuote, skeletonRows, formatNumber, citations, formatValue, capitalize, emptyState } from '../utils.js';
import { metricContext } from '../compare.js';

export async function renderPaper(container, params) {
  const paperId = params.id;
  const mapId = params.map;

  const page = h('div', { className: 'page paper-page' });
  const inner = h('article', { className: 'container container--narrow' }, skeletonRows(4));
  page.appendChild(inner);
  container.appendChild(page);

  let paper;
  try {
    if (mapId) {
      const map = await getMap(mapId);
      paper = map.papers.find(p => p.id === paperId);
    }
    if (!paper) paper = await getPaper(paperId);
  } catch (err) {
    if (!page.isConnected) return;
    document.title = 'Paper not found · Ereuna';
    inner.replaceChildren(emptyState('article', err.status === 404 ? 'Paper not found' : 'Couldn’t open this paper', err.status === 404 ? 'It may belong to a private map, or the link is wrong.' : err.message,
      h('a', { href: mapId ? `#/map/${mapId}` : '#/', className: 'btn btn-primary' }, mapId ? 'Back to map' : 'New search')));
    return;
  }
  if (!page.isConnected) return;
  document.title = `${paper.title} · Ereuna`;
  const uploaded = paper.source === 'upload';
  const ext = paper.extraction;

  // Header
  let saved = false;
  const saveBtn = h('button', {
    className: 'btn btn-outline btn-sm', type: 'button',
    onClick: async () => {
      saveBtn.disabled = true;
      try {
        if (saved) { await removeSavedPaper(paper.id); saved = false; toast('Removed from library'); }
        else { await savePaper(paper.id); saved = true; toast('Saved to library'); }
        paintSave();
      } catch (e) { toast(e.message); }
      saveBtn.disabled = false;
    },
  });
  const paintSave = () => { saveBtn.setAttribute('aria-pressed', String(saved)); saveBtn.replaceChildren(icon('bookmark', saved ? 'icon-filled' : ''), saved ? 'Saved' : 'Save'); };
  paintSave();

  const meta = [
    paper.year > 0 ? String(paper.year) : null,
    paper.venue || null,
    uploaded && !paper.citation_count ? null : citations(paper.citation_count),
  ].filter(Boolean).join(' · ');

  const body = [
    mapId ? h('a', { className: 'back-link', href: `#/map/${encodeURIComponent(mapId)}?tab=papers` }, icon('arrow_back'), 'Back to map') : null,
    h('header', { className: 'paper-header' },
      h('div', { className: 'paper-header__tags' },
        uploaded ? h('span', { className: 'pill pill--accent' }, icon('upload_file'), 'Your PDF') : null,
        h('span', { className: `paper-row__tag ${paper.has_full_text ? 'paper-row__tag--full' : 'paper-row__tag--abstract'}` },
          paper.has_full_text ? 'Read in full' : 'Abstract only')),
      h('h1', { className: 'paper-header__title' }, paper.title),
      paper.authors?.length ? h('p', { className: 'paper-header__authors' },
        paper.authors.slice(0, 12).map(a => a.affiliation ? `${a.name} (${a.affiliation})` : a.name).join(', ') + (paper.authors.length > 12 ? ` and ${paper.authors.length - 12} more` : '')) : null,
      meta ? h('div', { className: 'paper-header__meta' }, meta) : null,
      h('div', { className: 'paper-header__actions' },
        paper.doi ? h('a', { className: 'btn btn-outline btn-sm', href: `https://doi.org/${paper.doi}`, target: '_blank', rel: 'noopener' }, icon('open_in_new'), 'Publisher page') : null,
        paper.oa_pdf_url ? h('a', { className: 'btn btn-outline btn-sm', href: paper.oa_pdf_url, target: '_blank', rel: 'noopener' }, icon('picture_as_pdf'), 'Open-access PDF') : null,
        saveBtn)),
  ];

  if (!paper.has_full_text && !uploaded) {
    body.push(h('div', { className: 'notice' }, icon('info'),
      'No open-access full text was available, so only the abstract was read. Results below may be incomplete.'));
  }

  if (paper.relevance_reason && !uploaded) {
    body.push(section('Why it’s in this map', h('p', { className: 'prose' }, paper.relevance_reason)));
  }

  const quote = (ev) => ev?.quote ? evidenceQuote(ev) : null;

  if (ext) {
    if (ext.problem) body.push(section('Problem', h('p', { className: 'prose' }, ext.problem.text), quote(ext.problem.evidence)));
    if (ext.method) body.push(section('Method', h('p', { className: 'prose' }, ext.method.text), quote(ext.method.evidence)));

    const t = ext.technology;
    if (t && (t.device || t.cell_type || t.node_nm)) {
      body.push(section('Platform', h('div', { className: 'kv-row' },
        ...[['Device', t.device], ['Configuration', t.cell_type], ['Node', t.node_nm ? `${t.node_nm} nm` : null]]
          .filter(([, v]) => v).map(([k, v]) => h('div', { className: 'kv' }, h('div', { className: 'kv__k' }, k), h('div', { className: 'kv__v' }, v)))),
      quote(t.evidence)));
    }

    if (ext.metrics?.length) {
      body.push(section(`Reported values · ${ext.metrics.length}`, h('div', { className: 'metric-grid' },
        ...ext.metrics.map(m => h('div', { className: 'metric-card' },
          h('div', { className: 'metric-card__top' }, h('span', { className: 'metric-card__name' }, capitalize(m.name)), confidenceTag(m.confidence)),
          h('div', { className: 'metric-card__value' }, formatValue(m.value), h('span', { className: 'metric-card__unit' }, m.unit)),
          metricContext(m) ? h('div', { className: 'metric-card__cond' }, metricContext(m)) : null,
          quote(m.evidence))))));
    }

    if (ext.findings?.length) {
      body.push(section(`Findings · ${ext.findings.length}`, h('ul', { className: 'plain-list' },
        ...ext.findings.map(f => h('li', { className: 'finding-item' },
          h('div', { className: 'finding-item__top' }, h('div', { className: 'finding-item__text' }, f.text), confidenceTag(f.confidence)),
          quote(f.evidence))))));
    }

    if (ext.limitations?.length) {
      body.push(section(`Limitations stated by the authors · ${ext.limitations.length}`, h('ul', { className: 'plain-list' },
        ...ext.limitations.map(l => h('li', { className: 'finding-item' }, h('div', { className: 'finding-item__text' }, l.text), quote(l.evidence))))));
    }

    const named = [...(ext.tools || []), ...(ext.datasets || [])];
    if (named.length) {
      body.push(section('Tools and datasets', h('div', { className: 'tag-row' },
        ...named.map(t => h('span', { className: 'tool-tag', title: t.evidence?.quote ? `“${t.evidence.quote}”` : undefined },
          t.name, t.category ? h('span', { className: 'tool-tag__count' }, t.category) : null)))));
    }

    if (!ext.problem && !ext.method && !ext.metrics?.length && !ext.findings?.length) {
      body.push(h('div', { className: 'notice' }, icon('info'), 'Nothing could be extracted from this paper with a verifiable quote.'));
    }
    if (ext.extracted_by) body.push(h('p', { className: 'fine-print' }, `Extracted by ${ext.extracted_by}. Every item above quotes the paper; check the original before citing.`));
  }

  if (paper.abstract) body.push(section('Abstract', h('p', { className: 'prose prose--abstract' }, paper.abstract)));

  inner.replaceChildren(...body.filter(Boolean));

  listSavedPapers().then(list => { saved = list.some(p => p.id === paper.id); paintSave(); }).catch(() => {});
}

function section(title, ...children) {
  return h('section', { className: 'paper-section' }, h('h2', { className: 'section-label' }, title), ...children.filter(Boolean));
}
