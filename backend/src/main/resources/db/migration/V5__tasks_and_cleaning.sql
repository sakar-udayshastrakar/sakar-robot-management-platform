-- Vendor-neutral task/cleaning/charging model (Master Requirements "Task
-- Model"/"Cleaning" sections). Schema only in Phase 1.

CREATE TABLE robot_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id        UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    created_by      UUID NOT NULL REFERENCES users(id),
    task_type       TEXT NOT NULL,
    parameters      TEXT,
    status          VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_tasks_robot_id ON robot_tasks(robot_id);
CREATE INDEX idx_robot_tasks_organization_id ON robot_tasks(organization_id);

CREATE TABLE task_events (
    id         BIGSERIAL PRIMARY KEY,
    task_id    UUID NOT NULL REFERENCES robot_tasks(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    detail     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_events_task_id ON task_events(task_id);

CREATE TABLE cleaning_sessions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id         UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    site_id          UUID REFERENCES sites(id),
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    task_id          UUID REFERENCES robot_tasks(id),
    started_at       TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    duration_seconds BIGINT,
    area_sq_meters   DOUBLE PRECISION,
    efficiency       DOUBLE PRECISION,
    result           TEXT,
    failure_reason   TEXT,
    vendor_reference TEXT,
    snapshot_url     TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cleaning_sessions_robot_id ON cleaning_sessions(robot_id);
CREATE INDEX idx_cleaning_sessions_organization_id ON cleaning_sessions(organization_id);

CREATE TABLE charging_sessions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id               UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    started_at             TIMESTAMPTZ,
    ended_at               TIMESTAMPTZ,
    start_battery_percent  INTEGER,
    end_battery_percent    INTEGER,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_charging_sessions_robot_id ON charging_sessions(robot_id);
