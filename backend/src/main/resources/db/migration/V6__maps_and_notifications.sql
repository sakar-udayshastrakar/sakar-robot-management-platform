-- Maps + per-user notifications. Schema only in Phase 1.

CREATE TABLE maps (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id      UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    vendor_map_id TEXT,
    image_url     TEXT,
    name          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_maps_robot_id ON maps(robot_id);

CREATE TABLE map_points (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id     UUID NOT NULL REFERENCES maps(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    point_type TEXT,
    x          DOUBLE PRECISION,
    y          DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_map_points_map_id ON map_points(map_id);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       TEXT NOT NULL,
    message    TEXT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
