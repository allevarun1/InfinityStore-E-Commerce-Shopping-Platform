import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiService } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { Loader } from './Loader';

export function Profile() {
  const [form, setForm] = useState(null);
  const [original, setOriginal] = useState({});
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const { logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  useEffect(() => {
    setLoading(true);
    ApiService.profile()
      .then((profile) => {
        setOriginal(profile);
        setForm(profile);
      })
      .catch(() => toast.error('Failed to load profile details'))
      .finally(() => setLoading(false));
  }, [toast]);

  const validate = () => {
    const tempErrors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!form.firstName?.trim()) tempErrors.firstName = 'First name is required';
    if (!form.lastName?.trim()) tempErrors.lastName = 'Last name is required';
    if (!emailRegex.test(form.email || '')) {
      tempErrors.email = 'Please enter a valid email address';
    }
    if (form.phone && !/^\d{10}$/.test(form.phone)) {
      tempErrors.phone = 'Phone number must be exactly 10 digits';
    }
    if (form.password && form.password.length < 8) {
      tempErrors.password = 'New password must be at least 8 characters long';
    }

    if ((form.password || (form.email && form.email !== original.email)) && !form.currentPassword) {
      tempErrors.currentPassword = 'Enter your current password to change your email or password';
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

  const save = (e) => {
    e.preventDefault();
    if (!validate()) return;

    setIsSubmitting(true);
    ApiService.updateProfile(form)
      .then((saved) => {
        setOriginal(saved);
        setForm(prev => ({ ...prev, password: '', currentPassword: '' }));
        toast.success('Profile updated successfully');
      })
      .catch((err) => toast.error(err.response?.data?.message || 'Failed to update profile'))
      .finally(() => setIsSubmitting(false));
  };

  const deleteAccount = () => {
    if (window.confirm('Are you sure you want to delete your InfinityStore account? This action cannot be undone.')) {
      ApiService.deleteAccount()
        .then(() => {
          toast.success('Account deleted successfully');
          logout();
        })
        .catch(() => toast.error('Failed to delete account'));
    }
  };

  if (loading || !form) {
    return <Loader message="Loading profile..." />;
  }

  return (
    <form className="form-card" onSubmit={save} style={{ gap: '16px' }}>
      <h1>Your account</h1>
      <div className="account-links">
        <Link className="btn btn-outline" to="/orders"><i className="bi bi-box-seam" aria-hidden="true"></i> Your orders</Link>
        <Link className="btn btn-outline" to="/wishlist"><i className="bi bi-heart" aria-hidden="true"></i> Wishlist</Link>
        <button type="button" className="btn btn-outline" onClick={() => { logout(); navigate('/'); }}>
          <i className="bi bi-box-arrow-right" aria-hidden="true"></i> Sign out
        </button>
      </div>
      
      <div className="field-group">
        <label htmlFor="firstName">First Name</label>
        <input 
          id="firstName"
          name="firstName" 
          placeholder="First name" 
          value={form.firstName || ''}
          className={errors.firstName ? 'input-invalid' : form.firstName ? 'input-valid' : ''}
          onChange={handleChange}
          required
        />
        {errors.firstName && <span className="field-error">{errors.firstName}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="lastName">Last Name</label>
        <input 
          id="lastName"
          name="lastName" 
          placeholder="Last name" 
          value={form.lastName || ''}
          className={errors.lastName ? 'input-invalid' : form.lastName ? 'input-valid' : ''}
          onChange={handleChange}
          required
        />
        {errors.lastName && <span className="field-error">{errors.lastName}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="email">Email</label>
        <input 
          id="email"
          name="email" 
          type="email" 
          placeholder="Email" 
          value={form.email || ''}
          className={errors.email ? 'input-invalid' : form.email ? 'input-valid' : ''}
          onChange={handleChange}
          required
        />
        {errors.email && <span className="field-error">{errors.email}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="phone">Phone Number</label>
        <input 
          id="phone"
          name="phone" 
          placeholder="Phone (10 digits)" 
          value={form.phone || ''}
          className={errors.phone ? 'input-invalid' : (form.phone && /^\d{10}$/.test(form.phone)) ? 'input-valid' : ''}
          onChange={handleChange}
        />
        {errors.phone && <span className="field-error">{errors.phone}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="addresses">Delivery Address</label>
        <textarea 
          id="addresses"
          name="addresses" 
          placeholder="Addresses" 
          value={form.addresses || ''}
          onChange={handleChange}
        />
      </div>

      <div className="field-group">
        <label htmlFor="password">Change Password</label>
        <input 
          id="password"
          name="password" 
          type="password" 
          placeholder="New password (leave blank to keep current)" 
          value={form.password || ''}
          className={errors.password ? 'input-invalid' : ''}
          onChange={handleChange}
        />
        {errors.password && <span className="field-error">{errors.password}</span>}
      </div>
      <div className="field-group">
        <label htmlFor="currentPassword">Current password</label>
        <input
          id="currentPassword"
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          placeholder="Required only when changing email or password"
          value={form.currentPassword || ''}
          className={errors.currentPassword ? 'input-invalid' : ''}
          onChange={handleChange}
        />
        {errors.currentPassword && <span className="field-error">{errors.currentPassword}</span>}
      </div>
      
      <button type="submit" className="primary" disabled={isSubmitting}>
        {isSubmitting ? 'Saving...' : 'Save profile'}
      </button>
      <button type="button" className="danger-btn" onClick={deleteAccount}>Delete account</button>
    </form>
  );
}
