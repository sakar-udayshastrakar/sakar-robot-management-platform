-- Open Platform: Customer registration (real onboarding record, reviewed via
-- ROLE_MANAGE, never auto-"pass") + Application management (real API
-- client/key registry — the secret is stored hashed, only ever shown in
-- plaintext once, at creation, same convention as a user password). "File
-- download" needs no table: it serves the backend's own real, already-live
-- OpenAPI spec (see OpenPlatformController).

CREATE TABLE open_platform_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id),
    company_name TEXT NOT NULL,
    area TEXT,
    company_address TEXT,
    system_matcher TEXT,
    contact_information TEXT,
    docking_requirements TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_by TEXT,
    reviewed_by TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE open_platform_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    app_id TEXT NOT NULL UNIQUE,
    application_name TEXT NOT NULL,
    business_type TEXT,
    access_key TEXT NOT NULL UNIQUE,
    secret_key_hash TEXT NOT NULL,
    secret_key_last_four TEXT NOT NULL,
    created_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_open_platform_applications_organization_id ON open_platform_applications(organization_id);
