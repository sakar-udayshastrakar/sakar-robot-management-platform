-- Robot-specific Keenon sceneCode configuration (Phase 1I). Live verification
-- proved GET /api/open/custom/clean/robot/status (the only status endpoint
-- that works for C-series robots on this account) does not return
-- sceneCode/sceneName/mapId at all, and the one endpoint that ever did
-- return sceneCode (GET /api/open/scene/v1/robot/status) is rejected with
-- 610403 for this account on C-series robots. Until Keenon documents an
-- authoritative robot -> scene endpoint, sceneCode is Sakar-owned
-- configuration data: one row per robot, set explicitly by an operator via
-- PUT /api/v1/robots/{id}/keenon/scene-config — never a global default,
-- never inferred from another robot's configuration, a historical scene, or
-- a vendor mapId.
CREATE TABLE keenon_robot_scene_configs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    robot_id    UUID NOT NULL UNIQUE REFERENCES robots(id) ON DELETE CASCADE,
    scene_code  TEXT NOT NULL,
    scene_name  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
