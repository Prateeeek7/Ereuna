/**
 * Settings: account, appearance, data sources, privacy and account deletion.
 */
import { getCurrentUser, signOut, deleteAccount } from '../api.js';
import { h, icon, toast } from '../utils.js';
import { getTheme, setTheme } from '../theme.js';
import { navigate } from '../router.js';

export function renderSettings(container) {
  document.title = 'Settings · Ereuna';
  const user = getCurrentUser();

  const group = (title, ...children) => h('section', { className: 'settings-group' }, h('h2', { className: 'section-label' }, title), ...children);

  // Appearance
  const themes = [
    ['light', 'Paper', 'Warm off-white, for daylight', 'light_mode'],
    ['dark', 'Night', 'Low glare, for the lab at 2 a.m.', 'dark_mode'],
  ];
  const themeCards = h('div', { className: 'radio-cards', role: 'radiogroup', 'aria-label': 'Theme' });
  const paintThemes = () => themeCards.replaceChildren(...themes.map(([id, name, desc, ic]) => h('button', {
    className: `radio-card ${getTheme() === id ? 'active' : ''}`, type: 'button', role: 'radio', 'aria-checked': String(getTheme() === id),
    onClick: () => {
      setTheme(id);
      paintThemes();
      const btn = document.getElementById('theme-toggle');
      if (btn) btn.replaceChildren(icon(id === 'dark' ? 'light_mode' : 'dark_mode'));
    },
  }, h('span', { className: 'radio-dot' }), icon(ic), h('span', {}, h('span', { className: 'radio-card__name' }, name), h('span', { className: 'radio-card__desc' }, desc)))));
  paintThemes();

  // Sources
  const sources = [
    ['OpenAlex', 'Search, metadata, citation counts and references.', 'https://openalex.org'],
    ['Semantic Scholar', 'Relevance search, abstracts, title matching.', 'https://www.semanticscholar.org'],
    ['arXiv', 'Preprints and their full text.', 'https://arxiv.org'],
    ['Crossref', 'DOI metadata.', 'https://www.crossref.org'],
    ['Unpaywall', 'Legal open-access copies of published papers.', 'https://unpaywall.org'],
    ['Europe PMC', 'Open full text for biomedical articles.', 'https://europepmc.org'],
  ];

  // Delete account
  const pw = h('input', { className: 'input', type: 'password', id: 'delete-password', autocomplete: 'current-password', placeholder: 'Your password' });
  const confirmBox = h('input', { type: 'checkbox', id: 'delete-confirm' });
  const err = h('div', { className: 'auth-error', role: 'alert', hidden: true });
  const delBtn = h('button', { className: 'btn btn-danger', type: 'submit' }, icon('delete_forever'), 'Delete my account');
  const deleteForm = h('form', {
    className: 'danger-form', hidden: true,
    onSubmit: async e => {
      e.preventDefault();
      err.hidden = true;
      if (!confirmBox.checked) { err.textContent = 'Tick the box to confirm.'; err.hidden = false; return; }
      if (!pw.value) { err.textContent = 'Enter your password.'; err.hidden = false; return; }
      delBtn.disabled = true;
      delBtn.replaceChildren(h('span', { className: 'spinner spinner--light' }), 'Deleting…');
      try {
        await deleteAccount(pw.value);
        sessionStorage.setItem('ereuna_notice', 'Your account and its data have been deleted.');
        navigate('/signin');
      } catch (ex) {
        err.textContent = ex.message;
        err.hidden = false;
        delBtn.disabled = false;
        delBtn.replaceChildren(icon('delete_forever'), 'Delete my account');
      }
    },
  },
    h('p', { className: 'danger-form__text' }, 'This permanently deletes your account, your library, and every map built from your PDFs. It can’t be undone.'),
    err,
    h('div', { className: 'input-group' }, h('label', { for: 'delete-password' }, 'Password'), pw),
    h('label', { className: 'check-row', for: 'delete-confirm' }, confirmBox, 'I understand this can’t be undone'),
    h('div', { className: 'danger-form__actions' },
      h('button', { className: 'btn btn-ghost', type: 'button', onClick: () => { deleteForm.hidden = true; openDelete.hidden = false; } }, 'Cancel'),
      delBtn));
  const openDelete = h('button', {
    className: 'btn btn-outline btn-danger-outline btn-sm', type: 'button',
    onClick: () => { deleteForm.hidden = false; openDelete.hidden = true; pw.focus(); },
  }, 'Delete account…');

  container.appendChild(h('div', { className: 'page' }, h('div', { className: 'container container--narrow' },
    h('header', { className: 'page-header' }, h('h1', { className: 'type-display' }, 'Settings')),

    group('Account',
      h('div', { className: 'account-row' },
        h('div', { className: 'account-row__avatar', 'aria-hidden': 'true' }, (user?.name || '?').charAt(0).toUpperCase()),
        h('div', { className: 'flex-1' },
          h('div', { className: 'type-title' }, user?.name || ''),
          h('div', { className: 'type-body-small text-ink2' }, user?.email || '')),
        h('button', {
          className: 'btn btn-outline btn-sm', type: 'button',
          onClick: () => { signOut(); toast('Signed out'); navigate('/'); },
        }, icon('logout'), 'Sign out'))),

    group('Appearance', themeCards),

    group('Data sources',
      h('p', { className: 'settings-note' }, 'Ereuna reads openly available metadata and legally open-access full text from:'),
      h('ul', { className: 'source-list' }, ...sources.map(([name, desc, url]) => h('li', {},
        h('a', { className: 'source-row', href: url, target: '_blank', rel: 'noopener' },
          h('span', { className: 'flex-1' }, h('span', { className: 'source-row__name' }, name), h('span', { className: 'source-row__desc' }, desc)),
          icon('open_in_new', 'text-ink3')))))),

    group('Privacy',
      h('ul', { className: 'privacy-points' },
        h('li', {}, 'Your email is used only to sign you in.'),
        h('li', {}, 'Uploaded PDFs are read to build your map, then discarded. Maps from them are visible only to you.'),
        h('li', {}, 'Nothing is sold or shared with advertisers. No tracking scripts.')),
      h('div', { className: 'link-row' },
        h('a', { href: '#/privacy' }, 'Privacy policy'), h('a', { href: '#/terms' }, 'Terms of use'))),

    group('Delete account', openDelete, deleteForm),

    h('p', { className: 'fine-print settings-version' }, 'Ereuna web 0.1.0'),
  )));
}
