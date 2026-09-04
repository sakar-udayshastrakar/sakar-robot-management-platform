-- Keenon map-point sync slice. map_points had no state to represent "this
-- point is no longer reported by the vendor" — required to satisfy the
-- explicit deactivate-on-disappearance rule (mirrors the same active
-- column already on keenon_area_mappings / keenon_cleaning_mode_mappings /
-- keenon_back_point_mappings). No other column is added in this migration.
ALTER TABLE map_points ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
