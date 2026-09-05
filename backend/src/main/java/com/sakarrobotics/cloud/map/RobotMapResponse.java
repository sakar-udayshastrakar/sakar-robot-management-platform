package com.sakarrobotics.cloud.map;

import java.time.Instant;

/**
 * The public shape of a robot's map metadata. Deliberately excludes {@code
 * imageUrl} (a local filesystem path — never exposed to a client, see
 * {@link RobotMapImageService}), any raw Keenon payload, and vendor
 * credentials — this record carries only the fields evidenced by Keenon's
 * own responses and already stored on {@link RobotMap}.
 */
public record RobotMapResponse(
        String vendorMapId,
        String name,
        Integer width,
        Integer height,
        String mapMd5,
        Instant updatedAt) {

    public static RobotMapResponse from(RobotMap robotMap) {
        return new RobotMapResponse(
                robotMap.getVendorMapId(), robotMap.getName(), robotMap.getWidth(),
                robotMap.getHeight(), robotMap.getMapMd5(), robotMap.getUpdatedAt());
    }
}
