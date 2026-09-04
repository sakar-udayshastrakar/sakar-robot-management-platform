-- Keenon read-only back/charging-point sync slice. Mirrors
-- keenon_cleaning_mode_mappings' shape exactly: GET .../strategy/back/point
-- takes only robotSn (no storeId), so there is no keenon_store_id/
-- keenon_map_id column here either.
CREATE TABLE keenon_back_point_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    site_id             UUID REFERENCES sites(id),
    robot_id            UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    keenon_back_point_id TEXT NOT NULL,
    display_name        TEXT NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_keenon_back_point_mappings_robot_id ON keenon_back_point_mappings(robot_id);
