import React, { useState } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';

export function ResetPassword() {
  const [searchParams] = useSearchParams();
  const [form, setForm] = useState({
    email: searchParams.get('email') || '',
    code: searchParams.get('token') || searchParams.get('code') || '',
    newPassword: '',
    confirmPassword: ''
  });
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [success, setSuccess] = useState(false);

  const { resetPassword } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const validate = () => {
    const tempErrors = {};
    if (!form.email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      tempErrors.email = 'Enter a valid email address';
    }
    if (!form.code.trim() || form.code.trim().length < 20) {
      tempErrors.code = 'Open the link from your email, or paste the full reset token';
    }
    if (form.newPassword.length < 8) {
      tempErrors.newPassword = 'Password must be at least 8 characters';
    }
    if (form.newPassword !== form.confirmPassword) {
      tempErrors.confirmPassword = 'Passwords do not match';
    }
    setErrors(tempErrors);
    return Object.keys(tempErrors).length === 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    setIsSubmitting(true);
    resetPassword(form)
      .then(() => {
        toast.success('Password reset successfully! You can now login.');
        setSuccess(true);
        setTimeout(() => navigate('/login'), 2000);
      })
      .catch((err) => {
        const msg = err.response?.data?.message || 'Failed to reset password. The link may have expired.';
        toast.error(msg);
      })
      .finally(() => {
        setIsSubmitting(false);
      });
  };

  if (success) {
    return (
      <div className="form-card" style={{ textAlign: 'center' }}>
        <div style={{ fontSize: '3rem', color: 'var(--success-color)', marginBottom: '16px' }}>
          <i className="bi bi-check-circle-fill"></i>
        </div>
        <h1>Password Reset!</h1>
        <p>Your password has been changed successfully. Redirecting to login...</p>
        <Link to="/login" className="primary link-button" style={{ marginTop: '16px' }}>Go to Login</Link>
      </div>
    );
  }

  return (
    <div className="form-card">
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px', width: '100%' }}>
        <div style={{ textAlign: 'center', marginBottom: '8px' }}>
          <div style={{ fontSize: '2.5rem', color: 'var(--accent-color)', marginBottom: '8px' }}>
            <i className="bi bi-shield-lock"></i>
          </div>
          <h1>Reset Password</h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginTop: '8px' }}>
            Choose a new password. Use the link from your email; it expires after 30 minutes.
          </p>
        </div>

        <div className="field-group">
          <label htmlFor="reset-email">Email Address</label>
          <input
            id="reset-email"
            name="email"
            type="email"
            placeholder="your@email.com"
            required
            value={form.email}
            className={errors.email ? 'input-invalid' : form.email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? 'input-valid' : ''}
            onChange={handleChange}
          />
          {errors.email && <span className="field-error">{errors.email}</span>}
        </div>

        <div className="field-group">
          <label htmlFor="reset-code">Reset token</label>
          <input
            id="reset-code"
            name="code"
            placeholder="Filled in automatically from your email link"
            required
            value={form.code}
            className={errors.code ? 'input-invalid' : form.code.length >= 20 ? 'input-valid' : ''}
            onChange={handleChange}
            autoComplete="off"
          />
          {errors.code && <span className="field-error">{errors.code}</span>}
        </div>

        <div className="field-group">
          <label htmlFor="reset-newPassword">New Password</label>
          <div className="password-wrapper">
            <input
              id="reset-newPassword"
              name="newPassword"
              type={showPassword ? 'text' : 'password'}
              placeholder="Minimum 8 characters"
              required
              value={form.newPassword}
              className={errors.newPassword ? 'input-invalid' : form.newPassword.length >= 8 ? 'input-valid' : ''}
              onChange={handleChange}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword(!showPassword)}
              tabIndex={-1}
            >
              <i className={`bi ${showPassword ? 'bi-eye-slash' : 'bi-eye'}`}></i>
            </button>
          </div>
          {errors.newPassword && <span className="field-error">{errors.newPassword}</span>}
        </div>

        <div className="field-group">
          <label htmlFor="reset-confirmPassword">Confirm Password</label>
          <div className="password-wrapper">
            <input
              id="reset-confirmPassword"
              name="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              placeholder="Re-enter your password"
              required
              value={form.confirmPassword}
              className={errors.confirmPassword ? 'input-invalid' : (form.confirmPassword.length >= 8 && form.confirmPassword === form.newPassword) ? 'input-valid' : ''}
              onChange={handleChange}
            />
          </div>
          {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
        </div>

        <button type="submit" className="primary" disabled={isSubmitting}>
          {isSubmitting ? 'Resetting...' : 'Reset Password'}
        </button>

        <Link to="/login" style={{ textAlign: 'center', marginTop: '8px' }}>Back to Login</Link>
      </form>
    </div>
  );
}
