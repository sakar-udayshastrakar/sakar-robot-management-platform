package com.sakarrobotics.c40agent.telemetry;

/**
 * A position + orientation pair, mirroring the SDK's own {@code
 * NavigationDestPoseApi.Bean.DataBean.PoseBean} shape (verified by
 * decompiling peanut-sdk-release.aar - see {@code
 * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md}). Either field may be
 * {@code null} if the vendor response omitted it - this class does not
 * invent a default position or orientation.
 */
public final class Pose {

    private final Position position;
    private final Orientation orientation;

    public Pose(Position position, Orientation orientation) {
        this.position = position;
        this.orientation = orientation;
    }

    public Position getPosition() {
        return position;
    }

    public Orientation getOrientation() {
        return orientation;
    }
}
