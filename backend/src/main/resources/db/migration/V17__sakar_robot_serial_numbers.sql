-- Sakar-generated robot serial numbers (SR-CB-YYYY-NNNNNN) must never collide,
-- even under concurrent registration. A native sequence gives an atomic,
-- non-transactional nextval() with no possible race — unlike a
-- max(serial_number)+1 read-then-write pattern, which is NOT concurrency-safe
-- under concurrent inserts. See SakarSerialNumberService for why this exact
-- statement (CREATE SEQUENCE IF NOT EXISTS) is also executed idempotently at
-- application startup rather than solely here: the automated test suite runs
-- against a Hibernate-generated H2 schema with Flyway disabled
-- (IntegrationTestSupport), so this migration alone would leave the sequence
-- absent in every automated test.
CREATE SEQUENCE IF NOT EXISTS sakar_robot_serial_seq START WITH 1 INCREMENT BY 1;

-- Preserves the vendor's own manufacturer serial (e.g. Keenon mftCode)
-- separately from Sakar's own serial_number — see KeenonRobotSyncService's
-- Javadoc for why these must never be conflated. Nullable: not every robot
-- has a vendor-supplied manufacturer serial, and existing rows have none yet.
ALTER TABLE robots ADD COLUMN vendor_serial_number TEXT;
