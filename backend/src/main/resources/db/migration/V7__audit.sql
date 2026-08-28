-- Immutable security/compliance audit trail (Master Requirements Part
-- 12.E/13). This migration does not (and, for local/dev Postgres roles,
-- normally cannot without additional deployment-specific role setup) apply
-- the REVOKE UPDATE/DELETE database-role hardening described in Part 26 —
-- see the Final Report "known limitations". Application code never issues
-- an UPDATE or DELETE against this table (AuditLog/AuditService).

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID,
    organization_id UUID,
    robot_id        UUID,
    action          TEXT NOT NULL,
    result          TEXT NOT NULL,
    reason          TEXT,
    ip_address      TEXT,
    device          TEXT,
    request_id      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_organization_id ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_robot_id ON audit_logs(robot_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
