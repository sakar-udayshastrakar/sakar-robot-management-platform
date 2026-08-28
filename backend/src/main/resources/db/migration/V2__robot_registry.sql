-- Vendor-neutral robot registry (Master Requirements §6.A,
-- SAKAR_ROBOT_PLATFORM_DATABASE.md §6-§8 + Appendix A). No column here is
-- ever named c40_* — everything model-specific is reached through
-- robot_model_id.

CREATE TABLE robot_manufacturers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE robot_models (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_id    UUID NOT NULL REFERENCES robot_manufacturers(id),
    name               TEXT NOT NULL,
    sakar_product_name TEXT,
    adapter_type       VARCHAR(32) NOT NULL,
    integration_path   VARCHAR(32) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_models_manufacturer_id ON robot_models(manufacturer_id);

CREATE TABLE robot_capabilities (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_model_id UUID NOT NULL REFERENCES robot_models(id) ON DELETE CASCADE,
    capability     VARCHAR(32) NOT NULL,
    supported      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_robot_capabilities_model_capability UNIQUE (robot_model_id, capability)
);

CREATE TABLE robots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id),
    site_id           UUID REFERENCES sites(id),
    robot_model_id    UUID NOT NULL REFERENCES robot_models(id),
    name              TEXT NOT NULL,
    serial_number     TEXT NOT NULL UNIQUE,
    external_robot_id TEXT,
    status            VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robots_organization_id ON robots(organization_id);
CREATE INDEX idx_robots_site_id ON robots(site_id);
CREATE INDEX idx_robots_robot_model_id ON robots(robot_model_id);
CREATE INDEX idx_robots_external_robot_id ON robots(external_robot_id);

CREATE TABLE robot_credentials (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id               UUID NOT NULL UNIQUE REFERENCES robots(id) ON DELETE CASCADE,
    credential_type        TEXT NOT NULL,
    credential_value_hash  TEXT NOT NULL,
    provisioned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
