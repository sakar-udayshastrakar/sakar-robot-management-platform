-- Phase 3 (Robot Communication / MQTT). Idempotency ledger for inbound
-- agent->cloud MQTT messages (Master Requirements Part 15/23): every
-- accepted envelope is recorded here keyed on (robot_id, message_id) so a
-- redelivered message (QoS 1 "at-least-once", agent reconnect replay) is a
-- no-op rather than a duplicate telemetry/event/error row. Mirrors the
-- existing vendor_webhook_events idempotency pattern
-- (V8__keenon_integration.sql) rather than inventing a new one.

CREATE TABLE mqtt_inbound_messages (
    id           BIGSERIAL PRIMARY KEY,
    robot_id     UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    message_id   TEXT NOT NULL,
    message_type VARCHAR(16) NOT NULL,
    sequence     BIGINT,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_mqtt_inbound_messages_robot_message UNIQUE (robot_id, message_id)
);
CREATE INDEX idx_mqtt_inbound_messages_robot_id_received_at ON mqtt_inbound_messages(robot_id, received_at);
