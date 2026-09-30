/**
 * Minimal hash router: #/path?query. Each route renders into a container and
 * may return a cleanup function that runs before the next route renders.
 */

const routes = [];
let cleanup = null;
let beforeEach = null;
let rendering = 0;

/** Register a route. Patterns may contain :params, e.g. /map/:id */
export function route(pattern, handler) {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/\/:([^/]+)/g, (_, k) => { keys.push(k); return '/([^/]+)'; }) + '/?$');
  routes.push({ re, keys, handler });
}

export function navigate(path, { replace = false } = {}) {
  const target = `#${path}`;
  if (window.location.hash === target) render();
  else if (replace) { history.replaceState(null, '', target); render(); }
  else window.location.hash = path;
}

export function getParams() {
  const hash = window.location.hash.slice(1) || '/';
  const q = hash.indexOf('?');
  const path = q >= 0 ? hash.slice(0, q) : hash;
  const params = Object.fromEntries(new URLSearchParams(q >= 0 ? hash.slice(q + 1) : ''));
  return { path: path || '/', params };
}

/** Remember where a signed-out visitor was going, to return there after sign-in. */
export function rememberReturnPath(path) {
  sessionStorage.setItem('ereuna_next', path);
}

export function takeReturnPath() {
  const p = sessionStorage.getItem('ereuna_next');
  sessionStorage.removeItem('ereuna_next');
  return p;
}

/** Called before every render with the path; returns the element to render into. */
export function onBeforeEach(fn) {
  beforeEach = fn;
}

async function render() {
  const token = ++rendering;
  const { path, params } = getParams();
  if (cleanup) {
    try { cleanup(); } catch (e) { console.error(e); }
    cleanup = null;
  }
  const match = routes.map(r => ({ r, m: path.match(r.re) })).find(x => x.m);
  const container = beforeEach(path);
  container.replaceChildren();
  window.scrollTo(0, 0);

  if (!match) {
    container.appendChild(notFound());
    return;
  }
  const routeParams = { ...params };
  match.r.keys.forEach((k, i) => { routeParams[k] = decodeURIComponent(match.m[i + 1]); });
  try {
    const result = await match.r.handler(container, routeParams);
    if (typeof result === 'function') {
      if (token === rendering) cleanup = result;
      else result();
    }
  } catch (err) {
    console.error('Route error:', err);
    if (token !== rendering) return;
    container.replaceChildren(errorPage(err));
  }
}

function page(title, body) {
  const wrap = document.createElement('div');
  wrap.className = 'page container status-page';
  const t = document.createElement('div');
  t.className = 'type-display';
  t.textContent = title;
  const p = document.createElement('p');
  p.className = 'text-ink2';
  p.textContent = body;
  const a = document.createElement('a');
  a.className = 'btn btn-primary';
  a.href = '#/';
  a.textContent = 'Go home';
  wrap.append(t, p, a);
  return wrap;
}

function notFound() {
  document.title = 'Not found · Ereuna';
  return page('Page not found', 'The page you’re looking for doesn’t exist.');
}

function errorPage(err) {
  return page('Something went wrong', err?.message || 'Unexpected error.');
}

export function startRouter() {
  window.addEventListener('hashchange', render);
  render();
}
