-- Robot command security envelope + remote lock/unlock software
-- infrastructure (Master Requirements Part 11/20). Schema only in Phase 1
-- — no dispatch logic exists yet (Phase 5), and the physical effect of
-- lock/unlock remains REQUIRES PHYSICAL C40 TEST regardless (Part 38).

CREATE TABLE robot_commands (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id        UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    issued_by       UUID NOT NULL REFERENCES users(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    command_type    TEXT NOT NULL,
    payload         TEXT,
    status          VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    nonce           TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    signature       TEXT,
    sent_at         TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_robot_commands_robot_nonce ON robot_commands(robot_id, nonce);
CREATE INDEX idx_robot_commands_robot_id ON robot_commands(robot_id);
CREATE INDEX idx_robot_commands_organization_id ON robot_commands(organization_id);
CREATE INDEX idx_robot_commands_status ON robot_commands(status);

CREATE TABLE command_results (
    id          BIGSERIAL PRIMARY KEY,
    command_id  UUID NOT NULL REFERENCES robot_commands(id) ON DELETE CASCADE,
    result      VARCHAR(24) NOT NULL,
    detail      TEXT,
    duration_ms BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_command_results_command_id ON command_results(command_id);

CREATE TABLE robot_locks (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id             UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    action               VARCHAR(16) NOT NULL,
    requested_by         UUID NOT NULL REFERENCES users(id),
    reason               TEXT NOT NULL,
    command_id           UUID REFERENCES robot_commands(id),
    result               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    physically_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_robot_locks_robot_id ON robot_locks(robot_id);
