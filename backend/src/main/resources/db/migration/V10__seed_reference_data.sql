-- Reference data matching the live-tested Keenon C40 S / Sakar CleanBot
-- 5000 Plus evidence (Master Requirements Part 40,
-- SAKAR_LIVE_API_VALIDATION_MATRIX.md). Capability flags below are graded
-- directly from that evidence — do not mark LOCK/UNLOCK/GET_MAP supported
-- without new evidence (Part 11/40).

INSERT INTO organizations (name, org_type, status, path)
VALUES ('Sakar Robotics', 'SAKAR_ROOT', 'ACTIVE', '/');

UPDATE organizations
SET path = '/' || id || '/'
WHERE name = 'Sakar Robotics' AND org_type = 'SAKAR_ROOT';

INSERT INTO robot_manufacturers (name) VALUES ('Keenon');

INSERT INTO robot_models (manufacturer_id, name, sakar_product_name, adapter_type, integration_path)
SELECT id, 'C40 S', 'Sakar CleanBot 5000 Plus', 'KEENON_CLOUD', 'KEENON_CLOUD_DEPENDENT'
FROM robot_manufacturers WHERE name = 'Keenon';

INSERT INTO robot_capabilities (robot_model_id, capability, supported)
SELECT rm.id, cap.capability, cap.supported
FROM robot_models rm
CROSS JOIN (VALUES
    ('GET_STATUS', TRUE),
    ('GET_BATTERY', TRUE),
    ('GET_TELEMETRY', TRUE),
    ('GET_EVENTS', TRUE),
    ('GET_AREAS', TRUE),
    ('GET_MAP', FALSE),
    ('START_TASK', TRUE),
    ('STOP_TASK', TRUE),
    ('PAUSE_TASK', TRUE),
    ('RESUME_TASK', FALSE),
    ('RETURN_TO_DOCK', TRUE),
    ('LOCK', FALSE),
    ('UNLOCK', FALSE),
    ('CLEANING', TRUE)
) AS cap(capability, supported)
WHERE rm.name = 'C40 S';
