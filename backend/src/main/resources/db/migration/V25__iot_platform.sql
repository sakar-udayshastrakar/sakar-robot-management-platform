-- IoT Platform: Elevator Module (Elevator management, Elevator
-- configuration + set record, Cloud ladder control configuration) + Phone
-- Module (Device management). "Online status"/"Slave online status" are
-- deliberately NOT columns here — no elevator/phone vendor telemetry
-- channel exists anywhere in this codebase, so the frontend shows an
-- honest "Unknown" rather than a fabricated live status (see
-- ElevatorController's own Javadoc).

CREATE TABLE elevator_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id UUID NOT NULL REFERENCES sites(id),
    device_id TEXT NOT NULL,
    device_name TEXT,
    building TEXT,
    protocol TEXT,
    networking_mode TEXT,
    communication_mode TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_elevator_devices_organization_id ON elevator_devices(organization_id);

CREATE TABLE elevator_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id UUID NOT NULL REFERENCES sites(id),
    elevator_device_id UUID NOT NULL REFERENCES elevator_devices(id),
    robot_id UUID NOT NULL REFERENCES robots(id),
    name TEXT NOT NULL,
    notes TEXT,
    modified_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_elevator_configurations_organization_id ON elevator_configurations(organization_id);

-- "set record" — an append-only change history for elevator_configurations,
-- same shape/purpose as task_events for robot_tasks.
CREATE TABLE elevator_configuration_events (
    id BIGSERIAL PRIMARY KEY,
    elevator_configuration_id UUID NOT NULL REFERENCES elevator_configurations(id),
    event_type TEXT NOT NULL,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cloud ladder control configuration → Store binding. One binding per
-- store (Site) to a third-party ("tripartite") ladder-control vendor.
CREATE TABLE ladder_control_store_bindings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id UUID NOT NULL UNIQUE REFERENCES sites(id),
    manufacturer TEXT NOT NULL,
    building_id TEXT,
    client_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ladder_control_store_bindings_organization_id ON ladder_control_store_bindings(organization_id);

-- Cloud ladder control configuration → "The elevator configuration is
-- delivered". Bookkeeping only, same RECORDED-not-confirmed convention as
-- ota.DeploymentRecord — no delivery channel to a physical elevator
-- controller exists.
CREATE TABLE elevator_configuration_deliveries (
    id BIGSERIAL PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    elevator_configuration_id UUID NOT NULL REFERENCES elevator_configurations(id),
    robot_id UUID NOT NULL REFERENCES robots(id),
    delivered_by TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'RECORDED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_elevator_configuration_deliveries_organization_id ON elevator_configuration_deliveries(organization_id);

CREATE TABLE phone_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    site_id UUID NOT NULL REFERENCES sites(id),
    device_id TEXT NOT NULL,
    device_name TEXT,
    networking_mode TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_phone_devices_organization_id ON phone_devices(organization_id);
