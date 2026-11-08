// Quick sign-in helpers: Google Identity Services and Truecaller one-tap. Each provider's script or app is only
// touched when the shopper reaches a login screen, so the rest of the shop stays fast.

const GSI_SRC = 'https://accounts.google.com/gsi/client';
let gsi = null;

/** Loads Google's sign-in script once; resolves to window.google. */
export function loadGoogle() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google);
  if (!gsi) {
    gsi = new Promise((resolve, reject) => {
      const tag = document.createElement('script');
      tag.src = GSI_SRC;
      tag.async = true;
      tag.onload = () => resolve(window.google);
      tag.onerror = () => { gsi = null; reject(new Error('Could not load Google sign-in')); };
      document.head.appendChild(tag);
    });
  }
  return gsi;
}

/** Fetches Google's sign-in script in the background so the button is ready the moment the login page opens. */
export function warmGoogle() {
  loadGoogle().catch(() => {});
}

let googleReadyFor = null;

/**
 * Draws the official "Continue with Google" button into `el` and also offers Google's one-tap prompt.
 * `onCredential` receives the ID token to send to our server.
 */
export async function showGoogleButton(el, clientId, onCredential) {
  const google = await loadGoogle();
  if (googleReadyFor !== clientId) {
    google.accounts.id.initialize({
      client_id: clientId,
      callback: (res) => onCredential(res.credential),
      ux_mode: 'popup',
      cancel_on_tap_outside: true,
      itp_support: true,
      use_fedcm_for_prompt: true,
    });
    googleReadyFor = clientId;
  }
  el.innerHTML = '';
  google.accounts.id.renderButton(el, {
    type: 'standard', theme: 'outline', size: 'large', shape: 'pill', text: 'continue_with', logo_alignment: 'center',
    width: Math.min(400, Math.max(240, el.clientWidth || 320)),
  });
  google.accounts.id.prompt();
}

/** Truecaller one-tap works on Android phones that have the Truecaller app. */
export const isAndroid = () => typeof navigator !== 'undefined' && /Android/i.test(navigator.userAgent);

/** Opens the Truecaller app's verification sheet for this request. */
export function openTruecaller(config, nonce) {
  const origin = window.location.origin;
  const params = new URLSearchParams({
    type: 'btmsheet',
    requestNonce: nonce,
    partnerKey: config.appKey,
    partnerName: config.appName,
    lang: 'en',
    privacyUrl: `${origin}/privacy`,
    termsUrl: `${origin}/terms`,
    loginPrefix: 'continue',
    loginSuffix: 'login',
    ctaPrefix: 'continuewith',
    ctaColor: '#7b1e2b',
    ctaTextColor: '#ffffff',
    btnShape: 'round',
    skipOption: 'useanothernum',
    ttl: '60000',
  });
  window.location.href = `truecallersdk://truesdk/web_verify?${params}`;
}
