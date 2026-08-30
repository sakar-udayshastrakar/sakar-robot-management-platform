import type { ComponentType, SVGProps } from 'react';
import { Icon } from '../ui/Icon';
import type { PermissionCode } from '../../types/permissions';

export type DataMode = 'live' | 'simulated' | 'unavailable';

export interface NavItem {
  label: string;
  path: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
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
    items: [{ label: 'Dashboard', path: '/dashboard', icon: Icon.dashboard, permission: 'ROBOT_VIEW', dataMode: 'live' }],
  },
  {
    title: 'Fleet',
    items: [
      { label: 'Organizations', path: '/organizations', icon: Icon.building, permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Sites', path: '/sites', icon: Icon.mapPin, permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Robots', path: '/robots', icon: Icon.robot, permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Fleet Map', path: '/fleet', icon: Icon.mapEmpty, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
    ],
  },
  {
    title: 'Operations',
    items: [
      { label: 'Tasks', path: '/tasks', icon: Icon.listCheck, permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Cleaning', path: '/cleaning', icon: Icon.spray, permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Telemetry', path: '/telemetry', icon: Icon.gauge, permission: 'ROBOT_VIEW', dataMode: 'simulated' },
      { label: 'Alerts', path: '/alerts', icon: Icon.alertTriangle, permission: 'ROBOT_VIEW', dataMode: 'live' },
      { label: 'Events', path: '/events', icon: Icon.activity, permission: 'ROBOT_VIEW', dataMode: 'simulated' },
      { label: 'Errors', path: '/errors', icon: Icon.xCircle, permission: 'ROBOT_VIEW', dataMode: 'simulated' },
    ],
  },
  {
    title: 'Insights',
    items: [
      { label: 'Logs', path: '/logs', icon: Icon.fileText, permission: 'ROBOT_LOG_VIEW', dataMode: 'unavailable' },
      { label: 'Analytics', path: '/analytics', icon: Icon.barChart, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
    ],
  },
  {
    title: 'Administration',
    items: [
      { label: 'Users', path: '/users', icon: Icon.users, permission: 'USER_MANAGE', dataMode: 'live' },
      // Real backend gate is USER_MANAGE (RoleController), not ROLE_MANAGE —
      // see the matching comment on the /roles route in App.tsx.
      { label: 'Roles', path: '/roles', icon: Icon.shield, permission: 'USER_MANAGE', dataMode: 'live' },
      { label: 'Permissions', path: '/permissions', icon: Icon.key, permission: 'ROLE_MANAGE', dataMode: 'live' },
      { label: 'Audit Logs', path: '/audit', icon: Icon.clipboard, permission: 'AUDIT_VIEW', dataMode: 'live' },
      { label: 'Settings', path: '/settings', icon: Icon.settings, dataMode: 'live' },
    ],
  },
];

export const ALL_NAV_ITEMS: NavItem[] = NAV_SECTIONS.flatMap((s) => s.items);
