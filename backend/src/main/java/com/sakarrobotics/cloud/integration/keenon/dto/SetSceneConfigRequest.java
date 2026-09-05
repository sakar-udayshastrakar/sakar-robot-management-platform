package com.sakarrobotics.cloud.integration.keenon.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The caller must supply the sceneCode explicitly — Sakar never hardcodes,
 * infers, or defaults one robot's sceneCode from another's (see {@code
 * KeenonMapMetadataSyncService}'s Javadoc). {@code sceneName} is an optional
 * human-readable label; no live Keenon endpoint currently supplies one for
 * this account, so it is operator-supplied metadata, not vendor-verified data.
 */
public record SetSceneConfigRequest(@NotBlank String sceneCode, String sceneName) {
}
