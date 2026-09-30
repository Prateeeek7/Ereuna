/**
 * Ereuna API client — thin wrapper over fetch() for all backend endpoints.
 * The session token is kept in localStorage and sent as a Bearer header.
 *
 * In development Vite proxies /v1 to the local backend. For a deployed site,
 * set VITE_API_BASE (e.g. https://api.ereuna.app) at build time.
 */

const API_ORIGIN = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '');
const API_BASE = `${API_ORIGIN}/v1`;

const TOKEN_KEY = 'ereuna_token';
const USER_KEY = 'ereuna_user';

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function authHeaders(extra = {}) {
  const headers = { ...extra };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

/** Error carrying the HTTP status, so pages can tell "not found" from "offline". */
export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.status = status;
  }
}

// A 401 on any signed-in call means the session expired or the account is gone.
function onUnauthorized() {
  if (!getToken()) return;
  clearSession();
  const here = window.location.hash.slice(1);
  if (here && !here.startsWith('/sign')) sessionStorage.setItem('ereuna_next', here);
  sessionStorage.setItem('ereuna_notice', 'Your session ended. Please sign in again.');
  window.location.hash = '/signin';
}

async function parseError(res) {
  const data = await res.json().catch(() => null);
  let msg = data?.detail;
  if (Array.isArray(msg)) msg = msg.map(d => d.msg).join(' ');
  if (!msg) {
    msg = res.status === 429 ? 'Daily limit reached. Try again tomorrow.'
      : res.status >= 500 ? 'The server had a problem. Try again in a moment.'
      : `Request failed (${res.status}).`;
  }
  return new ApiError(msg, res.status);
}

async function send(path, opts = {}) {
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, opts);
  } catch {
    throw new ApiError("Can't reach the Ereuna server. Check your connection.", 0);
  }
  if (res.status === 401 && !path.startsWith('/auth/sign')) onUnauthorized();
  if (!res.ok) throw await parseError(res);
  return res;
}

async function request(method, path, body = null) {
  const opts = { method, headers: authHeaders({ 'Content-Type': 'application/json' }) };
  if (body !== null) opts.body = JSON.stringify(body);
  const res = await send(path, opts);
  if (res.status === 204) return null;
  return res.json().catch(() => null);
}

// ── Session ───────────────────────────────────────────────
function storeSession(data) {
  localStorage.setItem(TOKEN_KEY, data.token);
  localStorage.setItem(USER_KEY, JSON.stringify(data.user));
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem('ereuna_recent');
}

export async function signUp(name, email, password) {
  const data = await request('POST', '/auth/signup', { name, email, password });
  storeSession(data);
  return data;
}

export async function signIn(email, password) {
  const data = await request('POST', '/auth/signin', { email, password });
  storeSession(data);
  return data;
}

export function signOut() {
  clearSession();
}

export function getCurrentUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
  } catch {
    return null;
  }
}

export function isSignedIn() {
  return !!getToken();
}

/** Re-reads the account from the server (and signs out if the token is stale). */
export async function refreshCurrentUser() {
  const user = await request('GET', '/auth/me');
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

export async function deleteAccount(password) {
  await request('POST', '/auth/delete', { password });
  clearSession();
}

// ── Maps ──────────────────────────────────────────────────
export function createMap(topic, filters = {}) {
  return request('POST', '/maps', { topic, filters });
}

export async function createMapFromUploads(topic, files, includeRelated = false) {
  const form = new FormData();
  form.append('topic', topic);
  form.append('include_search', includeRelated ? 'true' : 'false');
  files.forEach(f => form.append('files', f, f.name));
  const res = await send('/maps/upload', { method: 'POST', headers: authHeaders(), body: form });
  return res.json();
}

export function getMap(mapId) {
  return request('GET', `/maps/${encodeURIComponent(mapId)}`);
}

export function getMapGraph(mapId) {
  return request('GET', `/maps/${encodeURIComponent(mapId)}/graph`);
}

export function refreshMap(mapId) {
  return request('POST', `/maps/${encodeURIComponent(mapId)}/refresh`);
}

/** Downloads an export (md, bibtex, csv) and hands it to the browser as a file. */
export async function downloadExport(mapId, format, filename) {
  const res = await send(`/maps/${encodeURIComponent(mapId)}/download?format=${format}`, { headers: authHeaders() });
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ── Jobs (SSE over fetch, since EventSource can't send auth headers) ──
/**
 * Streams a job's progress events. Returns a controller with close().
 * onEvent(type, data) receives stage, log, paper_selected, done and error.
 */
export function subscribeToJob(jobId, onEvent) {
  const abort = new AbortController();
  const controller = { closed: false, close() { this.closed = true; abort.abort(); } };

  (async () => {
    let res;
    try {
      res = await fetch(`${API_BASE}/jobs/${encodeURIComponent(jobId)}/events`, {
        headers: authHeaders({ Accept: 'text/event-stream' }),
        signal: abort.signal,
      });
    } catch (e) {
      if (!controller.closed) onEvent('error', { message: "Lost connection to the server." });
      return;
    }
    if (!res.ok || !res.body) {
      if (res.status === 401) onUnauthorized();
      onEvent('error', { message: (await parseError(res)).message });
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let event = 'message';
    let data = [];
    let finished = false;

    const dispatch = () => {
      if (data.length) {
        try {
          const parsed = JSON.parse(data.join('\n'));
          onEvent(event, parsed);
          if (event === 'done' || event === 'error') finished = true;
        } catch { /* keep-alive or malformed line */ }
      }
      event = 'message';
      data = [];
    };

    try {
      while (!controller.closed && !finished) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (line === '') dispatch();
          else if (line.startsWith(':')) continue;
          else if (line.startsWith('event:')) event = line.slice(6).trim();
          else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
          if (finished) break;
        }
      }
      if (!finished && !controller.closed) {
        onEvent('error', { message: 'The progress stream ended before the map was ready.' });
      }
    } catch {
      if (!controller.closed && !finished) onEvent('error', { message: 'Lost connection to the server.' });
    } finally {
      reader.cancel().catch(() => {});
    }
  })();

  return controller;
}

// ── Papers ────────────────────────────────────────────────
export function getPaper(paperId) {
  return request('GET', `/papers/${encodeURIComponent(paperId)}`);
}

// ── Library ───────────────────────────────────────────────
export function listSavedMaps() {
  return request('GET', '/library/maps');
}

export function saveMap(mapId) {
  return request('POST', `/library/maps/${encodeURIComponent(mapId)}`);
}

export function removeSavedMap(mapId) {
  return request('DELETE', `/library/maps/${encodeURIComponent(mapId)}`);
}

export function listSavedPapers() {
  return request('GET', '/library/papers');
}

export function savePaper(paperId) {
  return request('POST', `/library/papers/${encodeURIComponent(paperId)}`);
}

export function removeSavedPaper(paperId) {
  return request('DELETE', `/library/papers/${encodeURIComponent(paperId)}`);
}

// ── Recent searches (this browser only) ───────────────────
const RECENT_KEY = 'ereuna_recent';

export function getRecentMaps() {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
  } catch {
    return [];
  }
}

export function rememberMap(map) {
  const entry = { id: map.id, topic: map.topic, papers: map.stats?.total_papers || 0, source: map.source || 'search', at: Date.now() };
  const list = [entry, ...getRecentMaps().filter(m => m.id !== map.id)].slice(0, 8);
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(list)); } catch { /* storage full or blocked */ }
}
