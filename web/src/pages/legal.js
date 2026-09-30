/**
 * Public legal pages: privacy policy, terms of use, and how to delete an account.
 * The privacy policy and deletion page double as the URLs a Play Store listing needs.
 *
 * Keep these in step with what the backend actually does.
 */
import { h, icon } from '../utils.js';
import { isSignedIn } from '../api.js';
import { brandMark } from '../components/brand.js';
import { siteFooter } from '../components/footer.js';

const UPDATED = '30 September 2026';
const CONTACT = import.meta.env.VITE_CONTACT_EMAIL || '';

const contactLine = () => CONTACT
  ? ['Questions or requests: ', h('a', { href: `mailto:${CONTACT}` }, CONTACT), '.']
  : [];

const PAGES = {
  privacy: {
    title: 'Privacy policy',
    lead: 'What Ereuna collects, why, and who else sees it. In short: only what is needed to build your maps, nothing is sold, and you can delete everything.',
    sections: [
      ['What we collect', [
        ['Account: ', 'your name, email address and a scrypt hash of your password (never the password itself).'],
        ['Research topics ', 'you search for, and the filters you choose.'],
        ['PDFs you upload. ', 'They are held in memory while they are read and then discarded; they are never written to disk. The map built from them is stored with your account and visible only to you.'],
        ['Your library: ', 'the maps and papers you save.'],
        ['A daily count ', 'of maps you have created, to apply fair-use limits.'],
      ]],
      ['What we don’t collect', [
        ['', 'No advertising identifiers, no analytics or tracking scripts, no cookies, no location, no contacts.'],
        ['', 'We do not sell or share your data with advertisers or data brokers.'],
      ]],
      ['Who else processes data', [
        ['Scholarly databases. ', 'Search terms derived from your topic are sent to OpenAlex, Semantic Scholar, arXiv, Crossref, Unpaywall and Europe PMC to find papers. These requests do not include your name or email.'],
        ['AI model providers. ', 'To expand your topic and to extract findings, text from papers (including the text of PDFs you upload) is sent to a language-model API. We currently use Cerebras, Google Gemini and Groq. They receive the text, not your account details.'],
        ['Hosting. ', 'Accounts and maps are stored in a managed Postgres database (Neon) over encrypted connections.'],
        ['Fonts. ', 'The website loads its typefaces from Google Fonts, so Google receives your IP address when you visit. The Android app does not.'],
      ]],
      ['Shared topic maps', [
        ['', 'A map built from a topic search contains only public research metadata. It is cached and may be shown to other people who search the same topic with the same filters. It is never linked to your name. Maps built from uploaded PDFs are never shared.'],
      ]],
      ['Storage on your device', [
        ['', 'The website keeps your sign-in token, theme and recently opened maps in your browser’s local storage. The Android app keeps the same on your phone. Signing out removes them.'],
      ]],
      ['Keeping and deleting data', [
        ['', 'Your data is kept while your account exists. You can delete your account at any time from Settings; this removes your account, library, usage counts and every map built from your uploads, immediately and permanently.'],
      ]],
      ['Children', [
        ['', 'Ereuna is not directed at children under 13 and we do not knowingly collect their data.'],
      ]],
      ['Changes', [
        ['', 'If this policy changes, the date at the top will change. Significant changes will be announced in the app.'],
      ]],
    ],
  },
  terms: {
    title: 'Terms of use',
    lead: 'The ground rules for using Ereuna.',
    sections: [
      ['Using Ereuna', [
        ['', 'Ereuna is provided free of charge for research, study and engineering work. You need an account, and you are responsible for keeping your password safe.'],
        ['', 'Fair-use limits apply to how many maps can be created per day, so the service stays available for everyone.'],
      ]],
      ['Your uploads', [
        ['', 'Only upload PDFs you have the right to use. You keep all rights to them; we use them only to build your map, as described in the privacy policy.'],
      ]],
      ['Acceptable use', [
        ['', 'Do not use automated scripts to create maps in bulk, try to access other people’s maps, or interfere with the service or the open databases it relies on.'],
      ]],
      ['Accuracy', [
        ['', 'Ereuna helps you find and organise research; it does not replace reading it. Findings are drawn from paper text by automated extraction and every one shows its source sentence so you can check it. Always read the original paper before you cite or rely on a result.'],
        ['', 'Coverage depends on what the open databases index and on which papers have open-access full text.'],
      ]],
      ['Third-party content', [
        ['', 'Paper metadata and text belong to their authors and publishers and are shown under the terms of the sources they come from. Links to papers open the publisher’s or repository’s own site.'],
      ]],
      ['No warranty', [
        ['', 'The service is provided “as is”, without warranties of any kind. To the extent the law allows, we are not liable for losses arising from its use.'],
      ]],
      ['Ending use', [
        ['', 'You can stop using Ereuna and delete your account at any time. We may suspend accounts that break these terms.'],
      ]],
    ],
  },
  deletion: {
    title: 'Delete your Ereuna account',
    lead: 'You can delete your account yourself, at any time, from the website or the Android app.',
    sections: [
      ['On the website', [
        ['1. ', 'Sign in and open Settings.'],
        ['2. ', 'Under “Delete account”, enter your password and confirm.'],
      ]],
      ['In the Android app', [
        ['1. ', 'Open Settings from the bottom bar.'],
        ['2. ', 'Tap “Delete account” and confirm with your password.'],
      ]],
      ['What is deleted', [
        ['', 'Your account (name, email, password hash), your saved maps and papers, your daily usage counts, and every map built from PDFs you uploaded. Deletion is immediate and cannot be undone.'],
      ]],
      ['What is kept', [
        ['', 'Maps built from topic searches contain only public research metadata and are shared with anyone searching the same topic, so they are not removed. They hold nothing that identifies you.'],
      ]],
    ],
  },
};

export function renderLegal(container, _params, key) {
  const pageData = PAGES[key];
  document.title = `${pageData.title} · Ereuna`;

  const body = h('article', { className: 'legal' },
    h('a', { className: 'legal__back', href: '#/' }, icon('arrow_back'), isSignedIn() ? 'Back to Ereuna' : 'Ereuna home'),
    h('div', { className: 'legal__brand' }, brandMark(28)),
    h('h1', { className: 'legal__title' }, pageData.title),
    h('p', { className: 'legal__updated' }, `Last updated ${UPDATED}`),
    h('p', { className: 'legal__lead' }, pageData.lead),
    ...pageData.sections.map(([heading, items]) => h('section', { className: 'legal__section' },
      h('h2', {}, heading),
      ...items.map(([strong, text]) => h('p', {}, strong ? h('strong', {}, strong) : null, text)))),
    key === 'deletion' && isSignedIn()
      ? h('a', { className: 'btn-editorial-primary legal__cta', href: '#/settings' }, 'Go to Settings')
      : null,
    CONTACT ? h('p', { className: 'legal__contact' }, ...contactLine()) : null,
  );

  container.appendChild(h('div', { className: 'legal-page' }, h('main', { className: 'legal-wrap' }, body), siteFooter()));
}
