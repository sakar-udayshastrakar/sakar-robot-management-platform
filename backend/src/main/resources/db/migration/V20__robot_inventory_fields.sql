-- Robot Management (Phase 2): warranty tracking and commercial use-type,
-- extending the existing robot registry rather than a parallel "inventory"
-- table. "Store" (site_id, already exists) and "agent of the machine"
-- (organization_id, already exists — an org's own hierarchy already models
-- distributor/sub-distributor/client) needed no new columns at all.
ALTER TABLE robots ADD COLUMN use_type VARCHAR(16) NOT NULL DEFAULT 'TRIAL';
ALTER TABLE robots ADD COLUMN warranty_start_date DATE;
ALTER TABLE robots ADD COLUMN warranty_end_date DATE;
