-- Sakar serial numbers are now generated per robot-model "family": each model owns its own
-- prefix (e.g. C40 S -> CB) and its own independent database sequence, rather than one shared
-- SR-CB-... sequence across every model regardless of type. See SakarSerialNumberService.
--
-- This does NOT touch any existing robots.serial_number value — the current database was
-- manually corrected to the intended per-family format before this migration was written, and
-- this migration only adds model-level metadata, never re-derives or overwrites a robot's own
-- serial_number/vendor_serial_number.
ALTER TABLE robot_models ADD COLUMN serial_prefix VARCHAR(8);

-- Known mappings as of this pass — a Sakar-owned business decision, not derived from Keenon.
-- A model with no row updated here (e.g. any future Keenon model) simply keeps serial_prefix
-- NULL, and robot registration for that model fails safely rather than inventing a prefix.
UPDATE robot_models SET serial_prefix = 'CB' WHERE name = 'C40 S' AND serial_prefix IS NULL;
UPDATE robot_models SET serial_prefix = 'BT' WHERE name = 'W3' AND serial_prefix IS NULL;
UPDATE robot_models SET serial_prefix = 'PL' WHERE name = 'S100' AND serial_prefix IS NULL;

-- The old global sequence (sakar_robot_serial_seq, from V17) is intentionally left in place,
-- unused, rather than dropped — it is harmless, and dropping it is unnecessary churn with no
-- benefit; no code references it after this pass. Per-prefix sequences (e.g.
-- sakar_robot_serial_seq_cb) are created lazily on first use by SakarSerialNumberService, not
-- pre-created here, since future prefixes are not known in advance.
