/**
 * Public landing page (signed-out visitors).
 * Editorial, journal-like layout: serif display type, mono metadata, hairlines.
 */
import { h, icon } from '../utils.js';
import { navigate } from '../router.js';
import { getTheme, toggleTheme } from '../theme.js';
import { brandMark } from '../components/brand.js';
import { mountHeroRadar } from '../components/heroRadar.js';
import { mountDemoMap } from '../components/demoMap.js';
import {
  problemIllustration, evidenceIllustration, constellationIllustration, revealOnScroll,
} from '../components/illustrations.js';
import { siteFooter } from '../components/footer.js';

function scrollToId(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function eyebrow(text, { dot = false, center = false } = {}) {
  return h('div', { className: `editorial-eyebrow ${center ? 'justify-center' : ''}` },
    h('span', { className: dot ? 'editorial-eyebrow__dot' : 'editorial-eyebrow__dash' }), text);
}

function illustration(markup, cls) {
  const el = h('div', { className: `ill-frame ${cls}`, 'data-reveal': '' });
  el.innerHTML = markup;
  return el;
}

export function renderLanding(container) {
  const cleanups = [];
  const goSignUp = () => navigate('/signup');
  const goSignIn = () => navigate('/signin');

  const page = h('div', { className: 'landing-page' });

  // ── Nav ───────────────────────────────────────────────
  const themeBtn = h('button', {
    className: 'editorial-nav__theme-btn', type: 'button', 'aria-label': 'Switch light or dark mode',
    onClick: () => {
      const next = toggleTheme();
      themeBtn.replaceChildren(icon(next === 'dark' ? 'light_mode' : 'dark_mode', 'editorial-nav__theme-icon'));
    },
  }, icon(getTheme() === 'dark' ? 'light_mode' : 'dark_mode', 'editorial-nav__theme-icon'));

  const nav = h('nav', { className: 'editorial-nav', 'aria-label': 'Main' },
    h('div', { className: 'editorial-nav__inner' },
      h('a', {
        className: 'editorial-nav__brand', href: '#/', 'aria-label': 'Ereuna home',
        onClick: e => { e.preventDefault(); window.scrollTo({ top: 0, behavior: 'smooth' }); },
      }, brandMark(), h('span', { className: 'editorial-nav__brand-text' },
        h('span', { className: 'editorial-nav__brand-name' }, 'EREUNA'),
        h('span', { className: 'editorial-nav__brand-tagline' }, 'Research, on your radar.'))),
      h('div', { className: 'editorial-nav__right' },
        themeBtn,
        h('button', { className: 'editorial-nav__btn-signin', type: 'button', onClick: goSignIn }, 'Sign in'),
        h('button', { className: 'editorial-nav__btn-signup', type: 'button', onClick: goSignUp }, 'Get started')),
    ));

  page.appendChild(nav);

  const onScroll = () => nav.classList.toggle('scrolled', window.scrollY > 24);
  window.addEventListener('scroll', onScroll, { passive: true });
  cleanups.push(() => window.removeEventListener('scroll', onScroll));
  onScroll();

  // ── Hero ──────────────────────────────────────────────
  const radarWrap = h('div', { className: 'radar-interactive-wrapper' });
  page.appendChild(h('header', { className: 'editorial-hero', id: 'hero' },
    h('div', { className: 'editorial-hero__inner' },
      h('div', { className: 'editorial-hero__left' },
        eyebrow('For researchers, engineers and students', { dot: true }),
        h('h1', { className: 'editorial-hero__headline' }, 'Every claim,', h('br'), 'traced to its source.'),
        h('p', { className: 'editorial-hero__body' },
          'Give Ereuna a research topic, or your own PDFs. It reads the literature and builds a map of the papers, findings, author-stated gaps and disagreements, with the sentence behind every item one click away.'),
        h('div', { className: 'editorial-hero__ctas' },
          h('button', { className: 'btn-editorial-primary', type: 'button', onClick: goSignUp },
            'Create free account', icon('arrow_forward', 'btn-icon-right')),
          h('button', { className: 'btn-editorial-secondary', type: 'button', onClick: () => scrollToId('research-map') },
            'See an example map')),
        h('dl', { className: 'editorial-hero__metrics' },
          ...[
            ['250M+', 'works searchable through OpenAlex'],
            ['6', 'open scholarly sources'],
            ['1 quote', 'behind every finding, checked against the paper'],
          ].map(([v, l]) => h('div', { className: 'editorial-metric' },
            h('dt', { className: 'editorial-metric__value' }, v),
            h('dd', { className: 'editorial-metric__label' }, l)))),
      ),
      h('div', { className: 'editorial-hero__right' },
        radarWrap,
        h('p', { className: 'radar-caption' }, 'Hover or tap a blip to see the sentence it came from.')),
    )));

  // ── Problem ───────────────────────────────────────────
  page.appendChild(h('section', { className: 'editorial-section editorial-problem', id: 'problem' },
    h('div', { className: 'editorial-problem__inner' },
      h('div', { className: 'editorial-problem__content' },
        eyebrow('The problem'),
        h('h2', { className: 'editorial-section__headline' }, 'The literature is connected.', h('br'), 'Finding the connections isn’t.'),
        h('p', { className: 'editorial-section__body' },
          'The information is out there. It is spread across databases, preprint servers, publisher PDFs and conference proceedings, each with its own terms and search box.'),
        h('ul', { className: 'editorial-points-list' },
          ...[
            ['Scattered sources', 'Preprints, journals and proceedings live in separate indexes.'],
            ['Buried limitations', 'What authors say is still unsolved sits in a paragraph near the end of the PDF.'],
            ['Unsourced summaries', 'An AI summary without the original sentence gives you nothing to check.'],
          ].map(([t, d]) => h('li', { className: 'editorial-point' },
            h('span', { className: 'editorial-point__icon', 'aria-hidden': 'true' }, '—'),
            h('div', {}, h('strong', { className: 'editorial-point__title' }, `${t}. `), h('span', { className: 'editorial-point__desc' }, d))))),
      ),
      h('div', { className: 'editorial-problem__visual' }, illustration(problemIllustration(), 'ill-frame--problem')),
    )));

  // ── What you get ──────────────────────────────────────
  page.appendChild(h('section', { className: 'editorial-section editorial-solution', id: 'solution' },
    h('div', { className: 'editorial-container text-center' },
      eyebrow('One research space', { dot: true, center: true }),
      h('h2', { className: 'editorial-section__headline editorial-headline--centered' }, 'From a question to a map of the literature.'),
      h('p', { className: 'editorial-section__body editorial-body--centered' },
        'Ereuna turns scattered papers into one place you can navigate, connecting papers, findings, gaps, citations and the passages they come from.'),
      h('div', { className: 'capabilities-track', 'data-reveal': '' },
        h('div', { className: 'capabilities-line' }),
        h('div', { className: 'capabilities-grid' },
          ...[
            ['01', 'Discover', 'Search several open indexes with one question, or start from the PDFs you already have.', 'OpenAlex · Semantic Scholar · arXiv'],
            ['02', 'Connect', 'See how papers cite each other, which results agree, and where values disagree.', 'Citation graph · comparison table'],
            ['03', 'Verify', 'Open any finding and read the exact sentence it came from, with section and page.', 'Quotes checked against the paper text'],
          ].map(([n, t, d, m], i) => h('div', { className: 'capability-col', style: { '--i': i } },
            h('div', { className: 'capability-node' }, h('div', { className: 'capability-node__inner' })),
            h('div', { className: 'capability-number' }, n),
            h('h3', { className: 'capability-title' }, t),
            h('p', { className: 'capability-desc' }, d),
            h('div', { className: 'capability-meta' }, m))))),
    )));

  // ── Demo map ──────────────────────────────────────────
  const demo = h('div', { className: 'editorial-map-demo' });
  page.appendChild(h('section', { className: 'editorial-section editorial-map-section', id: 'research-map' },
    h('div', { className: 'editorial-container' },
      eyebrow('Example research map'),
      h('h2', { className: 'editorial-section__headline' }, 'See the landscape before you read every paper.'),
      h('p', { className: 'editorial-section__body' },
        'A small slice of a real map on attention in transformers. Click a node to read the sentence it is built on, and follow its connections.'),
      demo)));

  // ── Evidence ──────────────────────────────────────────
  page.appendChild(h('section', { className: 'editorial-section editorial-evidence', id: 'evidence' },
    h('div', { className: 'editorial-evidence__inner' },
      h('div', { className: 'editorial-evidence__visual' }, illustration(evidenceIllustration(), 'ill-frame--evidence')),
      h('div', { className: 'editorial-evidence__content' },
        eyebrow('Traceable evidence'),
        h('h2', { className: 'editorial-section__headline' }, 'Don’t just find the claim.', h('br'), 'Find where it came from.'),
        h('p', { className: 'editorial-section__body' },
          'Every finding, metric and gap keeps the quote it was drawn from, with the paper, section and page. Quotes are matched against the paper’s own text, and anything that can’t be found there is dropped.'),
        h('figure', { className: 'evidence-callout' },
          h('figcaption', { className: 'evidence-callout__header' }, icon('verified', 'text-accent'),
            h('span', { className: 'evidence-callout__badge' }, 'Source sentence · §4 Why Self-Attention')),
          h('blockquote', { className: 'evidence-callout__quote' },
            '“A self-attention layer connects all positions with a constant number of sequentially executed operations, whereas a recurrent layer requires O(n) sequential operations.”'),
          h('div', { className: 'evidence-callout__source' },
            h('span', { className: 'evidence-callout__paper' }, 'Attention Is All You Need'),
            h('span', { className: 'evidence-callout__authors' }, 'Vaswani, Shazeer, Parmar, Uszkoreit et al. · NeurIPS 2017'),
            h('a', { className: 'evidence-callout__doi', href: 'https://doi.org/10.48550/arXiv.1706.03762', target: '_blank', rel: 'noopener' }, 'doi:10.48550/arXiv.1706.03762'))),
      ))));

  // ── Workflow ──────────────────────────────────────────
  page.appendChild(h('section', { className: 'editorial-section editorial-workflow', id: 'workflow' },
    h('div', { className: 'editorial-container' },
      eyebrow('How it works', { dot: true, center: true }),
      h('h2', { className: 'editorial-section__headline editorial-headline--centered' }, 'Two ways in. One map out.'),
      h('p', { className: 'editorial-section__body editorial-body--centered' },
        'Start from a topic to survey the field, or from your own PDFs to organise what you have already collected. Either way a map takes a minute or two.'),
      h('div', { className: 'modes-grid', 'data-reveal': '' },
        h('div', { className: 'mode-card' },
          h('div', { className: 'mode-card__icon' }, icon('travel_explore')),
          h('div', { className: 'mode-card__kicker' }, 'Mode 1'),
          h('h3', { className: 'mode-card__title' }, 'Search the literature'),
          h('p', { className: 'mode-card__body' }, 'Type a topic. Ereuna searches open indexes, ranks what it finds and reads the open-access full text where it exists.'),
          h('div', { className: 'mode-card__example' }, 'e.g. “solid-state battery electrolyte degradation”')),
        h('div', { className: 'mode-card' },
          h('div', { className: 'mode-card__icon' }, icon('upload_file')),
          h('div', { className: 'mode-card__kicker' }, 'Mode 2'),
          h('h3', { className: 'mode-card__title' }, 'Map your own papers'),
          h('p', { className: 'mode-card__body' }, 'Upload up to 15 PDFs. They are read, matched to their published records, and mapped. The map is visible only to you.'),
          h('div', { className: 'mode-card__example' }, 'Optionally add related papers from the literature'))),
      h('ol', { className: 'workflow-pipeline', 'data-reveal': '' },
        h('div', { className: 'workflow-pipeline__line', 'aria-hidden': 'true' }),
        h('div', { className: 'workflow-pipeline__grid' },
          ...[
            ['01', 'Ask', 'Start with a question, a topic, or a folder of PDFs.', 'The query is expanded into the terms papers actually use.'],
            ['02', 'Gather', 'Candidates are collected, de-duplicated and ranked for relevance.', 'OpenAlex, Semantic Scholar and arXiv, with full text from arXiv, Europe PMC and Unpaywall.'],
            ['03', 'Read', 'Each paper is read for its problem, method, metrics, findings and limitations.', 'Every item must quote the paper, or it is dropped.'],
            ['04', 'Map', 'Results are compared across papers: agreements, conflicts, gaps, citations.', 'Export to Markdown, BibTeX or CSV.'],
          ].map(([n, t, d, sub], i) => h('li', { className: 'workflow-step', style: { '--i': i } },
            h('div', { className: 'workflow-step__node' }, h('div', { className: 'workflow-step__pulse' })),
            h('div', { className: 'workflow-step__number' }, n),
            h('h3', { className: 'workflow-step__title' }, t),
            h('p', { className: 'workflow-step__desc' }, d),
            h('div', { className: 'workflow-step__sub' }, sub))))),
    )));

  // ── Sources ───────────────────────────────────────────
  const sources = [
    ['01', 'OpenAlex', 'openalex.org', 'An open catalogue of over 250 million scholarly works. Used for search, metadata, citation counts and reference lists.', ['Search', 'Citations', 'References']],
    ['02', 'Semantic Scholar', 'semanticscholar.org', 'The Allen Institute for AI’s paper index. Used for relevance search, abstracts and matching uploaded PDFs to their records.', ['Search', 'Abstracts', 'Title matching']],
    ['03', 'arXiv', 'arxiv.org', 'The open preprint server for physics, mathematics, computer science and more. Used for recent preprints and their full-text PDFs.', ['Preprints', 'Full text']],
    ['04', 'Crossref, Unpaywall & Europe PMC', 'crossref.org', 'DOI metadata, legal open-access copies of published papers, and open full text for biomedical articles.', ['DOI metadata', 'Open-access PDFs', 'Biomedical full text']],
  ];
  page.appendChild(h('section', { className: 'editorial-section editorial-opendata', id: 'open-data' },
    h('div', { className: 'editorial-container' },
      eyebrow('Open research data'),
      h('h2', { className: 'editorial-section__headline' }, 'Built on open scholarly infrastructure.'),
      h('p', { className: 'editorial-section__body' },
        'Ereuna reads only openly available metadata and legally open-access full text. Paywalled papers are included from their abstracts and marked as such.'),
      h('div', { className: 'opendata-sources-list' },
        ...sources.map(([i, name, host, desc, tags]) => h('div', { className: 'opendata-source-row', 'data-reveal': '' },
          h('div', { className: 'opendata-source-row__left' },
            h('div', { className: 'opendata-source-index' }, i),
            h('a', { className: 'opendata-source-name', href: `https://${host}`, target: '_blank', rel: 'noopener' }, name)),
          h('div', { className: 'opendata-source-row__right' },
            h('p', { className: 'opendata-source-desc' }, desc),
            h('div', { className: 'opendata-source-specs' }, ...tags.map(t => h('span', { className: 'opendata-spec-tag' }, t))))))),
    )));

  // ── About ─────────────────────────────────────────────
  page.appendChild(h('section', { className: 'editorial-section editorial-about', id: 'about' },
    h('div', { className: 'editorial-about__inner' },
      h('div', { className: 'editorial-about__content' },
        eyebrow('Why Ereuna'),
        h('h2', { className: 'editorial-section__headline' }, 'Research should feel like exploration,', h('br'), 'not excavation.'),
        h('p', { className: 'editorial-section__body' },
          'Ereuna (ἔρευνα) is Greek for research, an inquiry or search. It is built on one idea: you should spend your time thinking about evidence, not hunting for it.'),
        h('ul', { className: 'about-principles' },
          ...[
            ['Show the source.', 'Nothing appears without the sentence it came from.'],
            ['Say what’s missing.', 'Abstract-only papers, skipped PDFs and unmatched records are labelled, not hidden.'],
            ['Don’t pick winners.', 'Values are shown as reported, with their conditions. Whether higher is better depends on the metric.'],
          ].map(([t, d]) => h('li', {}, h('strong', {}, t), ' ', d))),
      ),
      h('div', { className: 'editorial-about__visual' }, illustration(constellationIllustration(), 'ill-frame--about')),
    )));

  // ── Final CTA ─────────────────────────────────────────
  page.appendChild(h('section', { className: 'editorial-final-cta', id: 'cta' },
    h('div', { className: 'cta-radar-watermark', 'aria-hidden': 'true' }),
    h('div', { className: 'editorial-final-cta__inner' },
      h('h2', { className: 'editorial-final-cta__headline' }, 'Put your research on the radar.'),
      h('p', { className: 'editorial-final-cta__body' }, 'Free to use. Build your first map in about two minutes.'),
      h('div', { className: 'editorial-final-cta__buttons' },
        h('button', { className: 'btn-editorial-primary', type: 'button', onClick: goSignUp }, 'Create free account', icon('arrow_forward', 'btn-icon-right')),
        h('button', { className: 'btn-editorial-secondary', type: 'button', onClick: goSignIn }, 'Sign in')))));

  page.appendChild(siteFooter({ onSection: scrollToId }));
  container.appendChild(page);

  cleanups.push(mountHeroRadar(radarWrap));
  cleanups.push(mountDemoMap(demo, { onCta: goSignUp }));
  cleanups.push(revealOnScroll(page));

  return () => cleanups.forEach(fn => fn && fn());
}
