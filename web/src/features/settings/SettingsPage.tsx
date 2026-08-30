import { useAuth } from '../../features/auth/AuthContext';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';

// No theme selector — the platform follows the Sakar Robotics Support
// Portal design system, which is light-only (no dark mode), and this
// application is deliberately kept visually consistent with it.
export function SettingsPage() {
  const { user } = useAuth();

  return (
    <div>
      <PageHeader title="Settings" subtitle="Session and account information." />

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
    </div>
  );
}
