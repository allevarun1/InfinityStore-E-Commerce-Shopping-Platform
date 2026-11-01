// Login with mobile number + OTP through Firebase Phone Auth. Firebase sends the SMS and checks the code; we only
// keep the short-lived ID token it returns and swap it for our own session at /api/auth/phone. The Firebase SDK is
// loaded on first use so it never slows down the rest of the shop. The web config comes from /api/store/info.

let loaded = null;

async function firebase(config) {
  if (!loaded) {
    loaded = Promise.all([import('firebase/app'), import('firebase/auth')]).then(([app, auth]) => {
      const instance = auth.getAuth(app.initializeApp(config, 'rekha-phone-login'));
      instance.languageCode = 'en';
      return { auth, instance };
    });
  }
  return loaded;
}

/** Firebase error codes worth explaining; anything else gets a generic retry message. */
const MESSAGES = {
  'auth/invalid-phone-number': 'Enter a valid 10-digit mobile number',
  'auth/too-many-requests': 'Too many attempts. Please wait a few minutes and try again.',
  'auth/quota-exceeded': 'We cannot send more OTPs right now. Please try again later or sign in with your password.',
  'auth/invalid-verification-code': 'That OTP is not right. Please check the SMS and try again.',
  'auth/code-expired': 'That OTP has expired. Please request a new one.',
  'auth/network-request-failed': 'No internet connection. Please check and try again.',
  'auth/captcha-check-failed': 'Security check failed. Please reload the page and try again.',
  'auth/operation-not-allowed': 'OTP login is not available right now. Please sign in with your username and password.',
  'auth/billing-not-enabled': 'OTP login is not available right now. Please sign in with your username and password.',
  'auth/unauthorized-domain':'OTP login is not available right now. Please sign in with your username and password.',
};

export const otpError = (err) => {
  // Keeps the Firebase code in the console so a failure can be traced without guessing.
  if (err?.code) console.warn('OTP login failed:', err.code);
  return MESSAGES[err?.code] || err?.response?.data?.message || 'Something went wrong. Please try again.';
};

/**
 * Sends an OTP to a 10-digit Indian mobile. `buttonId` is the element the invisible security check attaches to.
 * Returns a confirm(code) function that resolves to the Firebase ID token.
 */
export async function sendOtp(config, mobile, buttonId) {
  const { auth, instance } = await firebase(config);
  if (window.__rekhaRecaptcha) {
    try { window.__rekhaRecaptcha.clear(); } catch { /* already gone */ }
  }
  const verifier = new auth.RecaptchaVerifier(instance, buttonId, { size: 'invisible' });
  window.__rekhaRecaptcha = verifier;
  const result = await auth.signInWithPhoneNumber(instance, `+91${mobile}`, verifier);
  return async (code) => {
    const credential = await result.confirm(code);
    const token = await credential.user.getIdToken();
    // Our own session takes over from here; do not keep a Firebase session in the browser.
    auth.signOut(instance).catch(() => {});
    return token;
  };
}
