import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { ApiRequestError } from '../../api/client';
import sakarLogo from '../../assets/sakar-logo-full.png';
import './auth.css';

// Matches the Sakar Robotics Support Portal's own AdminLoginPage.jsx:
// centered logo, centered title, the same card/input/button system.
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      const from = (location.state as { from?: Location })?.from?.pathname ?? '/dashboard';
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.status === 401 ? 'Invalid email or password.' : err.message);
      } else {
        setError('Unable to reach the Sakar Cloud Backend.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="sakar-auth-page">
      <div className="sakar-auth-card">
        <div className="sakar-auth-brand">
          <img src={sakarLogo} alt="Sakar Robotics" className="sakar-auth-logo" />
          <h1 className="sakar-auth-title">Robot Management Platform</h1>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <div className="sakar-field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="sakar-field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && (
            <div className="sakar-field-error" role="alert" style={{ marginBottom: 16 }}>
              {error}
            </div>
          )}

          <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting} style={{ width: '100%', justifyContent: 'center' }}>
            {submitting ? 'Signing in…' : 'Log in'}
          </button>
        </form>
      </div>
    </div>
  );
}
