import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { usePermissions } from '../../hooks/usePermissions';
import { listAlerts } from '../../api/alerts';
import { Icon } from '../ui/Icon';
import sakarLogo from '../../assets/sakar-logo-full.png';

// Header structure matches the Sakar Robotics Support Portal's own
// AdminLayout.jsx exactly: logo, a vertical separator, then the product
// name on the left; a notifications bell, the signed-in user's email, and
// an explicit Logout button on the right. No breadcrumb/search/system-
// status badge in this row — those live on their own pages instead (the
// Dashboard's System Health card, and each list page's own search field).
export function TopNav() {
  const { user, logout } = useAuth();
  const { hasPermission } = usePermissions();
  const navigate = useNavigate();

  // Real, organization-scoped open-alert count (GET /api/v1/alerts). Fetched
  // silently — a failure here just leaves the badge hidden, since a missed
  // notification count is not worth surfacing as a page-wide error.
  const [attentionCount, setAttentionCount] = useState(0);
  useEffect(() => {
    if (!hasPermission('ROBOT_VIEW')) {
      return;
    }
    let cancelled = false;
    listAlerts(0, 100)
      .then((page) => {
        if (!cancelled) {
          setAttentionCount(page.content.filter((a) => a.status === 'OPEN').length);
        }
      })
      .catch(() => {
        /* silent — see comment above */
      });
    return () => {
      cancelled = true;
    };
  }, [hasPermission]);

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="sakar-topnav">
      <div className="sakar-topnav-brand">
        <img src={sakarLogo} alt="Sakar Robotics" className="sakar-topnav-logo" />
        <div className="sakar-topnav-product">
          <p className="sakar-topnav-product-name">Robot Management</p>
          <p className="sakar-topnav-product-sub">Platform</p>
        </div>
      </div>
      <div className="sakar-topnav-actions">
        <button
          type="button"
          className="sakar-topnav-icon-btn"
          onClick={() => navigate('/alerts')}
          aria-label="Notifications"
          title="Items needing attention"
        >
          <Icon.bell width={19} height={19} />
          {attentionCount > 0 && <span className="sakar-topnav-badge">{attentionCount > 99 ? '99+' : attentionCount}</span>}
        </button>
        {user?.email && <span className="sakar-topnav-email">{user.email}</span>}
        <button type="button" className="sakar-topnav-logout" onClick={handleLogout}>
          <Icon.logout width={16} height={16} />
          <span>Logout</span>
        </button>
      </div>
    </header>
  );
}
