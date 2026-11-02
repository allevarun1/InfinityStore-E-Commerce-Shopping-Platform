import React, { useEffect, useRef, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useStore } from '../contexts/StoreContext';
import { showGoogleButton, isAndroid, openTruecaller } from '../lib/quickLogin';
import { PhoneLogin } from './PhoneLogin';

const RESEND_SECONDS = 30;
const TRUECALLER_WAIT_MS = 60000;
const errorText = (err, fallback) => err?.response?.data?.message || err?.message || fallback;

/**
 * Passwordless sign-in: Continue with Google, Truecaller one-tap (Android) and OTP on WhatsApp. New shoppers get an
 * account automatically. Each option appears only when the shop has set it up; with none set up this renders nothing.
 */
export function QuickLogin({ onSignedIn }) {
  const { info } = useStore();
  const options = info?.login || {};
  const googleId = options.googleClientId;
  const truecaller = options.truecaller;
  const whatsapp = !!options.whatsappOtp;
  const firebasePhone = !whatsapp && !!info?.phoneLogin;
  const android = isAndroid();

  if (!googleId && !truecaller && !whatsapp && !firebasePhone) return null;

  return (
    <div className="quick-login">
      {googleId && <GoogleButton clientId={googleId} onSignedIn={onSignedIn} />}
      {truecaller && android && <TruecallerButton config={truecaller} onSignedIn={onSignedIn} />}
      {truecaller && (
        <p className="ql-note">Truecaller one-tap works only on Android phones that have the Truecaller app.</p>
      )}
      {whatsapp && (googleId || (truecaller && android)) && <div className="otp-or"><span>or get an OTP on WhatsApp</span></div>}
      {whatsapp && <WhatsAppOtp onSignedIn={onSignedIn} />}
      {firebasePhone && <PhoneLogin onSignedIn={onSignedIn} />}
      {!firebasePhone && <div className="otp-or"><span>or sign in with username</span></div>}
    </div>
  );
}

function GoogleButton({ clientId, onSignedIn }) {
  const { loginWithGoogle } = useAuth();
  const el = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let live = true;
    showGoogleButton(el.current, clientId, (credential) => {
      if (!live) return;
      setBusy(true);
      setError('');
      loginWithGoogle(credential)
        .then(user => onSignedIn?.(user))
        .catch(err => { setError(errorText(err, 'Google sign-in failed. Please try again.')); setBusy(false); });
    }).catch(() => live && setFailed(true));
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId]);

  if (failed) return null;
  return (
    <div className="ql-google">
      <div ref={el} className="ql-google-btn" aria-busy={busy} />
      {busy && <p className="otp-help" role="status">Signing you in…</p>}
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}

function TruecallerButton({ config, onSignedIn }) {
  const { startTruecaller, pollTruecaller } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const timer = useRef(null);

  useEffect(() => () => clearTimeout(timer.current), []);

  const start = async () => {
    setBusy(true);
    setError('');
    let nonce;
    try {
      nonce = await startTruecaller();
    } catch (err) {
      setError(errorText(err, 'Truecaller is not available right now.'));
      setBusy(false);
      return;
    }
    openTruecaller(config, nonce);
    // If the page still has focus, the Truecaller app did not open (not installed).
    timer.current = setTimeout(() => {
      if (document.hasFocus()) {
        setError('Truecaller app not found on this phone. Please use Google or WhatsApp OTP.');
        setBusy(false);
        return;
      }
      const until = Date.now() + TRUECALLER_WAIT_MS;
      const tick = async () => {
        try {
          const status = await pollTruecaller(nonce);
          if (status === 'VERIFIED') { onSignedIn?.(); return; }
          if (status === 'REJECTED') { setError('No problem. Choose another way to sign in.'); setBusy(false); return; }
          if (Date.now() > until) { setError('Truecaller did not respond. Please try again.'); setBusy(false); return; }
          timer.current = setTimeout(tick, document.hidden ? 1500 : 800);
        } catch (err) {
          setError(errorText(err, 'Truecaller sign-in did not complete.'));
          setBusy(false);
        }
      };
      tick();
    }, 700);
  };

  return (
    <div className="ql-truecaller">
      <button type="button" className="ql-tc-btn" onClick={start} disabled={busy}>
        <i className="bi bi-telephone-fill" aria-hidden="true"></i> {busy ? 'Waiting for Truecaller…' : 'Continue with Truecaller'}
      </button>
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}

function WhatsAppOtp({ onSignedIn }) {
  const { sendWhatsAppOtp, loginWithWhatsAppOtp } = useAuth();
  const [step, setStep] = useState('phone');
  const [mobile, setMobile] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [resendIn, setResendIn] = useState(0);
  const codeRef = useRef(null);

  useEffect(() => {
    if (resendIn <= 0) return undefined;
    const t = setTimeout(() => setResendIn(s => s - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  useEffect(() => { if (step === 'code') codeRef.current?.focus(); }, [step]);

  const validMobile = /^[6-9]\d{9}$/.test(mobile);

  const send = () => {
    if (!validMobile) { setError('Enter a valid 10-digit mobile number'); return; }
    setBusy(true);
    setError('');
    sendWhatsAppOtp(mobile)
      .then(() => { setCode(''); setStep('code'); setResendIn(RESEND_SECONDS); })
      .catch(err => setError(errorText(err, 'Could not send the OTP. Please try again.')))
      .finally(() => setBusy(false));
  };

  const verify = (otp) => {
    if (!/^\d{6}$/.test(otp) || busy) return;
    setBusy(true);
    setError('');
    loginWithWhatsAppOtp(mobile, otp)
      .then(user => onSignedIn?.(user))
      .catch(err => { setError(errorText(err, 'That OTP is not right.')); setBusy(false); });
  };

  return (
    <div className="otp-login">
      {step === 'phone' ? (
        <form onSubmit={e => { e.preventDefault(); send(); }} noValidate>
          <label htmlFor="wa-mobile">Mobile number</label>
          <div className="otp-mobile">
            <span aria-hidden="true">+91</span>
            <input id="wa-mobile" type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={10}
              placeholder="10-digit WhatsApp number" value={mobile} aria-invalid={!!error} aria-describedby="wa-help"
              onChange={e => { setMobile(e.target.value.replace(/\D/g, '')); setError(''); }} />
          </div>
          <p id="wa-help" className="otp-help">We'll send a 6-digit OTP on WhatsApp. New here? Your account is created automatically.</p>
          {error && <p className="field-error" role="alert">{error}</p>}
          <button type="submit" className="primary" disabled={busy || !validMobile}>
            <i className="bi bi-whatsapp" aria-hidden="true"></i> {busy ? 'Sending OTP…' : 'Get OTP on WhatsApp'}
          </button>
        </form>
      ) : (
        <form onSubmit={e => { e.preventDefault(); verify(code); }} noValidate>
          <label htmlFor="wa-code">Enter OTP</label>
          <p className="otp-help">
            Sent on WhatsApp to +91 {mobile.slice(0, 5)} {mobile.slice(5)}{' '}
            <button type="button" className="otp-link" onClick={() => { setStep('phone'); setError(''); }}>Change</button>
          </p>
          <input id="wa-code" ref={codeRef} className="otp-code" type="text" inputMode="numeric" autoComplete="one-time-code"
            maxLength={6} placeholder="••••••" value={code} aria-invalid={!!error}
            onChange={e => {
              const v = e.target.value.replace(/\D/g, '');
              setCode(v);
              setError('');
              if (v.length === 6) verify(v);
            }} />
          {error && <p className="field-error" role="alert">{error}</p>}
          <button type="submit" className="primary" disabled={busy || code.length !== 6}>
            {busy ? 'Verifying…' : 'Verify and continue'}
          </button>
          <button type="button" className="otp-link otp-resend" disabled={busy || resendIn > 0} onClick={send}>
            {resendIn > 0 ? `Resend OTP in ${resendIn}s` : 'Resend OTP'}
          </button>
        </form>
      )}
    </div>
  );
}
