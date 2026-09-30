/**
 * Sign in / create account.
 */
import { signIn, signUp } from '../api.js';
import { h, icon } from '../utils.js';
import { navigate, takeReturnPath } from '../router.js';
import { brandMark } from '../components/brand.js';

// Mirrors the server's rules (app/auth.py validate_signup).
const RULES = [
  ['At least 8 characters', p => p.length >= 8],
  ['An uppercase letter', p => p.toLowerCase() !== p],
  ['A number', p => /\d/.test(p)],
];

export function renderAuth(container, _params, mode = 'signin') {
  const isSignUp = mode === 'signup';
  document.title = `${isSignUp ? 'Create account' : 'Sign in'} · Ereuna`;

  const notice = sessionStorage.getItem('ereuna_notice');
  sessionStorage.removeItem('ereuna_notice');

  const errorEl = h('div', { className: 'auth-error', role: 'alert', hidden: true });
  const field = (id, label, attrs, hint) => h('div', { className: 'input-group' },
    h('label', { for: id }, label),
    h('input', { className: 'input', id, name: id, ...attrs }),
    hint || null);

  const password = h('input', {
    className: 'input', id: 'auth-password', name: 'password', type: 'password', required: true,
    autocomplete: isSignUp ? 'new-password' : 'current-password',
    placeholder: isSignUp ? 'Create a password' : 'Your password',
  });
  const reveal = h('button', {
    className: 'input-reveal', type: 'button', 'aria-label': 'Show password',
    onClick: () => {
      const show = password.type === 'password';
      password.type = show ? 'text' : 'password';
      reveal.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      reveal.replaceChildren(icon(show ? 'visibility_off' : 'visibility'));
    },
  }, icon('visibility'));

  const ruleItems = RULES.map(([text]) => h('li', {}, icon('check'), text));
  const rules = isSignUp ? h('ul', { className: 'password-rules', id: 'password-rules' }, ...ruleItems) : null;
  if (isSignUp) {
    password.setAttribute('aria-describedby', 'password-rules');
    password.addEventListener('input', () => {
      RULES.forEach(([, ok], i) => ruleItems[i].classList.toggle('met', ok(password.value)));
    });
  }

  const submit = h('button', { className: 'btn-editorial-primary w-full', type: 'submit' },
    isSignUp ? 'Create account' : 'Sign in');

  const form = h('form', {
    className: 'auth-form', novalidate: true,
    onSubmit: async e => {
      e.preventDefault();
      errorEl.hidden = true;
      const email = form.elements['auth-email'].value.trim();
      const name = isSignUp ? form.elements['auth-name'].value.trim() : '';
      const pw = password.value;

      let problem = '';
      if (isSignUp && !name) problem = 'Please enter your name.';
      else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) problem = 'Please enter a valid email address.';
      else if (!pw) problem = 'Please enter your password.';
      else if (isSignUp && RULES.some(([, ok]) => !ok(pw))) problem = 'Your password needs 8+ characters, an uppercase letter and a number.';
      if (problem) { errorEl.textContent = problem; errorEl.hidden = false; return; }

      submit.disabled = true;
      submit.replaceChildren(h('span', { className: 'spinner spinner--light' }), isSignUp ? 'Creating account…' : 'Signing in…');
      try {
        if (isSignUp) await signUp(name, email, pw);
        else await signIn(email, pw);
        navigate(takeReturnPath() || '/');
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
        submit.disabled = false;
        submit.replaceChildren(isSignUp ? 'Create account' : 'Sign in');
      }
    },
  },
    isSignUp ? field('auth-name', 'Name', { type: 'text', required: true, autocomplete: 'name', placeholder: 'Your name' }) : null,
    field('auth-email', 'Email', { type: 'email', required: true, autocomplete: 'email', inputmode: 'email', placeholder: 'you@university.edu' }),
    h('div', { className: 'input-group' },
      h('label', { for: 'auth-password' }, 'Password'),
      h('div', { className: 'input-with-action' }, password, reveal),
      rules),
    submit);

  const card = h('div', { className: 'auth-card' },
    h('a', { className: 'auth-back', href: '#/' }, icon('arrow_back'), 'Ereuna'),
    h('div', { className: 'auth-brand' }, brandMark(34)),
    h('h1', { className: 'editorial-form__title' }, isSignUp ? 'Create your account' : 'Welcome back'),
    h('p', { className: 'editorial-form__sub' }, isSignUp
      ? 'Free. Your maps and saved papers follow you between the web and the Android app.'
      : 'Sign in to open your research maps and library.'),
    notice ? h('div', { className: 'auth-notice', role: 'status' }, notice) : null,
    errorEl,
    form,
    h('p', { className: 'auth-toggle' },
      isSignUp ? 'Already have an account? ' : 'New to Ereuna? ',
      h('a', { href: isSignUp ? '#/signin' : '#/signup' }, isSignUp ? 'Sign in' : 'Create one')),
    h('p', { className: 'auth-privacy' },
      'Your email is used only to sign you in. By continuing you agree to the ',
      h('a', { href: '#/terms' }, 'terms'), ' and ', h('a', { href: '#/privacy' }, 'privacy policy'), '.'));

  container.appendChild(h('main', { className: 'auth-page' }, card));
  setTimeout(() => form.querySelector('input')?.focus(), 30);
}
