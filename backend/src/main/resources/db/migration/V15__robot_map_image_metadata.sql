-- Raw Keenon PNG map storage slice. Adds only the fields evidenced by the
-- vendor's own responses (originPosition.width/height from GET .../robot/map,
-- mapMd5 from GET .../robot/map/position) — never resolution/origin/yaw/
-- occupancy fields, which remain unconfirmed by any available evidence.
ALTER TABLE maps ADD COLUMN width INTEGER;
ALTER TABLE maps ADD COLUMN height INTEGER;
ALTER TABLE maps ADD COLUMN map_md5 TEXT;
