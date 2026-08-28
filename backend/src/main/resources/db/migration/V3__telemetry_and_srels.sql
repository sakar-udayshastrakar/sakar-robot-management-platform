-- Telemetry + SRELS (Master Requirements Part 9/12,
-- SAKAR_ROBOT_PLATFORM_DATABASE.md §9-§11, §24-§25). Schema only in
-- Phase 1 — see the entity Javadocs for what actually populates these.

CREATE TABLE robot_status (
    robot_id        UUID PRIMARY KEY REFERENCES robots(id) ON DELETE CASCADE,
    online          BOOLEAN NOT NULL DEFAULT FALSE,
    battery_percent INTEGER,
    charging_state  TEXT,
    main_state      TEXT,
    sub_state       TEXT,
    last_seen_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE robot_telemetry (
    id            BIGSERIAL PRIMARY KEY,
    robot_id      UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    metric        TEXT NOT NULL,
    value_numeric DOUBLE PRECISION,
    value_text    TEXT,
    recorded_at   TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_telemetry_robot_id_recorded_at ON robot_telemetry(robot_id, recorded_at);
CREATE INDEX idx_robot_telemetry_metric ON robot_telemetry(metric);

CREATE TABLE robot_events (
    id          BIGSERIAL PRIMARY KEY,
    robot_id    UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    event_type  TEXT NOT NULL,
    severity    VARCHAR(16) NOT NULL,
    payload     TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_events_robot_id_occurred_at ON robot_events(robot_id, occurred_at);
CREATE INDEX idx_robot_events_event_type ON robot_events(event_type);
CREATE INDEX idx_robot_events_severity ON robot_events(severity);

CREATE TABLE robot_errors (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id     UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    error_code   TEXT NOT NULL,
    severity     VARCHAR(16) NOT NULL,
    source       TEXT,
    message      TEXT,
    sdk_api      TEXT,
    status       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    resolved_by  UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_errors_robot_id ON robot_errors(robot_id);
CREATE INDEX idx_robot_errors_severity ON robot_errors(severity);
CREATE INDEX idx_robot_errors_status ON robot_errors(status);

CREATE TABLE robot_alerts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id         UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    organization_id  UUID NOT NULL REFERENCES organizations(id),
    alert_type       TEXT NOT NULL,
    severity         VARCHAR(16) NOT NULL,
    message          TEXT,
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    acknowledged_by  UUID,
    acknowledged_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_alerts_organization_id ON robot_alerts(organization_id);
CREATE INDEX idx_robot_alerts_robot_id ON robot_alerts(robot_id);
CREATE INDEX idx_robot_alerts_status ON robot_alerts(status);

CREATE TABLE application_logs (
    id         BIGSERIAL PRIMARY KEY,
    source     TEXT NOT NULL,
    robot_id   UUID,
    level      VARCHAR(16) NOT NULL,
    message    TEXT NOT NULL,
    context    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_application_logs_created_at ON application_logs(created_at);
