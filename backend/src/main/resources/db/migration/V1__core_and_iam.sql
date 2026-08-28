-- Core organization hierarchy, sites, and identity/RBAC.
-- Master Requirements Part 13 / SAKAR_ROBOT_PLATFORM_DATABASE.md §1-§4,
-- extended with the distributor/sub-distributor/client organization
-- hierarchy explicitly required by the Phase 1 implementation brief.

CREATE TABLE organizations (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_organization_id UUID REFERENCES organizations(id),
    name                   TEXT NOT NULL,
    org_type               VARCHAR(32) NOT NULL,
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    path                   VARCHAR(2048) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_organizations_parent_id ON organizations(parent_organization_id);
CREATE INDEX idx_organizations_path ON organizations(path text_pattern_ops);

CREATE TABLE sites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name            TEXT NOT NULL,
    address         TEXT,
    timezone        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sites_organization_id ON sites(organization_id);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(32) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID REFERENCES organizations(id),
    email                  TEXT NOT NULL UNIQUE,
    password_hash          TEXT NOT NULL,
    full_name              TEXT,
    role_id                UUID NOT NULL REFERENCES roles(id),
    mfa_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret             TEXT,
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at          TIMESTAMPTZ,
    failed_login_attempts  INTEGER NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_organization_id ON users(organization_id);

-- Implementation-necessary addition to Part 19.A (refresh token rotation/revocation) —
-- see RefreshToken.java Javadoc.
CREATE TABLE refresh_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    token_hash          TEXT NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    replaced_by_token_id UUID,
    device_info         TEXT,
    ip_address          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
