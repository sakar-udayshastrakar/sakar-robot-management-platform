-- Operation And Maintenance Platform: Customer Repair Requests + Remote
-- Deployment. Mission Log reuses the existing robot_tasks table (no new
-- table needed — see RobotTaskController's fleet-wide listing endpoint).

CREATE TABLE repair_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id UUID REFERENCES sites(id),
    robot_id UUID REFERENCES robots(id),
    work_order_number TEXT NOT NULL UNIQUE,
    symptom TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    reported_by TEXT,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_repair_requests_organization_id ON repair_requests(organization_id);

-- Bookkeeping only: an operator recording that they (manually) deployed a
-- configuration to a robot. No remote-configuration-push channel exists —
-- COMPLETED/FAILED are only ever set by the operator's own follow-up action,
-- never inferred from a device response.
CREATE TABLE remote_deployment_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id UUID REFERENCES sites(id),
    robot_id UUID NOT NULL REFERENCES robots(id),
    deployed_by TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'RECORDED',
    notes TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_remote_deployment_records_organization_id ON remote_deployment_records(organization_id);
