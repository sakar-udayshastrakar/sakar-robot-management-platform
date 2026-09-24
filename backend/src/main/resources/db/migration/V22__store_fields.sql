-- Store Management (Robot Management sidebar group) — extends the existing
-- sites table with the reference product's richer "store" fields, rather
-- than inventing a separate Store entity. "Affiliated agent" needs no new
-- column: it is the site's existing organization_id, resolved to that
-- organization's name.
ALTER TABLE sites ADD COLUMN area TEXT;
ALTER TABLE sites ADD COLUMN contact_name TEXT;
ALTER TABLE sites ADD COLUMN phone TEXT;
ALTER TABLE sites ADD COLUMN email TEXT;
ALTER TABLE sites ADD COLUMN scene_type TEXT;
ALTER TABLE sites ADD COLUMN is_chain_brand BOOLEAN NOT NULL DEFAULT false;
