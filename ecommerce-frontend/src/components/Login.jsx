import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';

export function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showForgot, setShowForgot] = useState(false);
  const [forgotStep, setForgotStep] = useState('request');
  const [forgotEmail, setForgotEmail] = useState('');
  const [resetCode, setResetCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [forgotMessage, setForgotMessage] = useState('');
  const [forgotSubmitting, setForgotSubmitting] = useState(false);

  const { login: authLogin, forgotPassword, resetPassword } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const validate = () => {
    const tempErrors = {};
    if (username.trim().length < 3) {
      tempErrors.username = 'Username must be at least 3 characters long';
    }
    if (password.length < 6) {
      tempErrors.password = 'Password must be at least 6 characters long';
    }
    setErrors(tempErrors);
    return Object.keys(tempErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    setIsSubmitting(true);
    authLogin(username, password)
      .then((res) => {
        toast.success('Login successful');
        navigate('/');
      })
      .catch((err) => {
        const errorMsg = err.response?.data?.message || 'Invalid username or password';
        toast.error(errorMsg);
      })
      .finally(() => {
        setIsSubmitting(false);
      });
  };

  const clearForgotFlow = () => {
    setShowForgot(false);
    setForgotStep('request');
    setForgotEmail('');
    setResetCode('');
    setNewPassword('');
    setConfirmPassword('');
    setForgotMessage('');
    setShowNewPassword(false);
    setShowConfirmPassword(false);
  };

  const handleForgotSubmit = (e) => {
    e.preventDefault();
    if (!forgotEmail.trim()) {
      toast.error('Please enter your email');
      return;
    }

    setForgotSubmitting(true);
    forgotPassword(forgotEmail.trim())
      .then(() => {
        setForgotStep('reset');
        setForgotMessage('If an account exists for that email, a reset link has been sent. Open it, or paste the token below.');
        toast.success('Check your email for the reset link');
      })
      .catch((err) => {
        const errorMsg = err.response?.data?.message || 'Failed to send reset code';
        toast.error(errorMsg);
      })
      .finally(() => {
        setForgotSubmitting(false);
      });
  };

  const handleResetSubmit = (e) => {
    e.preventDefault();
    if (!resetCode.trim()) {
      toast.error('Please enter the reset code');
      return;
    }
    if (newPassword.length < 8) {
      toast.error('New password must be at least 8 characters long');
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error('New password and confirm password do not match');
      return;
    }

    setForgotSubmitting(true);
    resetPassword({
      email: forgotEmail.trim(),
      code: resetCode.trim(),
      newPassword,
      confirmPassword
    })
      .then(() => {
        clearForgotFlow();
        setPassword('');
        toast.success('Password reset successful. Login with your same username and new password.');
      })
      .catch((err) => {
        const errorMsg = err.response?.data?.message || 'Failed to reset password';
        toast.error(errorMsg);
      })
      .finally(() => {
        setForgotSubmitting(false);
      });
  };

  return (
    <div className="form-card">
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px', width: '100%' }}>
        <h1>Login</h1>
        
        <div className="field-group">
          <label htmlFor="username">Username</label>
          <input 
            id="username"
            name="username" 
            placeholder="Enter your username" 
            required 
            value={username}
            className={errors.username ? 'input-invalid' : username.length >= 3 ? 'input-valid' : ''}
            onChange={e => {
              setUsername(e.target.value);
              if (errors.username) setErrors(prev => ({ ...prev, username: '' }));
            }}
          />
          {errors.username && <span className="field-error">{errors.username}</span>}
        </div>

        <div className="field-group">
          <label htmlFor="password">Password</label>
          <div className="password-wrapper">
            <input 
              id="password"
              name="password" 
              type={showPassword ? 'text' : 'password'} 
              placeholder="Enter your password" 
              required 
              value={password}
              className={errors.password ? 'input-invalid' : password.length >= 6 ? 'input-valid' : ''}
              onChange={e => {
                setPassword(e.target.value);
                if (errors.password) setErrors(prev => ({ ...prev, password: '' }));
              }}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              title={showPassword ? 'Hide password' : 'Show password'}
            >
              <i className={`bi ${showPassword ? 'bi-eye-slash' : 'bi-eye'}`}></i>
            </button>
          </div>
          {errors.password && <span className="field-error">{errors.password}</span>}
        </div>
        
        <button type="submit" className="primary" disabled={isSubmitting}>
          {isSubmitting ? 'Logging in...' : 'Login'}
        </button>

        <button type="button" className="ghost" onClick={() => showForgot ? clearForgotFlow() : setShowForgot(true)}>
          {showForgot ? 'Cancel forgot password' : 'Forgot password?'}
        </button>

        <Link to="/register" style={{ textAlign: 'center', marginTop: '8px' }}>Create account</Link>
      </form>

      {showForgot && forgotStep === 'request' && (
        <form onSubmit={handleForgotSubmit} className="forgot-password-container">
          <h3>Forgot Password</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', margin: '0 0 8px 0' }}>
            Enter your registered email address. We will send a reset code to that email.
          </p>
          <div className="field-group">
            <label htmlFor="forgotEmail">Email</label>
            <input
              id="forgotEmail"
              type="email"
              placeholder="name@example.com"
              required
              value={forgotEmail}
              onChange={e => setForgotEmail(e.target.value)}
            />
          </div>
          <button type="submit" className="primary" disabled={forgotSubmitting}>
            {forgotSubmitting ? 'Sending...' : 'Send Reset Code'}
          </button>
        </form>
      )}

      {showForgot && forgotStep === 'reset' && (
        <form onSubmit={handleResetSubmit} className="forgot-password-container">
          <h3>Reset Password</h3>
          {forgotMessage && <p className="message" style={{ margin: '0 0 8px 0', fontSize: '0.85rem' }}>{forgotMessage}</p>}
          <div className="field-group">
            <label htmlFor="resetEmail">Email</label>
            <input
              id="resetEmail"
              type="email"
              value={forgotEmail}
              onChange={e => setForgotEmail(e.target.value)}
              required
            />
          </div>
          <div className="field-group">
            <label htmlFor="resetCode">Reset Code</label>
            <input
              id="resetCode"
              placeholder="Enter code from email"
              value={resetCode}
              onChange={e => setResetCode(e.target.value)}
              required
            />
          </div>
          <div className="field-group">
            <label htmlFor="newPassword">New Password</label>
            <div className="password-wrapper">
              <input
                id="newPassword"
                type={showNewPassword ? 'text' : 'password'}
                placeholder="New password"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                required
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowNewPassword(!showNewPassword)}
                aria-label={showNewPassword ? 'Hide new password' : 'Show new password'}
                title={showNewPassword ? 'Hide new password' : 'Show new password'}
              >
                <i className={`bi ${showNewPassword ? 'bi-eye-slash' : 'bi-eye'}`}></i>
              </button>
            </div>
          </div>
          <div className="field-group">
            <label htmlFor="confirmNewPassword">Confirm New Password</label>
            <div className="password-wrapper">
              <input
                id="confirmNewPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                placeholder="Confirm new password"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                required
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
                title={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
              >
                <i className={`bi ${showConfirmPassword ? 'bi-eye-slash' : 'bi-eye'}`}></i>
              </button>
            </div>
          </div>
          <button type="submit" className="primary" disabled={forgotSubmitting}>
            {forgotSubmitting ? 'Resetting...' : 'Reset Password'}
          </button>
          <button type="button" className="ghost" onClick={() => setForgotStep('request')}>
            Send code again
          </button>
        </form>
      )}
    </div>
  );
}
