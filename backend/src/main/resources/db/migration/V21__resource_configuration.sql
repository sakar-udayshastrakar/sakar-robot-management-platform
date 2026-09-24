-- New Resource Configuration (Robot Management sidebar group, "Scene"
-- management + Marketing materials). "Store" reuses the existing sites
-- table (no new Store concept); a scene may optionally be bound to one
-- specific robot via robot_id, reusing the existing robots table too.
CREATE TABLE resource_scenes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    site_id             UUID REFERENCES sites(id),
    robot_id            UUID REFERENCES robots(id),
    name                TEXT NOT NULL,
    resource_pack_type  TEXT NOT NULL DEFAULT 'STANDARD',
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resource_scenes_organization_id ON resource_scenes(organization_id);

CREATE TABLE marketing_materials (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id),
    name              TEXT NOT NULL,
    material_type     TEXT NOT NULL DEFAULT 'GENERAL',
    status            VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_marketing_materials_organization_id ON marketing_materials(organization_id);
