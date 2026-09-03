import { NavLink } from 'react-router-dom';
import { NAV_SECTIONS } from './navConfig';
import { usePermissions } from '../../hooks/usePermissions';
import { useSidebarCollapse } from '../../hooks/useSidebarCollapse';
import { Icon } from '../ui/Icon';
import './layout.css';

// White sidebar, logo lives in the header (TopNav) instead — matching the
// Sakar Robotics Support Portal's own layout, where the sidebar starts
// directly with the nav (no duplicate brand mark inside it).
export function Sidebar() {
  const { hasPermission } = usePermissions();
  const { collapsed, toggle } = useSidebarCollapse();

  return (
    <aside className={`sakar-sidebar${collapsed ? ' sakar-sidebar--collapsed' : ''}`}>
      <nav className="sakar-sidebar-nav" aria-label="Primary">
        {NAV_SECTIONS.map((section) => (
          <div key={section.title} className="sakar-sidebar-section">
            {!collapsed && <div className="sakar-sidebar-section-title">{section.title}</div>}
            {section.items
              .filter((item) => !item.permission || hasPermission(item.permission))
              .map((item) => {
                const ItemIcon = item.icon;
                return (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    title={collapsed ? item.label : undefined}
                    className={({ isActive }) =>
                      'sakar-sidebar-link' + (isActive ? ' sakar-sidebar-link--active' : '')
                    }
                  >
                    <ItemIcon width={16} height={16} aria-hidden="true" />
                    {!collapsed && (
                      <>
                        <span className="sakar-sidebar-link-label">{item.label}</span>
                        {item.dataMode === 'simulated' && <span className="sakar-nav-tag sakar-nav-tag--sim">SIM</span>}
                        {item.dataMode === 'unavailable' && <span className="sakar-nav-tag sakar-nav-tag--planned">SOON</span>}
                      </>
                    )}
                  </NavLink>
                );
              })}
          </div>
        ))}
      </nav>
      <button
        type="button"
        className="sakar-sidebar-collapse-btn"
        onClick={toggle}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {collapsed ? <Icon.chevronRight width={16} height={16} /> : <Icon.chevronLeft width={16} height={16} />}
        {!collapsed && <span>Collapse</span>}
      </button>
    </aside>
  );
}
