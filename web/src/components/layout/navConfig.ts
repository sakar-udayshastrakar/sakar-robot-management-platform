import type { PermissionCode } from '../../types/permissions';

export type DataMode = 'live' | 'simulated' | 'unavailable';

export interface NavItem {
  label: string;
  path: string;
  permission?: PermissionCode;
  dataMode: DataMode;
}

export interface NavSection {
  title: string;
  items: NavItem[];
}

export const NAV_SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    items: [{ label: 'Dashboard', path: '/dashboard', permission: 'ROBOT_VIEW', dataMode: 'live' }],
  },
  {
    title: 'Fleet',
    items: [
      { label: 'Organizations', path: '/organizations', permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Sites', path: '/sites', permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Robots', path: '/robots', permission: 'ROBOT_VIEW', dataMode: 'live' },
    ],
  },
  {
    title: 'Operations',
    items: [
      { label: 'Tasks', path: '/tasks', permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
      { label: 'Cleaning', path: '/cleaning', permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
      { label: 'Telemetry', path: '/telemetry', permission: 'ROBOT_VIEW', dataMode: 'simulated' },
      { label: 'Alerts', path: '/alerts', permission: 'ROBOT_VIEW', dataMode: 'simulated' },
      { label: 'Events', path: '/events', permission: 'ROBOT_VIEW', dataMode: 'simulated' },
      { label: 'Errors', path: '/errors', permission: 'ROBOT_VIEW', dataMode: 'simulated' },
    ],
  },
  {
    title: 'Insights',
    items: [
      { label: 'Logs', path: '/logs', permission: 'ROBOT_LOG_VIEW', dataMode: 'unavailable' },
      { label: 'Analytics', path: '/analytics', permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
    ],
  },
  {
    title: 'Administration',
    items: [
      { label: 'Users', path: '/users', permission: 'USER_MANAGE', dataMode: 'unavailable' },
      { label: 'Roles', path: '/roles', permission: 'ROLE_MANAGE', dataMode: 'unavailable' },
      { label: 'Audit Logs', path: '/audit', permission: 'AUDIT_VIEW', dataMode: 'live' },
      { label: 'Settings', path: '/settings', dataMode: 'live' },
    ],
  },
];
