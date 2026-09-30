/**
 * Theme manager — light/dark, remembered in localStorage.
 * First visit follows the system setting.
 */

const STORAGE_KEY = 'ereuna_theme';

function stored() {
  try { return localStorage.getItem(STORAGE_KEY); } catch { return null; }
}

export function getTheme() {
  return stored() || (window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
}

export function setTheme(theme) {
  try { localStorage.setItem(STORAGE_KEY, theme); } catch { /* private mode */ }
  applyTheme(theme);
}

export function toggleTheme() {
  const next = getTheme() === 'dark' ? 'light' : 'dark';
  setTheme(next);
  return next;
}

export function applyTheme(theme = getTheme()) {
  document.documentElement.setAttribute('data-theme', theme);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#121210' : '#F5F2EC');
}

export function initTheme() {
  applyTheme();
  // Follow the system until the visitor picks a theme themselves.
  window.matchMedia?.('(prefers-color-scheme: light)').addEventListener?.('change', () => {
    if (!stored()) applyTheme();
  });
}
