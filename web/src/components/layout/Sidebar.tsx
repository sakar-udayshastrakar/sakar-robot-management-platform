import { NavLink } from 'react-router-dom';
import { NAV_SECTIONS } from './navConfig';
import { usePermissions } from '../../hooks/usePermissions';
import './layout.css';

export function Sidebar() {
  const { hasPermission } = usePermissions();

  return (
    <aside className="sakar-sidebar">
      <div className="sakar-sidebar-brand">
        <span className="sakar-sidebar-brand-mark">SR</span>
        <span className="sakar-sidebar-brand-name">Sakar Robotics</span>
      </div>
      <nav className="sakar-sidebar-nav">
        {NAV_SECTIONS.map((section) => (
          <div key={section.title} className="sakar-sidebar-section">
            <div className="sakar-sidebar-section-title">{section.title}</div>
            {section.items
              .filter((item) => !item.permission || hasPermission(item.permission))
              .map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    'sakar-sidebar-link' + (isActive ? ' sakar-sidebar-link--active' : '')
                  }
                >
                  <span>{item.label}</span>
                  {item.dataMode === 'simulated' && <span className="sakar-nav-tag sakar-nav-tag--sim">SIM</span>}
                  {item.dataMode === 'unavailable' && (
                    <span className="sakar-nav-tag sakar-nav-tag--planned">SOON</span>
                  )}
                </NavLink>
              ))}
          </div>
        ))}
      </nav>
    </aside>
  );
}
