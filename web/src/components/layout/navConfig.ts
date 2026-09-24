import type { ComponentType, SVGProps } from 'react';
import { Icon } from '../ui/Icon';
import type { PermissionCode } from '../../types/permissions';

export type DataMode = 'live' | 'simulated' | 'unavailable';

export interface NavItem {
  label: string;
  // Omittable only for a group parent (one that has `children`) — a group
  // heading itself is not a route.
  path?: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  permission?: PermissionCode;
  dataMode: DataMode;
  // Optional one level of nested items. A parent with children renders as
  // an expandable group, never itself as a NavLink — see Sidebar.tsx.
  children?: NavItem[];
}

export interface NavSection {
  title: string;
  items: NavItem[];
}

// Mirrors the reference product's own top-level structure at the user's
// explicit request: every entry is an expandable icon-group at the same
// level, no section-divider headers — one flat NavSection, not several.
// Dashboard/Robot Management/Account Permissions/New Resource Configuration/
// Store Management/OTA Management/Operation And Maintenance Platform (all but
// its Remote log sub-item)/IoT Platform/Open Platform/Operational Dashboard
// are wired to real, existing pages/APIs — Operational Dashboard's mileage,
// call-count, and hotel-room figures are the one exception: no
// distance/odometer, call/summon, or room concept exists anywhere in this
// codebase, so those render "Not tracked" rather than a fabricated number
// (see DashboardService's own Javadoc). Learning Center Platform has no
// Sakar backend behind it yet — it renders an honest "not connected"
// placeholder (PlannedFeaturePage, dataMode 'unavailable') rather than
// fabricated data, matching this app's existing convention (see the
// pre-existing Analytics
// entry). Building these out for real is separately scoped, later work.
export const NAV_SECTIONS: NavSection[] = [
  {
    title: '',
    items: [
      {
        label: 'Dashboard',
        icon: Icon.dashboard,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'Dashboard', path: '/dashboard', icon: Icon.dashboard, permission: 'ROBOT_VIEW', dataMode: 'live' },
        ],
      },
      {
        label: 'Account Permission Platform',
        icon: Icon.shield,
        permission: 'USER_MANAGE',
        dataMode: 'live',
        children: [
          { label: 'Internal Users', path: '/users/internal', icon: Icon.users, permission: 'USER_MANAGE', dataMode: 'live' },
          { label: 'External Users', path: '/users/external', icon: Icon.users, permission: 'USER_MANAGE', dataMode: 'live' },
          // Real backend gate is USER_MANAGE (RoleController), not ROLE_MANAGE —
          // see the matching comment on the /roles route in App.tsx.
          { label: 'Roles', path: '/roles', icon: Icon.shield, permission: 'USER_MANAGE', dataMode: 'live' },
          { label: 'Permissions', path: '/permissions', icon: Icon.key, permission: 'ROLE_MANAGE', dataMode: 'live' },
        ],
      },
      {
        label: 'Robot management',
        icon: Icon.robot,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'Robot List', path: '/robots', icon: Icon.robot, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Inventory Control', path: '/robots/inventory', icon: Icon.clipboard, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Sub-Agent Inventory', path: '/robots/sub-agent-inventory', icon: Icon.building, permission: 'ROBOT_VIEW', dataMode: 'live' },
          // No fleet-wide run-statistics/run-history aggregation endpoint exists on the
          // backend today (same gap as the pre-existing Analytics placeholder) — marked
          // 'unavailable' rather than faking a dashboard with invented numbers.
          { label: 'Running Statistics', path: '/robots/running-statistics', icon: Icon.barChart, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
          { label: 'Running Record', path: '/robots/running-record', icon: Icon.timeline, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
        ],
      },
      {
        label: 'New Resource Configuration',
        icon: Icon.dock,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'New Resource Configuration', path: '/resource-configuration', icon: Icon.dock, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Marketing materials', path: '/resource-configuration/marketing-materials', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'live' },
          // No template/file-upload subsystem exists on the backend — this one
          // sub-item stays an honest placeholder while its two siblings are real.
          { label: 'Language configuration management', path: '/resource-configuration/language', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
        ],
      },
      {
        label: 'Store Management',
        icon: Icon.building,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'Store list', path: '/stores', icon: Icon.building, permission: 'ROBOT_VIEW', dataMode: 'live' },
        ],
      },
      {
        label: 'OTA Management',
        icon: Icon.refresh,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          // KEENON nests these one level deeper, under "System Upgrade Management" — flattened
          // here since this sidebar only supports one level of nesting (see Account Permissions).
          { label: 'System Version Management', path: '/ota/versions', icon: Icon.refresh, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Update record', path: '/ota/records', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'live' },
        ],
      },
      {
        label: 'Operation And Maintenance Platform',
        icon: Icon.gauge,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'Mission Log', path: '/operations/mission-log', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Customer Repair Requests', path: '/operations/repair-requests', icon: Icon.alertOctagon, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Device Management', path: '/operations/device-management', icon: Icon.settings, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Remote Deployment', path: '/operations/remote-deployment', icon: Icon.plug, permission: 'ROBOT_VIEW', dataMode: 'live' },
          // "Remote log" needs a real robot-side log-capture channel (upload/crawl progress,
          // file storage) that doesn't exist anywhere in this codebase — unlike its four
          // siblings above, faking "Download"/"Capture log" here would misrepresent data
          // that was never actually retrieved from a robot, so this one stays a placeholder.
          { label: 'Remote log', path: '/operations/remote-log', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
        ],
      },
      {
        label: 'IoT Platform',
        icon: Icon.plug,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          // The reference product nests these under two sub-groups ("Elevator
          // Module", "Phone Module") — flattened here since this sidebar only
          // supports one level of nesting (see Account Permissions/OTA Management).
          { label: 'Elevator management', path: '/iot/elevator-management', icon: Icon.plug, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Elevator configuration', path: '/iot/elevator-configuration', icon: Icon.settings, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Cloud ladder control configuration', path: '/iot/cloud-ladder-control', icon: Icon.building, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Phone device management', path: '/iot/phone-device-management', icon: Icon.plug, permission: 'ROBOT_VIEW', dataMode: 'live' },
        ],
      },
      {
        label: 'Open Platform',
        icon: Icon.key,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'Customer registration', path: '/open-platform/customer-registration', icon: Icon.key, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Application management', path: '/open-platform/applications', icon: Icon.settings, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'File download', path: '/open-platform/file-download', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'live' },
        ],
      },
      {
        label: 'Operational Dashboard',
        icon: Icon.barChart,
        permission: 'ROBOT_VIEW',
        dataMode: 'live',
        children: [
          { label: 'Home page', path: '/operational-dashboard', icon: Icon.barChart, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Operation Ranking', path: '/operational-dashboard/operation-ranking', icon: Icon.listCheck, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Store Real-Time Data Statistics', path: '/operational-dashboard/store-realtime', icon: Icon.activity, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Use Retention Analytics', path: '/operational-dashboard/retention', icon: Icon.timeline, permission: 'ROBOT_VIEW', dataMode: 'live' },
          { label: 'Hotel Task Record', path: '/operational-dashboard/hotel-task-record', icon: Icon.clipboard, permission: 'ROBOT_VIEW', dataMode: 'live' },
        ],
      },
      {
        label: 'Learning Center Platform',
        icon: Icon.fileText,
        permission: 'ROBOT_VIEW',
        dataMode: 'unavailable',
        children: [
          { label: 'Learning Center Platform', path: '/learning-center', icon: Icon.fileText, permission: 'ROBOT_VIEW', dataMode: 'unavailable' },
        ],
      },
    ],
  },
];

// Flattens one level of children — used wherever a flat list of every real
// route/permission is needed (nothing outside Sidebar.tsx needs the nesting
// itself).
export const ALL_NAV_ITEMS: NavItem[] = NAV_SECTIONS.flatMap((s) => s.items.flatMap((item) => item.children ?? [item]));
