import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { NAV_SECTIONS, type NavItem } from './navConfig';
import { usePermissions } from '../../hooks/usePermissions';
import { useSidebarCollapse } from '../../hooks/useSidebarCollapse';
import { Icon } from '../ui/Icon';
import './layout.css';

function DataModeTag({ dataMode }: { dataMode: NavItem['dataMode'] }) {
  if (dataMode === 'simulated') return <span className="sakar-nav-tag sakar-nav-tag--sim">SIM</span>;
  if (dataMode === 'unavailable') return <span className="sakar-nav-tag sakar-nav-tag--planned">SOON</span>;
  return null;
}

// A leaf item always has `path` (enforced by how NAV_SECTIONS is built —
// only a group parent omits it); the fallback here only satisfies the type.
function NavLeaf({ item, collapsed }: { item: NavItem; collapsed: boolean }) {
  const ItemIcon = item.icon;
  return (
    <NavLink
      to={item.path ?? '#'}
      title={collapsed ? item.label : undefined}
      className={({ isActive }) => 'sakar-sidebar-link' + (isActive ? ' sakar-sidebar-link--active' : '')}
    >
      <ItemIcon width={16} height={16} aria-hidden="true" />
      {!collapsed && (
        <>
          <span className="sakar-sidebar-link-label">{item.label}</span>
          <DataModeTag dataMode={item.dataMode} />
        </>
      )}
    </NavLink>
  );
}

// Renders a group with nested children (Account Permission Platform) as an
// expandable/collapsible entry — same link styling/tokens as a leaf item,
// plus a chevron and an indented child list. Starts expanded when the
// current route is one of its children, so navigating in never hides the
// active page's own group.
function NavGroup({ item, collapsed }: { item: NavItem; collapsed: boolean }) {
  const { hasPermission } = usePermissions();
  const location = useLocation();
  const children = (item.children ?? []).filter((child) => !child.permission || hasPermission(child.permission));
  const containsActive = children.some((child) => child.path && location.pathname.startsWith(child.path));
  const [open, setOpen] = useState(containsActive);
  const ItemIcon = item.icon;

  if (children.length === 0) {
    return null;
  }

  if (collapsed) {
    // Collapsed rail has no room for a submenu — render each child directly,
    // same as the flat rendering every other section already uses.
    return (
      <>
        {children.map((child) => (
          <NavLeaf key={child.path} item={child} collapsed={collapsed} />
        ))}
      </>
    );
  }

  return (
    <div className="sakar-sidebar-group">
      <button
        type="button"
        className={'sakar-sidebar-link sakar-sidebar-group-toggle' + (containsActive ? ' sakar-sidebar-link--active' : '')}
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        <ItemIcon width={16} height={16} aria-hidden="true" />
        <span className="sakar-sidebar-link-label">{item.label}</span>
        <DataModeTag dataMode={item.dataMode} />
        {open ? <Icon.chevronDown width={14} height={14} aria-hidden="true" /> : <Icon.chevronRight width={14} height={14} aria-hidden="true" />}
      </button>
      {open && (
        <div className="sakar-sidebar-subnav">
          {children.map((child) => (
            <NavLeaf key={child.path} item={child} collapsed={collapsed} />
          ))}
        </div>
      )}
    </div>
  );
}

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
            {!collapsed && section.title && <div className="sakar-sidebar-section-title">{section.title}</div>}
            {section.items
              .filter((item) => !item.permission || hasPermission(item.permission))
              .map((item) =>
                item.children ? (
                  <NavGroup key={item.label} item={item} collapsed={collapsed} />
                ) : (
                  <NavLeaf key={item.path} item={item} collapsed={collapsed} />
                ),
              )}
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
