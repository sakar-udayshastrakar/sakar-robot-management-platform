-- OTA Management (Robot Management sidebar group's sibling, governance doc
-- Module B "OTA / Deployment" — approved for a real build). A software
-- version is a named package; a deployment record is one push of one
-- version to one existing robot (reusing the robots table — no duplicate
-- "machine SN"/"machine code" columns).
CREATE TABLE software_versions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID NOT NULL REFERENCES organizations(id),
    package_name           TEXT NOT NULL,
    whole_machine_software TEXT,
    package_version        TEXT NOT NULL,
    hardware_version       TEXT,
    grayscale              BOOLEAN NOT NULL DEFAULT false,
    size_bytes             BIGINT,
    created_by             TEXT,
    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_software_versions_organization_id ON software_versions(organization_id);

-- Append-only (AppendOnlyEntity convention: BIGSERIAL id, no updated_at) —
-- a push is a historical fact, never edited in place.
CREATE TABLE deployment_records (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       UUID NOT NULL REFERENCES organizations(id),
    robot_id              UUID NOT NULL REFERENCES robots(id),
    software_version_id   UUID NOT NULL REFERENCES software_versions(id),
    old_version_number    TEXT,
    new_version_number    TEXT NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'RECORDED',
    error_message         TEXT,
    grayscale             BOOLEAN NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_deployment_records_organization_id ON deployment_records(organization_id);
CREATE INDEX idx_deployment_records_robot_id ON deployment_records(robot_id);
