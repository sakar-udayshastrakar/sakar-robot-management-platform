// Mirrors backend/src/main/java/com/sakarrobotics/cloud/iam/PermissionCode.java
// and RoleName.java VERBATIM. Do not add a permission or role here that does
// not exist in that enum — the backend is always the authority; this list
// only drives what the UI shows/hides, never what it allows.

export const PERMISSION_CODES = [
  'ROBOT_VIEW',
  'ROBOT_CONTROL',
  'ROBOT_TASK_CREATE',
  'ROBOT_TASK_CANCEL',
  'ROBOT_LOCK',
  'ROBOT_UNLOCK',
  'ROBOT_CONFIGURE',
  'ROBOT_DIAGNOSTICS',
  'ROBOT_LOG_VIEW',
  'AUDIT_VIEW',
  'USER_MANAGE',
  'ROLE_MANAGE',
  'SYSTEM_ADMIN',
] as const;

export type PermissionCode = (typeof PERMISSION_CODES)[number];

export const ROLE_NAMES = [
  'SUPER_ADMIN',
  'ORG_ADMIN',
  'SITE_ADMIN',
  'OPERATOR',
  'TECHNICIAN',
  'VIEWER',
] as const;

export type RoleName = (typeof ROLE_NAMES)[number];
