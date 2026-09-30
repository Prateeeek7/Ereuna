/**
 * The one site footer, used on every page (public and signed in):
 * links on top, and the name set very large in the bottom-right corner.
 */
import { h } from '../utils.js';
import { navigate } from '../router.js';
import { isSignedIn } from '../api.js';
import { brandMark } from './brand.js';

export function siteFooter({ onSection } = {}) {
  const signedIn = isSignedIn();

  const section = (text, id) => h('a', {
    className: 'site-footer__link', href: `#${id}`,
    onClick: e => {
      e.preventDefault();
      if (onSection) onSection(id);
      else { navigate('/'); setTimeout(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' }), 150); }
    },
  }, text);
  const route = (text, path) => h('a', { className: 'site-footer__link', href: `#${path}` }, text);
  const ext = (text, url) => h('a', { className: 'site-footer__link', href: url, target: '_blank', rel: 'noopener' }, text, h('span', { className: 'site-footer__ext', 'aria-hidden': 'true' }, '↗'));
  const col = (title, ...links) => h('div', { className: 'site-footer__col' }, h('h2', { className: 'site-footer__col-title' }, title), ...links);

  const first = signedIn
    ? col('Ereuna', route('New search', '/'), route('Library', '/library'), route('Settings', '/settings'))
    : col('Product', section('How it works', 'workflow'), section('Example map', 'research-map'), section('Evidence', 'evidence'), section('Sources', 'open-data'));
  const second = signedIn
    ? col('Account', route('Privacy policy', '/privacy'), route('Terms of use', '/terms'), route('Delete your account', '/account-deletion'))
    : col('Account', route('Create account', '/signup'), route('Sign in', '/signin'), route('Delete your account', '/account-deletion'));
  const third = signedIn
    ? col('Sources', ext('OpenAlex', 'https://openalex.org'), ext('Semantic Scholar', 'https://www.semanticscholar.org'), ext('arXiv', 'https://arxiv.org'))
    : col('Legal', route('Privacy policy', '/privacy'), route('Terms of use', '/terms'), ext('OpenAlex', 'https://openalex.org'));

  return h('footer', { className: 'site-footer' },
    h('div', { className: 'site-footer__inner' },
      h('div', { className: 'site-footer__top' },
        h('div', { className: 'site-footer__about' },
          h('div', { className: 'site-footer__logo' }, brandMark(26)),
          h('p', { className: 'site-footer__desc' },
            'Maps of the research literature, built from open scholarly data, with the source sentence behind every finding.')),
        h('nav', { className: 'site-footer__cols', 'aria-label': 'Footer' }, first, second, third)),
      h('div', { className: 'site-footer__bottom' },
        h('div', { className: 'site-footer__meta' },
          h('span', { className: 'site-footer__tagline' }, 'Research, on your radar.'),
          h('span', {}, `© ${new Date().getFullYear()} Ereuna · Metadata from OpenAlex (CC0), Semantic Scholar, arXiv and Crossref`)),
        h('div', { className: 'site-footer__giant', 'aria-hidden': 'true' }, 'EREUNA'))));
}
