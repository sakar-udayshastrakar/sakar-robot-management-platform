import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { Breadcrumbs } from './Breadcrumbs';

export function TopNav() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="sakar-topnav">
      <Breadcrumbs />
      <div className="sakar-topnav-user">
        <div className="sakar-topnav-user-info">
          <span className="sakar-topnav-user-email">{user?.email}</span>
          <span className="sakar-topnav-user-role">{user?.role}</span>
        </div>
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={handleLogout}>
          Log out
        </button>
      </div>
    </header>
  );
}
