package com.sakarrobotics.cloud.integration.keenon.dto;

import com.sakarrobotics.cloud.integration.keenon.KeenonRobotSceneConfig;

public record SceneConfigResponse(String sceneCode, String sceneName) {

    public static SceneConfigResponse from(KeenonRobotSceneConfig config) {
        return new SceneConfigResponse(config.getSceneCode(), config.getSceneName());
    }
}
