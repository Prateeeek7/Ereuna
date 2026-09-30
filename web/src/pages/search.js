/**
 * Search: build a map from a topic (Literature) or from the user's own PDFs (My papers),
 * then follow the build live and open the map when it's done.
 */
import { createMap, createMapFromUploads, subscribeToJob, getRecentMaps, getCurrentUser } from '../api.js';
import { h, icon, toast } from '../utils.js';
import { navigate } from '../router.js';

// Limits enforced by the server (app/pipeline/uploads.py).
const MAX_PDFS = 15;
const MAX_PDF_BYTES = 25 * 1024 * 1024;
const MAX_TOTAL_BYTES = 120 * 1024 * 1024;

const LITERATURE_STAGES = [
  ['expand_query', 'Expanding the query'],
  ['retrieve', 'Searching the databases'],
  ['rank', 'Ranking candidates'],
  ['fetch_fulltext', 'Fetching full text'],
  ['extract', 'Extracting and verifying'],
  ['cross_paper', 'Comparing across papers'],
  ['citation_graph', 'Building the citation graph'],
  ['experiments', 'Drafting experiments'],
];
const UPLOAD_STAGES = [
  ['read_uploads', 'Reading your PDFs'],
  ['identify_papers', 'Identifying your papers'],
  ['find_related', 'Finding related papers'],
  ['fetch_related', 'Fetching their full text'],
  ...LITERATURE_STAGES.slice(4),
];

const formatBytes = n => n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`;

export function renderSearch(container) {
  document.title = 'Ereuna — Research, on your radar';
  let mode = 'literature';
  let files = [];
  let includeRelated = false;
  let job = null;
  let timer = null;
  const filters = { max_papers: 25, min_citations: 0, open_access_only: false, year_min: null };

  const page = h('div', { className: 'page search-page' });
  const inner = h('div', { className: 'container search-container' });
  page.appendChild(inner);
  container.appendChild(page);

  // ── Backdrop + hero ──
  page.prepend(h('div', { className: 'search-backdrop', 'aria-hidden': 'true', innerHTML: backdropSvg() }));
  const first = (getCurrentUser()?.name || '').split(' ')[0];
  const hour = new Date().getHours();
  const greeting = hour < 5 ? 'Working late' : hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const heroTitle = h('h1', { className: 'search-hero__title' });
  const heroSub = h('p', { className: 'search-hero__sub' });
  const hero = h('div', { className: 'search-hero' },
    h('div', { className: 'search-hero__eyebrow' }, h('span', { className: 'live-dot', 'aria-hidden': 'true' }), first ? `${greeting}, ${first}` : greeting),
    heroTitle, heroSub);

  // ── Mode switch ──
  const modeBtns = [['literature', 'travel_explore', 'Literature'], ['papers', 'upload_file', 'My papers']].map(([id, ic, text]) =>
    h('button', {
      className: 'segmented__btn', type: 'button', role: 'tab', 'data-mode': id,
      onClick: () => { if (!job) { mode = id; renderMode(); } },
    }, icon(ic), text));
  const modeSwitch = h('div', { className: 'segmented', role: 'tablist', 'aria-label': 'Build a map from' },
    h('span', { className: 'segmented__indicator', 'aria-hidden': 'true' }), ...modeBtns);

  // ── Topic ──
  const topic = h('input', {
    className: 'search-card__input', id: 'search-topic', type: 'text', maxlength: '300',
    autocomplete: 'off', spellcheck: 'true', 'aria-label': 'Research topic',
  });
  topic.addEventListener('input', updateSubmit);
  const submit = h('button', { className: 'btn btn-primary btn-large search-card__submit', type: 'submit', id: 'search-btn' });

  // ── Filters (literature) ──
  const chipGroup = (label, options, get, set) => {
    const group = h('div', { className: 'filter-group' }, h('span', { className: 'filter-group__label' }, label));
    const chips = h('div', { className: 'chip-group', role: 'radiogroup', 'aria-label': label });
    options.forEach(([text, value]) => {
      const chip = h('button', {
        className: `chip ${get() === value ? 'active' : ''}`, type: 'button', role: 'radio', 'aria-checked': String(get() === value),
        onClick: () => {
          set(value);
          chips.querySelectorAll('.chip').forEach(c => { c.classList.remove('active'); c.setAttribute('aria-checked', 'false'); });
          chip.classList.add('active'); chip.setAttribute('aria-checked', 'true');
        },
      }, text);
      chips.appendChild(chip);
    });
    group.appendChild(chips);
    return group;
  };
  const oaChip = h('button', {
    className: 'chip', type: 'button', 'aria-pressed': 'false',
    onClick: () => {
      filters.open_access_only = !filters.open_access_only;
      oaChip.classList.toggle('active', filters.open_access_only);
      oaChip.setAttribute('aria-pressed', String(filters.open_access_only));
    },
  }, icon('lock_open'), 'Open access');
  const filterSummary = h('span', { className: 'filter-toggle__text' });
  const paintSummary = () => {
    filterSummary.textContent = [
      `${filters.max_papers} papers`,
      filters.year_min ? `since ${filters.year_min}` : 'any year',
      filters.min_citations ? `≥ ${filters.min_citations} citations` : null,
    ].filter(Boolean).join(' · ');
  };
  const filterPop = h('div', { className: 'filter-pop', hidden: true, role: 'dialog', 'aria-label': 'Search filters' },
    chipGroup('Papers', [['15', 15], ['25', 25], ['50', 50]], () => filters.max_papers, v => { filters.max_papers = v; paintSummary(); }),
    chipGroup('Published', [['Any time', null], ['Since 2015', 2015], ['Since 2020', 2020], ['Since 2023', 2023]], () => filters.year_min, v => { filters.year_min = v; paintSummary(); }),
    chipGroup('Citations', [['Any', 0], ['≥ 10', 10], ['≥ 50', 50]], () => filters.min_citations, v => { filters.min_citations = v; paintSummary(); }));
  const filterToggle = h('button', {
    className: 'filter-toggle', type: 'button', 'aria-expanded': 'false',
    onClick: e => {
      e.stopPropagation();
      filterPop.hidden = !filterPop.hidden;
      filterToggle.setAttribute('aria-expanded', String(!filterPop.hidden));
    },
  }, icon('tune'), filterSummary, icon('expand_more', 'filter-toggle__chev'));
  const closePop = e => { if (!filterPop.hidden && !filterPop.contains(e.target)) { filterPop.hidden = true; filterToggle.setAttribute('aria-expanded', 'false'); } };
  document.addEventListener('click', closePop);
  paintSummary();
  const filterPanel = h('div', { className: 'search-card__filters' }, h('div', { className: 'filter-anchor' }, filterToggle, filterPop), oaChip);

  // ── PDFs (my papers): same card size as search; files live in a popover ──
  const fileInput = h('input', { type: 'file', multiple: true, accept: 'application/pdf,.pdf', hidden: true, onChange: e => { addFiles(e.target.files); e.target.value = ''; } });
  const fileList = h('ul', { className: 'file-list' });
  const pdfLabel = h('span', { className: 'filter-toggle__text' });
  const pdfPop = h('div', { className: 'filter-pop pdf-pop', hidden: true, role: 'dialog', 'aria-label': 'Your PDFs' },
    fileList,
    h('button', { className: 'btn btn-outline btn-sm pdf-pop__add', type: 'button', onClick: () => fileInput.click() }, icon('add'), 'Add more PDFs'),
    h('p', { className: 'pdf-pop__hint' }, `Up to ${MAX_PDFS} PDFs, 25 MB each. Text PDFs only; scans need OCR first.`));
  const pdfToggle = h('button', {
    className: 'filter-toggle pdf-toggle', type: 'button', 'aria-expanded': 'false',
    onClick: e => {
      e.stopPropagation();
      if (!files.length) { fileInput.click(); return; }
      pdfPop.hidden = !pdfPop.hidden;
      pdfToggle.setAttribute('aria-expanded', String(!pdfPop.hidden));
    },
  }, icon('upload_file'), pdfLabel, icon('expand_more', 'filter-toggle__chev'));
  const closePdfPop = e => { if (!pdfPop.hidden && !pdfPop.contains(e.target) && !pdfToggle.contains(e.target)) { pdfPop.hidden = true; pdfToggle.setAttribute('aria-expanded', 'false'); } };
  document.addEventListener('click', closePdfPop);

  const relatedToggle = h('input', { type: 'checkbox', id: 'include-related', onChange: e => { includeRelated = e.target.checked; relatedChip.classList.toggle('active', includeRelated); } });
  const relatedChip = h('label', { className: 'chip related-chip', for: 'include-related', title: 'Also search the literature for papers close to yours and map them together' },
    relatedToggle, icon('hub'), 'Add related papers');
  const papersPanel = h('div', { className: 'search-card__filters' }, h('div', { className: 'filter-anchor' }, pdfToggle, pdfPop), relatedChip, fileInput);

  // ── Suggestions ──
  const suggestions = h('div', { className: 'recent-line' });

  const hint = h('span', { className: 'search-card__hint' });
  const fieldIcon = h('span', { className: 'search-card__icon-wrap' }, icon('search', 'search-card__icon'));
  const form = h('form', { className: 'search-card', onSubmit: e => { e.preventDefault(); start(); } },
    h('div', { className: 'search-card__head' }, modeSwitch, hint),
    h('div', { className: 'search-card__field' }, fieldIcon, topic),
    h('div', { className: 'search-card__foot' }, filterPanel, papersPanel, submit),
    h('div', { className: 'search-card__drop', 'aria-hidden': 'true' }, icon('upload_file'), 'Drop PDFs to add them'));

  // Drop PDFs anywhere on the card (switches to My papers).
  let dragDepth = 0;
  form.addEventListener('dragenter', e => { if (job || !e.dataTransfer?.types?.includes('Files')) return; e.preventDefault(); dragDepth++; form.classList.add('is-dragover'); });
  form.addEventListener('dragover', e => { if (e.dataTransfer?.types?.includes('Files')) e.preventDefault(); });
  form.addEventListener('dragleave', () => { dragDepth = Math.max(0, dragDepth - 1); if (!dragDepth) form.classList.remove('is-dragover'); });
  form.addEventListener('drop', e => {
    e.preventDefault(); dragDepth = 0; form.classList.remove('is-dragover');
    if (job || !e.dataTransfer?.files?.length) return;
    if (mode !== 'papers') { mode = 'papers'; renderMode(); }
    addFiles(e.dataTransfer.files);
  });

  const setup = h('div', { className: 'search-setup' }, hero, form, suggestions, instrumentPanel());
  inner.appendChild(setup);

  // ── Progress ──
  const progress = h('section', { className: 'build', hidden: true, 'aria-live': 'polite' });
  inner.appendChild(progress);

  function addFiles(list) {
    const notes = [];
    for (const f of Array.from(list || [])) {
      const isPdf = f.type === 'application/pdf' || /\.pdf$/i.test(f.name);
      if (!isPdf) { notes.push(`${f.name} isn’t a PDF`); continue; }
      if (f.size > MAX_PDF_BYTES) { notes.push(`${f.name} is over 25 MB`); continue; }
      if (files.some(x => x.name === f.name && x.size === f.size)) continue;
      if (files.length >= MAX_PDFS) { notes.push(`only ${MAX_PDFS} PDFs per map`); break; }
      files.push(f);
    }
    const total = files.reduce((s, f) => s + f.size, 0);
    if (total > MAX_TOTAL_BYTES) notes.push('these PDFs add up to more than 120 MB; remove some');
    if (notes.length) toast(`Skipped: ${[...new Set(notes)].join('; ')}.`, 5000);
    renderFiles();
    updateSubmit();
  }

  function renderFiles() {
    fileList.replaceChildren(...files.map((f, i) => h('li', { className: 'file-item' },
      icon('picture_as_pdf', 'file-item__icon'),
      h('span', { className: 'file-item__name', title: f.name }, f.name),
      h('span', { className: 'file-item__size' }, formatBytes(f.size)),
      h('button', {
        className: 'icon-btn', type: 'button', 'aria-label': `Remove ${f.name}`,
        onClick: e => { e.stopPropagation(); files.splice(i, 1); renderFiles(); updateSubmit(); },
      }, icon('close')))));
    const total = files.reduce((s, f) => s + f.size, 0);
    pdfLabel.textContent = files.length ? `${files.length} PDF${files.length === 1 ? '' : 's'} · ${formatBytes(total)}` : 'Add PDFs';
    pdfToggle.classList.toggle('has-files', files.length > 0);
    if (!files.length) { pdfPop.hidden = true; pdfToggle.setAttribute('aria-expanded', 'false'); }
  }

  function renderSuggestions() {
    const recent = getRecentMaps().slice(0, 3);
    suggestions.hidden = !recent.length;
    suggestions.replaceChildren(...(recent.length ? [
      h('span', { className: 'recent-line__label' }, icon('history'), 'Recent'),
      ...recent.map(m => h('a', { className: 'recent-chip', href: `#/map/${m.id}`, title: `${m.topic} · ${m.papers} papers · ${timeAgo(m.at)}` },
        m.source === 'upload' ? icon('lock') : null, h('span', {}, m.topic))),
    ] : []));
  }

  function renderMode() {
    const lit = mode === 'literature';
    modeSwitch.dataset.mode = mode;
    modeBtns.forEach(b => {
      const on = b.dataset.mode === mode;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', String(on));
    });
    heroTitle.replaceChildren(...(lit ? ['Map the ', h('em', {}, 'literature'), '.'] : ['Map your own ', h('em', {}, 'papers'), '.']));
    heroSub.textContent = lit
      ? 'Give a topic. Ereuna finds the papers, reads them and maps what they found — every claim with its source.'
      : 'Upload the PDFs you’re working with. Ereuna identifies, reads and maps them together.';
    const narrow = window.matchMedia('(max-width: 720px)').matches;
    topic.placeholder = lit ? (narrow ? 'Describe a research topic' : 'e.g. Low-leakage SRAM using FinFET') : 'What are these papers about?';
    filterPanel.hidden = !lit;
    papersPanel.hidden = lit;
    fieldIcon.replaceChildren(icon(lit ? 'search' : 'description', 'search-card__icon'));
    hint.replaceChildren(...(lit
      ? [h('kbd', {}, '/'), ' to focus · ', h('kbd', {}, 'Enter'), ' to build']
      : [icon('lock'), ' Read, then discarded · map visible only to you']));
    form.classList.toggle('search-card--papers', !lit);
    setup.classList.toggle('search-setup--papers', !lit);
    const lead = setup.querySelector('.instrument__lead');
    if (lead) lead.textContent = lit
      ? 'Every blip on your map is a claim you can trace back to its sentence.'
      : 'Your PDFs are read, checked and mapped, and the map is private to you.';
    renderSuggestions();
    updateSubmit();
  }

  function updateSubmit() {
    const lit = mode === 'literature';
    const tooBig = files.reduce((s, f) => s + f.size, 0) > MAX_TOTAL_BYTES;
    submit.disabled = !!job || !topic.value.trim() || (!lit && (!files.length || tooBig));
    submit.replaceChildren(icon(lit ? 'explore' : 'hub'), h('span', {}, lit ? 'Build map' : `Map ${files.length || ''} PDF${files.length === 1 ? '' : 's'}`.replace('  ', ' ')));
  }

  // ── Running a job ──
  function showProgress(stages, label) {
    const started = Date.now();
    const items = stages.map(([name, text]) => h('li', { className: 'build-step', 'data-stage': name },
      h('span', { className: 'build-step__dot', 'aria-hidden': 'true' }), h('span', {}, text)));
    const bar = h('div', { className: 'progress-bar__fill', style: { width: '2%' } });
    const elapsed = h('span', { className: 'build__elapsed' }, '0:00');
    const status = h('div', { className: 'build__status' }, 'Starting…');
    const counters = h('div', { className: 'build__counters' });
    const papersList = h('ol', { className: 'build__papers' });
    const log = h('div', { className: 'progress-log', role: 'log' });
    const cancel = h('button', { className: 'btn btn-ghost btn-sm', type: 'button', onClick: stop }, 'Stop');

    progress.replaceChildren(
      h('div', { className: 'build__head' },
        h('div', {}, h('div', { className: 'build__kicker' }, 'Building map'), h('h2', { className: 'build__topic' }, label)),
        h('div', { className: 'build__head-right' }, elapsed, cancel)),
      h('div', { className: 'progress-bar' }, bar),
      status,
      h('div', { className: 'build__grid' },
        h('ol', { className: 'build-steps' }, ...items),
        h('div', { className: 'build__side' }, counters, papersList)),
      h('details', { className: 'build__log' }, h('summary', {}, 'Details'), log));
    progress.hidden = false;
    setup.classList.add('is-running');
    timer = setInterval(() => {
      const s = Math.floor((Date.now() - started) / 1000);
      elapsed.textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    }, 1000);

    let selected = 0;
    return {
      stage(name, fraction) {
        const idx = stages.findIndex(([n]) => n === name);
        items.forEach((li, i) => {
          li.classList.toggle('done', idx >= 0 && i < idx);
          li.classList.toggle('current', i === idx);
        });
        if (idx >= 0) status.textContent = `Step ${idx + 1} of ${stages.length} · ${stages[idx][1]}`;
        bar.style.width = `${Math.round((fraction || 0) * 100)}%`;
      },
      log(message) {
        const s = Math.floor((Date.now() - started) / 1000);
        log.appendChild(h('div', { className: 'progress-log__line' },
          h('span', { className: 'progress-log__time' }, `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`), h('span', {}, message)));
        log.scrollTop = log.scrollHeight;
      },
      paper(p) {
        selected += 1;
        counters.textContent = `${selected} paper${selected === 1 ? '' : 's'} selected`;
        papersList.appendChild(h('li', { className: 'build__paper' },
          h('span', { className: 'build__paper-title' }, p.title),
          h('span', { className: 'build__paper-meta' }, [p.year || null, p.citations ? `${p.citations} cit.` : null].filter(Boolean).join(' · '))));
      },
      done() {
        items.forEach(li => { li.classList.remove('current'); li.classList.add('done'); });
        bar.style.width = '100%';
        status.textContent = 'Done. Opening your map…';
      },
      fail(message) {
        status.textContent = '';
        progress.appendChild(h('div', { className: 'build__error', role: 'alert' }, icon('error'),
          h('div', {}, h('strong', {}, 'The map couldn’t be built. '), message),
          h('button', { className: 'btn btn-outline btn-sm', type: 'button', onClick: reset }, 'Try again')));
        cancel.hidden = true;
      },
    };
  }

  function stop() {
    if (job) job.close();
    toast('Stopped watching. The server may still finish this map.');
    reset();
  }

  function reset() {
    job = null;
    clearInterval(timer);
    progress.hidden = true;
    progress.replaceChildren();
    setup.classList.remove('is-running');
    topic.disabled = false;
    updateSubmit();
  }

  async function start() {
    const text = topic.value.trim();
    if (!text) { topic.focus(); return; }
    const lit = mode === 'literature';
    topic.disabled = true;
    job = { close() {} };
    updateSubmit();
    const stages = lit ? LITERATURE_STAGES : UPLOAD_STAGES.filter(([n]) => includeRelated || !['find_related', 'fetch_related'].includes(n));
    const view = showProgress(stages, text);
    view.log(lit ? `Topic: “${text}”` : `Uploading ${files.length} PDF${files.length === 1 ? '' : 's'}…`);

    try {
      const body = lit
        ? await createMap(text, { ...filters, year_min: filters.year_min || undefined })
        : await createMapFromUploads(text, files, includeRelated);
      if (body.status === 'cached' || !body.job_id) {
        view.done();
        navigate(`/map/${body.map_id}`);
        return;
      }
      job = subscribeToJob(body.job_id, (event, data) => {
        if (event === 'stage') view.stage(data.name, data.progress);
        else if (event === 'log') view.log(data.message);
        else if (event === 'paper_selected') view.paper(data);
        else if (event === 'done') {
          view.done();
          clearInterval(timer);
          setTimeout(() => navigate(`/map/${data.map_id}`), 500);
        } else if (event === 'error') {
          clearInterval(timer);
          view.fail(data.message || 'Something went wrong on the server.');
        }
      });
    } catch (err) {
      clearInterval(timer);
      view.fail(err.message);
    }
  }

  renderMode();
  renderFiles();
  setTimeout(() => topic.focus(), 30);

  const onKey = e => {
    if (e.key === '/' && document.activeElement !== topic && !/INPUT|TEXTAREA/.test(document.activeElement?.tagName || '')) {
      e.preventDefault();
      topic.focus();
    }
  };
  document.addEventListener('keydown', onKey);

  return () => {
    document.removeEventListener('keydown', onKey);
    document.removeEventListener('click', closePop);
    document.removeEventListener('click', closePdfPop);
    if (job) job.close();
    clearInterval(timer);
  };
}

function timeAgo(ms) {
  const m = Math.round((Date.now() - ms) / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const hrs = Math.round(m / 60);
  if (hrs < 24) return `${hrs} h ago`;
  const d = Math.round(hrs / 24);
  return d === 1 ? 'yesterday' : `${d} days ago`;
}

/** Dark panel: a radar whose blips are the things a map holds, and how a map is built. */
function instrumentPanel() {
  const steps = [
    ['Searches', 'OpenAlex, Semantic Scholar and arXiv at once'],
    ['Reads', 'open-access full text, not just abstracts'],
    ['Checks', 'every quote against the paper’s own text'],
    ['Maps', 'findings, conflicts, gaps and citations'],
  ];
  return h('aside', { className: 'instrument' },
    h('div', { className: 'instrument__radar', innerHTML: wordRadarSvg() }),
    h('div', { className: 'instrument__body' },
      h('div', { className: 'instrument__kicker' }, 'How a map is built'),
      h('p', { className: 'instrument__lead' }, 'Every blip on your map is a claim you can trace back to its sentence.'),
      h('ol', { className: 'instrument__steps' }, ...steps.map(([verb, rest], i) => h('li', { style: { '--i': i } },
        h('span', { className: 'instrument__num' }, String(i + 1).padStart(2, '0')),
        h('span', {}, h('strong', {}, verb), ' ', rest)))),
      h('div', { className: 'instrument__foot' },
        h('span', {}, h('b', {}, '~2 min'), ' per map'),
        h('span', {}, h('b', {}, '6'), ' open sources'),
        h('span', {}, h('b', {}, '1 quote'), ' behind every claim'))));
}

/** Radar with labelled contacts. The sweep turns clockwise once every PERIOD seconds;
 *  each word flashes as the beam passes its bearing. */
function wordRadarSvg() {
  const PERIOD = 6;
  const cx = 150, cy = 120, R = 96;
  const contacts = [
    ['PAPERS', 18, 0.72, 'hot'], ['FINDINGS', 78, 0.5, ''], ['CONFLICTS', 138, 0.8, 'warn'],
    ['GAPS', 200, 0.62, 'gap'], ['METRICS', 258, 0.42, ''], ['CITATIONS', 318, 0.86, ''],
  ];
  const rad = d => (d * Math.PI) / 180;
  const f = n => n.toFixed(1);
  const ticks = Array.from({ length: 60 }, (_, i) => {
    const a = rad(i * 6), long = i % 5 === 0;
    return `<line x1="${f(cx + Math.cos(a) * R)}" y1="${f(cy + Math.sin(a) * R)}" x2="${f(cx + Math.cos(a) * (R + (long ? 7 : 4)))}" y2="${f(cy + Math.sin(a) * (R + (long ? 7 : 4)))}" class="wr-tick ${long ? 'wr-tick--long' : ''}"/>`;
  }).join('');
  const blips = contacts.map(([word, deg, dist, kind]) => {
    const a = rad(deg), x = cx + Math.cos(a) * R * dist, y = cy + Math.sin(a) * R * dist;
    const right = Math.cos(a) >= -0.2;
    const lx = x + (right ? 9 : -9), ly = y + 3.5;
    const delay = ((deg / 360) * PERIOD - PERIOD).toFixed(2);
    return `<g class="wr-contact wr-contact--${kind || 'plain'}" style="animation-delay:${delay}s">
      <circle cx="${f(x)}" cy="${f(y)}" r="9" class="wr-halo"/>
      <circle cx="${f(x)}" cy="${f(y)}" r="3.4" class="wr-dot"/>
      <text x="${f(lx)}" y="${f(ly)}" text-anchor="${right ? 'start' : 'end'}" class="wr-word">${word}</text></g>`;
  }).join('');
  const trail = 42;
  const tx = cx + Math.cos(rad(-trail)) * R, ty = cy + Math.sin(rad(-trail)) * R;
  return `<svg viewBox="0 0 300 240" class="word-radar" role="img" aria-label="Radar showing papers, findings, conflicts, gaps, metrics and citations">
    <circle cx="${cx}" cy="${cy}" r="${R}" class="wr-ring wr-ring--outer"/>
    <circle cx="${cx}" cy="${cy}" r="${R * 0.66}" class="wr-ring"/>
    <circle cx="${cx}" cy="${cy}" r="${R * 0.33}" class="wr-ring wr-ring--dash"/>
    <line x1="${cx - R}" y1="${cy}" x2="${cx + R}" y2="${cy}" class="wr-axis"/>
    <line x1="${cx}" y1="${cy - R}" x2="${cx}" y2="${cy + R}" class="wr-axis"/>
    ${ticks}
    <g class="wr-sweep" style="animation-duration:${PERIOD}s">
      <path d="M${cx} ${cy} L${cx + R} ${cy} A${R} ${R} 0 0 0 ${f(tx)} ${f(ty)} Z" class="wr-wedge"/>
      <line x1="${cx}" y1="${cy}" x2="${cx + R}" y2="${cy}" class="wr-beam"/>
    </g>
    ${blips.replaceAll('animation-delay', `animation-duration:${PERIOD}s;animation-delay`)}
    <circle cx="${cx}" cy="${cy}" r="4" class="wr-core"/>
    <text x="${cx}" y="${cy + 16}" text-anchor="middle" class="wr-centre">YOUR TOPIC</text>
  </svg>`;
}

/** Faint radar rings behind the hero. */
function backdropSvg() {
  const rings = [120, 220, 320, 420, 520].map((r, i) => `<circle cx="600" cy="300" r="${r}" class="bd-ring ${i % 2 ? 'bd-ring--dash' : ''}"/>`).join('');
  const ticks = Array.from({ length: 72 }, (_, i) => {
    const a = (i / 72) * Math.PI * 2, r1 = 520, r2 = i % 6 ? 528 : 540;
    return `<line x1="${(600 + Math.cos(a) * r1).toFixed(1)}" y1="${(300 + Math.sin(a) * r1).toFixed(1)}" x2="${(600 + Math.cos(a) * r2).toFixed(1)}" y2="${(300 + Math.sin(a) * r2).toFixed(1)}" class="bd-tick"/>`;
  }).join('');
  return `<svg viewBox="0 0 1200 600" preserveAspectRatio="xMidYMin slice">
    <defs><radialGradient id="bd-glow" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="#C94B1F" stop-opacity=".16"/><stop offset="1" stop-color="#C94B1F" stop-opacity="0"/></radialGradient>
    <linearGradient id="bd-fade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset=".75" stop-color="#fff"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    <mask id="bd-mask"><rect width="1200" height="600" fill="url(#bd-fade)"/></mask></defs>
    <g mask="url(#bd-mask)">
      <circle cx="600" cy="300" r="420" fill="url(#bd-glow)"/>
      ${rings}${ticks}
      <line x1="80" y1="300" x2="1120" y2="300" class="bd-axis"/><line x1="600" y1="0" x2="600" y2="600" class="bd-axis"/>
      <g class="bd-sweep"><path d="M600 300 L1120 300 A520 520 0 0 0 1050.3 40 Z" class="bd-wedge"/></g>
    </g>
  </svg>`;
}
