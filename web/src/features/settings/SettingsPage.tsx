import { useEffect, useState } from 'react';
import { useAuth } from '../../features/auth/AuthContext';
import { Card } from '../../components/ui/Card';

type ThemeChoice = 'system' | 'light' | 'dark';

export function SettingsPage() {
  const { user } = useAuth();
  const [theme, setTheme] = useState<ThemeChoice>(
    (localStorage.getItem('sakar.theme') as ThemeChoice | null) ?? 'system',
  );

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', theme);
    }
    localStorage.setItem('sakar.theme', theme);
  }, [theme]);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Settings</h1>
          <p className="sakar-page-subtitle">Session and display preferences.</p>
        </div>
      </div>

      <Card title="Session">
        <dl style={{ display: 'grid', gridTemplateColumns: '160px 1fr', rowGap: 10 }}>
          <dt className="sakar-page-subtitle">Email</dt>
          <dd style={{ margin: 0 }}>{user?.email}</dd>
          <dt className="sakar-page-subtitle">Role</dt>
          <dd style={{ margin: 0 }}>{user?.role}</dd>
          <dt className="sakar-page-subtitle">Organization</dt>
          <dd style={{ margin: 0 }}>{user?.organizationId ?? '— (cross-organization)'}</dd>
          <dt className="sakar-page-subtitle">Permissions</dt>
          <dd style={{ margin: 0 }}>{user?.permissions.join(', ') || '—'}</dd>
        </dl>
      </Card>

      <div style={{ height: 16 }} />

      <Card title="Appearance">
        <div className="sakar-field">
          <label htmlFor="theme-select">Theme</label>
          <select id="theme-select" value={theme} onChange={(e) => setTheme(e.target.value as ThemeChoice)}>
            <option value="system">Match system</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </div>
      </Card>
    </div>
  );
}
