/**
 * Ereuna Web — entry point: routes and the two shells (public pages / signed-in app).
 */
import { route, startRouter, navigate, onBeforeEach, rememberReturnPath, getParams } from './router.js';
import { initTheme } from './theme.js';
import { isSignedIn, signOut, getCurrentUser, refreshCurrentUser } from './api.js';
import { h, icon } from './utils.js';
import { brandMark } from './components/brand.js';
import { siteFooter } from './components/footer.js';
import { renderLanding } from './pages/landing.js';
import { renderAuth } from './pages/auth.js';
import { renderLegal } from './pages/legal.js';
import { renderSearch } from './pages/search.js';
import { renderMap } from './pages/map.js';
import { renderPaper } from './pages/paper.js';
import { renderLibrary } from './pages/library.js';
import { renderSettings } from './pages/settings.js';

initTheme();

const app = document.getElementById('app');
let shell = null; // { kind: 'public' | 'app', content, user }

const PUBLIC_PATHS = ['/signin', '/signup', '/privacy', '/terms', '/account-deletion'];

function isPublic(path) {
  return PUBLIC_PATHS.includes(path) || (path === '/' && !isSignedIn());
}

function buildPublicShell() {
  const content = h('div', { id: 'page-content' });
  app.replaceChildren(content);
  return { kind: 'public', content };
}

function buildAppShell() {
  const user = getCurrentUser();
  const link = (id, href, iconName, label) => h('a', { className: 'topbar__link', href, id }, icon(iconName), h('span', { className: 'nav-label' }, label));

  const menu = h('div', { className: 'account-menu', hidden: true, role: 'menu' },
    h('div', { className: 'account-menu__who' },
      h('div', { className: 'account-menu__name' }, user?.name || 'Account'),
      h('div', { className: 'account-menu__email' }, user?.email || '')),
    h('a', { className: 'account-menu__item', href: '#/settings', role: 'menuitem' }, icon('settings'), 'Settings'),
    h('button', {
      className: 'account-menu__item', type: 'button', role: 'menuitem',
      onClick: () => { signOut(); navigate('/'); },
    }, icon('logout'), 'Sign out'));
  const avatar = h('button', {
    className: 'topbar__avatar', type: 'button', 'aria-haspopup': 'menu', 'aria-label': 'Account',
    onClick: e => { e.stopPropagation(); menu.hidden = !menu.hidden; },
  }, (user?.name || '?').trim().charAt(0).toUpperCase());
  document.addEventListener('click', () => { menu.hidden = true; });

  const topbar = h('header', { className: 'topbar' },
    h('div', { className: 'topbar__inner' },
      h('a', { className: 'topbar__brand', href: '#/', 'aria-label': 'Ereuna, new search' }, brandMark(22), h('span', {}, 'EREUNA')),
      h('nav', { className: 'topbar__nav', 'aria-label': 'Main' },
        link('nav-search', '#/', 'travel_explore', 'Search'),
        link('nav-library', '#/library', 'bookmarks', 'Library'),
        link('nav-settings', '#/settings', 'tune', 'Settings'),
        h('div', { className: 'account' }, avatar, menu))));

  const content = h('main', { id: 'page-content' });
  const bottom = h('nav', { className: 'bottom-nav', 'aria-label': 'Main' },
    link('bnav-search', '#/', 'travel_explore', 'Search'),
    link('bnav-library', '#/library', 'bookmarks', 'Library'),
    link('bnav-settings', '#/settings', 'tune', 'Settings'));
  app.replaceChildren(topbar, content, siteFooter(), bottom);
  return { kind: 'app', content, user: user?.id };
}

function updateActiveNav(path) {
  const groups = { search: ['/', '/map', '/paper'], library: ['/library'], settings: ['/settings'] };
  for (const [name, prefixes] of Object.entries(groups)) {
    const active = prefixes.some(p => p === '/' ? path === '/' : path === p || path.startsWith(p + '/'));
    document.querySelectorAll(`#nav-${name}, #bnav-${name}`).forEach(el => {
      el.classList.toggle('active', active);
      if (active) el.setAttribute('aria-current', 'page'); else el.removeAttribute('aria-current');
    });
  }
}

onBeforeEach(path => {
  const kind = isPublic(path) ? 'public' : 'app';
  const userId = getCurrentUser()?.id;
  if (!shell || shell.kind !== kind || (kind === 'app' && shell.user !== userId)) {
    shell = kind === 'public' ? buildPublicShell() : buildAppShell();
  }
  if (kind === 'app') updateActiveNav(path);
  return shell.content;
});

// Signed-in pages send signed-out visitors to sign in, and back afterwards.
function signedIn(handler) {
  return (container, params) => {
    if (!isSignedIn()) {
      const { path } = getParams();
      rememberReturnPath(window.location.hash.slice(1) || path);
      navigate('/signin', { replace: true });
      return;
    }
    return handler(container, params);
  };
}

function signedOutOnly(handler) {
  return (container, params) => {
    if (isSignedIn()) { navigate('/', { replace: true }); return; }
    return handler(container, params);
  };
}

route('/', (c, p) => (isSignedIn() ? renderSearch(c, p) : renderLanding(c, p)));
route('/signin', signedOutOnly((c, p) => renderAuth(c, p, 'signin')));
route('/signup', signedOutOnly((c, p) => renderAuth(c, p, 'signup')));
route('/privacy', (c, p) => renderLegal(c, p, 'privacy'));
route('/terms', (c, p) => renderLegal(c, p, 'terms'));
route('/account-deletion', (c, p) => renderLegal(c, p, 'deletion'));
route('/search', signedIn(renderSearch));
route('/map/:id', signedIn(renderMap));
route('/paper/:id', signedIn(renderPaper));
route('/library', signedIn(renderLibrary));
route('/settings', signedIn(renderSettings));
// Old links
route('/auth', (c) => navigate(isSignedIn() ? '/' : '/signin', { replace: true }));

startRouter();

// Check the stored session is still valid (expired tokens sign out via the 401 handler).
if (isSignedIn()) refreshCurrentUser().catch(() => {});
