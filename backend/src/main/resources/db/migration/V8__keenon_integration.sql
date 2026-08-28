-- Keenon area-id sync mapping (Master Requirements Part 40 — "current area
-- IDs must not be hardcoded") and idempotent vendor webhook intake.

CREATE TABLE keenon_area_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id         UUID REFERENCES sites(id),
    robot_id        UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    keenon_store_id TEXT NOT NULL,
    keenon_map_id   TEXT,
    keenon_area_id  TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    last_synced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_keenon_area_mappings_robot_id ON keenon_area_mappings(robot_id);

CREATE TABLE vendor_webhook_events (
    id          BIGSERIAL PRIMARY KEY,
    vendor      VARCHAR(32) NOT NULL DEFAULT 'KEENON',
    event_type  TEXT,
    dedup_key   TEXT NOT NULL UNIQUE,
    raw_payload TEXT NOT NULL,
    processed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
