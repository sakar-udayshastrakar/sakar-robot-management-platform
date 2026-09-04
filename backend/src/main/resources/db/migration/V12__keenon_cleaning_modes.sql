-- Keenon read-only cleaning-mode sync slice. Mirrors keenon_area_mappings'
-- shape: a Sakar-owned mapping row per (robot, vendor mode id), never a
-- hardcoded/compiled-in mode list — GET .../clean/robot/strategy/clean/model
-- takes only robotSn (no storeId), so unlike area mappings there is no
-- keenon_store_id/keenon_map_id column here.
CREATE TABLE keenon_cleaning_mode_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id         UUID REFERENCES sites(id),
    robot_id        UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    keenon_mode_id  TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_keenon_cleaning_mode_mappings_robot_id ON keenon_cleaning_mode_mappings(robot_id);
