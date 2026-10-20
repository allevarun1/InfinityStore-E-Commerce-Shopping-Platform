import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';

export function Register() {
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  
  const { signup } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const getPasswordStrength = (pass) => {
    if (!pass) return { label: '', class: '' };
    if (pass.length < 8) return { label: 'Weak (min 8 chars)', class: 'weak' };
    const hasLetters = /[a-zA-Z]/.test(pass);
    const hasNumbers = /[0-9]/.test(pass);
    const hasSpecials = /[^A-Za-z0-9]/.test(pass);
    
    if (hasLetters && hasNumbers && hasSpecials) {
      return { label: 'Strong', class: 'strong' };
    } else if (hasLetters && hasNumbers) {
      return { label: 'Medium', class: 'medium' };
    }
    return { label: 'Weak', class: 'weak' };
  };

  const validate = () => {
    const tempErrors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (!form.firstName.trim()) tempErrors.firstName = 'First name is required';
    if (!form.lastName.trim()) tempErrors.lastName = 'Last name is required';
    
    if (form.username.trim().length < 3 || form.username.trim().length > 20) {
      tempErrors.username = 'Username must be between 3 and 20 characters';
    }
    
    if (!emailRegex.test(form.email)) {
      tempErrors.email = 'Please enter a valid email address';
    }
    
    if (form.password.length < 8) {
      tempErrors.password = 'Password must be at least 8 characters long';
    }
    
    if (form.password !== form.confirmPassword) {
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
    const { confirmPassword, ...signupPayload } = form;
    signup(signupPayload)
      .then(() => {
        toast.success('Registration successful. Please log in.');
        navigate('/login', { state: location.state });
      })
      .catch(err => {
        const errorData = err.response?.data;
        if (errorData?.errors) {
          setErrors(errorData.errors);
        } else {
          toast.error(errorData?.message || 'Registration failed');
        }
      })
      .finally(() => {
        setIsSubmitting(false);
      });
  };

  const strength = getPasswordStrength(form.password);

  return (
    <form className="form-card" onSubmit={handleSubmit} style={{ gap: '16px' }}>
      <h1>Create account</h1>
      
      <div className="field-group">
        <label htmlFor="firstName">First Name</label>
        <input 
          id="firstName"
          name="firstName" 
          placeholder="First name"
          value={form.firstName}
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
          value={form.lastName}
          className={errors.lastName ? 'input-invalid' : form.lastName ? 'input-valid' : ''}
          onChange={handleChange}
          required
        />
        {errors.lastName && <span className="field-error">{errors.lastName}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="username">Username</label>
        <input 
          id="username"
          name="username" 
          placeholder="Username" 
          required
          value={form.username}
          className={errors.username ? 'input-invalid' : form.username.length >= 3 ? 'input-valid' : ''}
          onChange={handleChange}
        />
        {errors.username && <span className="field-error">{errors.username}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="email">Email</label>
        <input 
          id="email"
          name="email" 
          type="email" 
          placeholder="Email" 
          required
          value={form.email}
          className={errors.email ? 'input-invalid' : form.email ? 'input-valid' : ''}
          onChange={handleChange}
        />
        {errors.email && <span className="field-error">{errors.email}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="password">Password</label>
        <div className="password-wrapper">
          <input 
            id="password"
            name="password" 
            type={showPassword ? 'text' : 'password'} 
            placeholder="Password" 
            required
            value={form.password}
            className={errors.password ? 'input-invalid' : form.password.length >= 8 ? 'input-valid' : ''}
            onChange={handleChange}
            style={{ width: '100%' }}
          />
          <button 
            type="button" 
            className="password-toggle"
            onClick={() => setShowPassword(!showPassword)}
          >
            <i className={`bi ${showPassword ? 'bi-eye-slash' : 'bi-eye'}`}></i>
          </button>
        </div>
        {strength.label && (
          <span className={`password-strength ${strength.class}`}>
            Password Strength: {strength.label}
          </span>
        )}
        {errors.password && <span className="field-error">{errors.password}</span>}
      </div>

      <div className="field-group">
        <label htmlFor="confirmPassword">Confirm Password</label>
        <input 
          id="confirmPassword"
          name="confirmPassword" 
          type="password" 
          placeholder="Confirm Password" 
          required
          value={form.confirmPassword}
          className={errors.confirmPassword ? 'input-invalid' : form.confirmPassword && !errors.confirmPassword ? 'input-valid' : ''}
          onChange={handleChange}
        />
        {errors.confirmPassword && <span className="field-error">{errors.confirmPassword}</span>}
      </div>
      
      <button type="submit" className="primary" disabled={isSubmitting}>
        {isSubmitting ? 'Registering...' : 'Sign up'}
      </button>
    </form>
  );
}
