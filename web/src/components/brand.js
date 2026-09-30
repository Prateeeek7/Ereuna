/** Ereuna's mark: a radar with a single contact — drawn inline so it takes the theme colors. */
export function brandMark(size = 26) {
  const span = document.createElement('span');
  span.className = 'brand-mark';
  span.setAttribute('aria-hidden', 'true');
  span.innerHTML = `<svg width="${size}" height="${size}" viewBox="0 0 32 32" fill="none">
    <circle cx="16" cy="16" r="14.5" stroke="currentColor" stroke-opacity=".55"/>
    <circle cx="16" cy="16" r="9" stroke="currentColor" stroke-opacity=".35"/>
    <path d="M16 16 L30.5 16 A14.5 14.5 0 0 0 26.25 5.75 Z" fill="currentColor" fill-opacity=".22"/>
    <line x1="16" y1="16" x2="30.5" y2="16" stroke="currentColor" stroke-width="1.6"/>
    <circle cx="16" cy="16" r="2" fill="currentColor"/>
    <circle cx="23.5" cy="9.5" r="2.2" fill="currentColor"/>
  </svg>`;
  return span;
}
