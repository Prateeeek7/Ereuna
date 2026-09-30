/**
 * Library: saved maps and saved papers (stored with the account, shared with the Android app).
 */
import { listSavedMaps, listSavedPapers, removeSavedMap, removeSavedPaper } from '../api.js';
import { h, icon, toast, skeletonRows, formatNumber, citations, emptyState } from '../utils.js';
import { getParams } from '../router.js';

export function renderLibrary(container) {
  document.title = 'Library · Ereuna';
  let tab = getParams().params.tab === 'papers' ? 'papers' : 'maps';

  const content = h('div', { className: 'library-content' });
  const btns = [['maps', 'Maps', 'map'], ['papers', 'Papers', 'article']].map(([id, text, ic]) => h('button', {
    className: 'tab', type: 'button', role: 'tab', onClick: () => { tab = id; paint(); load(); },
    'data-id': id,
  }, icon(ic), text));
  const paint = () => btns.forEach(b => {
    const on = b.dataset.id === tab;
    b.classList.toggle('active', on);
    b.setAttribute('aria-selected', String(on));
    history.replaceState(null, '', `#/library${tab === 'papers' ? '?tab=papers' : ''}`);
  });

  container.appendChild(h('div', { className: 'page' }, h('div', { className: 'container' },
    h('header', { className: 'page-header' },
      h('h1', { className: 'type-display' }, 'Library'),
      h('p', { className: 'page-header__sub' }, 'Maps and papers you’ve saved. They’re also in the Android app when you sign in there.')),
    h('div', { className: 'tabs', role: 'tablist' }, ...btns),
    content)));
  paint();

  async function load() {
    content.replaceChildren(skeletonRows(3));
    try {
      if (tab === 'maps') {
        const maps = await listSavedMaps();
        if (!maps.length) {
          content.replaceChildren(emptyState('bookmark_add', 'No saved maps yet', 'Open a map and press Save to keep it here.', h('a', { href: '#/', className: 'btn btn-primary' }, 'Build a map')));
          return;
        }
        content.replaceChildren(h('ul', { className: 'library-grid' }, ...maps.map(mapCard)));
      } else {
        const papers = await listSavedPapers();
        if (!papers.length) {
          content.replaceChildren(emptyState('bookmark_add', 'No saved papers yet', 'Open a paper from any map and press Save.'));
          return;
        }
        content.replaceChildren(h('ul', { className: 'plain-list' }, ...papers.map(paperItem)));
      }
    } catch (err) {
      content.replaceChildren(emptyState('cloud_off', 'Couldn’t load your library', err.message,
        h('button', { className: 'btn btn-outline', type: 'button', onClick: load }, 'Try again')));
    }
  }

  function removeButton(label, fn, item) {
    return h('button', {
      className: 'icon-btn', type: 'button', title: 'Remove from library', 'aria-label': `Remove ${label} from library`,
      onClick: async e => {
        e.preventDefault(); e.stopPropagation();
        try {
          await fn();
          item.remove();
          toast('Removed from library');
          if (!content.querySelector('li')) load();
        } catch (err) { toast(err.message); }
      },
    }, icon('bookmark_remove'));
  }

  function mapCard(map) {
    const s = map.stats || {};
    const li = h('li');
    li.appendChild(h('a', { className: 'library-card', href: `#/map/${encodeURIComponent(map.id)}` },
      h('div', { className: 'library-card__top' },
        h('div', { className: 'library-card__title' }, map.topic),
        removeButton(map.topic, () => removeSavedMap(map.id), li)),
      map.source === 'upload' ? h('span', { className: 'pill pill--accent' }, icon('lock'), 'From your PDFs') : null,
      h('div', { className: 'library-card__meta' }, [
        `${s.total_papers || 0} papers`,
        s.gaps_count ? `${s.gaps_count} gaps` : null,
        s.year_range?.length === 2 && s.year_range[0] ? `${s.year_range[0]}–${s.year_range[1]}` : null,
      ].filter(Boolean).join(' · ')),
      map.created_at ? h('div', { className: 'library-card__date' }, `Built ${new Date(map.created_at).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}`) : null));
    return li;
  }

  function paperItem(p) {
    const li = h('li');
    li.appendChild(h('a', { className: 'paper-row', href: `#/paper/${encodeURIComponent(p.id)}` },
      h('div', { className: 'paper-row__top' },
        h('div', { className: 'paper-row__title' }, p.title),
        removeButton(p.title, () => removeSavedPaper(p.id), li)),
      h('div', { className: 'paper-row__meta' }, [
        p.source === 'upload' ? 'Your PDF' : null,
        p.year > 0 ? String(p.year) : null,
        p.venue || null,
        p.source === 'upload' && !p.citation_count ? null : citations(p.citation_count),
      ].filter(Boolean).join(' · '))));
    return li;
  }

  load();
}
