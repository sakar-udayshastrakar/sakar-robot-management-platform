-- Account Permission Platform, Phase 1: an Internal/External user split plus
-- a flat department list for internal (Sakar-staff) users, extending the
-- existing users/roles model rather than introducing a parallel one.
--
-- Departments are deliberately NOT organization-scoped: they classify
-- Sakar's own internal staff only, not customer-org structure, matching the
-- reference product's single flat department list.
CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- user_type distinguishes Sakar-internal staff from customer/external
-- accounts, independent of organization_id nullability (which remains
-- SUPER_ADMIN-only per the existing rule in UserService#create). Existing
-- SUPER_ADMIN-scoped rows (organization_id IS NULL) backfill to INTERNAL;
-- every other existing row backfills to EXTERNAL, the safer default for
-- already-provisioned customer accounts.
ALTER TABLE users ADD COLUMN user_type VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL';
UPDATE users SET user_type = 'INTERNAL' WHERE organization_id IS NULL;

ALTER TABLE users ADD COLUMN department_id UUID REFERENCES departments(id);
CREATE INDEX idx_users_department_id ON users(department_id);
