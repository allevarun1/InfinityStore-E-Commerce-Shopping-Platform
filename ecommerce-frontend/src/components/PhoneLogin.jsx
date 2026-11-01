import React, { useEffect, useRef, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useStore } from '../contexts/StoreContext';
import { sendOtp, otpError } from '../lib/phoneAuth';

const RESEND_SECONDS = 30;

/**
 * Login with mobile number + OTP. New numbers get an account automatically, so there is no separate sign-up.
 * Renders nothing until the shop has Firebase set up (store info carries phoneLogin).
 */
export function PhoneLogin({ onSignedIn }) {
  const { info } = useStore();
  const { loginWithPhone } = useAuth();
  const config = info?.phoneLogin;
  const [step, setStep] = useState('phone');
  const [mobile, setMobile] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [resendIn, setResendIn] = useState(0);
  const confirmRef = useRef(null);
  const codeRef = useRef(null);

  useEffect(() => {
    if (resendIn <= 0) return undefined;
    const t = setTimeout(() => setResendIn(s => s - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  useEffect(() => { if (step === 'code') codeRef.current?.focus(); }, [step]);

  if (!config) return null;

  const validMobile = /^[6-9]\d{9}$/.test(mobile);

  const send = (buttonId) => {
    if (!validMobile) { setError('Enter a valid 10-digit mobile number'); return; }
    setBusy(true);
    setError('');
    sendOtp(config, mobile, buttonId)
      .then((confirm) => {
        confirmRef.current = confirm;
        setCode('');
        setStep('code');
        setResendIn(RESEND_SECONDS);
      })
      .catch(err => setError(otpError(err)))
      .finally(() => setBusy(false));
  };

  const verify = (otp) => {
    if (!/^\d{6}$/.test(otp) || !confirmRef.current || busy) return;
    setBusy(true);
    setError('');
    confirmRef.current(otp)
      .then(loginWithPhone)
      .then(user => onSignedIn?.(user))
      .catch(err => { setError(otpError(err)); setBusy(false); });
  };

  return (
    <div className="otp-login">
      {step === 'phone' ? (
        <form onSubmit={e => { e.preventDefault(); send('otp-send'); }} noValidate>
          <label htmlFor="otp-mobile">Mobile number</label>
          <div className="otp-mobile">
            <span aria-hidden="true">+91</span>
            <input id="otp-mobile" type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={10}
              placeholder="10-digit mobile number" value={mobile} aria-invalid={!!error} aria-describedby="otp-help"
              onChange={e => { setMobile(e.target.value.replace(/\D/g, '')); setError(''); }} />
          </div>
          <p id="otp-help" className="otp-help">We'll send a 6-digit OTP by SMS. New here? Your account is created automatically.</p>
          {error && <p className="field-error" role="alert">{error}</p>}
          <button id="otp-send" type="submit" className="primary" disabled={busy || !validMobile}>
            {busy ? 'Sending OTP…' : 'Get OTP'}
          </button>
        </form>
      ) : (
        <form onSubmit={e => { e.preventDefault(); verify(code); }} noValidate>
          <label htmlFor="otp-code">Enter OTP</label>
          <p className="otp-help">
            Sent to +91 {mobile.slice(0, 5)} {mobile.slice(5)}{' '}
            <button type="button" className="otp-link" onClick={() => { setStep('phone'); setError(''); }}>Change</button>
          </p>
          <input id="otp-code" ref={codeRef} className="otp-code" type="text" inputMode="numeric" autoComplete="one-time-code"
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
          <button id="otp-resend" type="button" className="otp-link otp-resend" disabled={busy || resendIn > 0}
            onClick={() => send('otp-resend')}>
            {resendIn > 0 ? `Resend OTP in ${resendIn}s` : 'Resend OTP'}
          </button>
        </form>
      )}
      <div className="otp-or"><span>or sign in with username</span></div>
    </div>
  );
}
