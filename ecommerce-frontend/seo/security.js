// Security headers for HTML produced by the Functions. Pages applies public/_headers to static files, and Function
// responses copy those headers from the app shell; these values are the safety net if that copy ever comes back empty.
// seo.test.mjs fails if this list drifts from the "/*" block of public/_headers.
export const SECURITY_HEADERS = {
  'Strict-Transport-Security': 'max-age=31536000; includeSubDomains',
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(self)',
  'Content-Security-Policy': "default-src 'self'; script-src 'self' https://www.googletagmanager.com https://connect.facebook.net https://www.google.com https://www.gstatic.com https://apis.google.com https://checkout.razorpay.com https://accounts.google.com; style-src 'self' 'unsafe-inline' https://accounts.google.com; font-src 'self'; img-src 'self' data: blob: https:; media-src 'self' blob: https:; frame-src https://www.youtube-nocookie.com https://www.google.com https://*.firebaseapp.com https://api.razorpay.com https://checkout.razorpay.com https://accounts.google.com; connect-src 'self' https:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
};
