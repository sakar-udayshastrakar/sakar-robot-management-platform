-- Authoritative role/permission seed, verbatim from Master Requirements
-- Part 19.B's permission matrix. ROBOT_UNLOCK is intentionally granted to
-- fewer roles than ROBOT_LOCK (Part 19.B rationale: unlocking must be
-- deliberately harder to obtain than locking).

INSERT INTO roles (name, description) VALUES
    ('SUPER_ADMIN', 'Cross-organization Sakar staff administrator'),
    ('ORG_ADMIN', 'Administrator scoped to their own organization and its descendants'),
    ('SITE_ADMIN', 'Administrator scoped to their own site'),
    ('OPERATOR', 'Day-to-day robot operator'),
    ('TECHNICIAN', 'Field technician with diagnostics/configuration access, no task/lock authority'),
    ('VIEWER', 'Read-only access');

INSERT INTO permissions (code, description) VALUES
    ('ROBOT_VIEW', 'View robot identity, status, telemetry, history'),
    ('ROBOT_CONTROL', 'Issue non-task robot commands (e.g. start/stop charge)'),
    ('ROBOT_TASK_CREATE', 'Create a robot task (e.g. a cleaning task)'),
    ('ROBOT_TASK_CANCEL', 'Cancel/stop/pause an in-progress robot task'),
    ('ROBOT_LOCK', 'Remotely lock a robot''s motors'),
    ('ROBOT_UNLOCK', 'Remotely unlock a robot''s motors (stronger grant than ROBOT_LOCK)'),
    ('ROBOT_CONFIGURE', 'Register/configure robots, sites, and organizations'),
    ('ROBOT_DIAGNOSTICS', 'View diagnostic-level robot detail'),
    ('ROBOT_LOG_VIEW', 'View the SRELS operational log timeline'),
    ('AUDIT_VIEW', 'View the security/compliance audit trail'),
    ('USER_MANAGE', 'Create/manage users within scope'),
    ('ROLE_MANAGE', 'Manage roles/permission assignment'),
    ('SYSTEM_ADMIN', 'Platform-level configuration');

-- SUPER_ADMIN: every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'SUPER_ADMIN';

-- ORG_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ORG_ADMIN' AND p.code IN (
    'ROBOT_VIEW','ROBOT_CONTROL','ROBOT_TASK_CREATE','ROBOT_TASK_CANCEL',
    'ROBOT_LOCK','ROBOT_UNLOCK','ROBOT_CONFIGURE','ROBOT_DIAGNOSTICS',
    'ROBOT_LOG_VIEW','AUDIT_VIEW','USER_MANAGE');

-- SITE_ADMIN (no ROBOT_UNLOCK, no AUDIT_VIEW/USER_MANAGE — Part 19.B rationale)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SITE_ADMIN' AND p.code IN (
    'ROBOT_VIEW','ROBOT_CONTROL','ROBOT_TASK_CREATE','ROBOT_TASK_CANCEL',
    'ROBOT_LOCK','ROBOT_CONFIGURE','ROBOT_DIAGNOSTICS','ROBOT_LOG_VIEW');

-- OPERATOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'OPERATOR' AND p.code IN (
    'ROBOT_VIEW','ROBOT_CONTROL','ROBOT_TASK_CREATE','ROBOT_TASK_CANCEL',
    'ROBOT_LOCK','ROBOT_LOG_VIEW');

-- TECHNICIAN (no task/lock authority; diagnostics + configure per the matrix)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'TECHNICIAN' AND p.code IN (
    'ROBOT_VIEW','ROBOT_CONTROL','ROBOT_TASK_CANCEL','ROBOT_CONFIGURE',
    'ROBOT_DIAGNOSTICS','ROBOT_LOG_VIEW');

-- VIEWER (read-only)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'VIEWER' AND p.code IN ('ROBOT_VIEW','ROBOT_LOG_VIEW');
