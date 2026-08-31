package com.sakarrobotics.c40agent.telemetry;

/**
 * Decoupled mirror of an orientation field inside the SDK's own {@code
 * NavigationDestPoseApi.Bean.DataBean.PoseBean.OrientationBean} (verified
 * by decompiling peanut-sdk-release.aar - see {@code
 * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md}). A full quaternion, not a
 * single heading angle - the vendor bean carries all four components.
 *
 * <p>All four fields are {@code double} here even though the verified
 * vendor bean's {@code x}/{@code y} fields are {@code int} - see {@link
 * Position}'s Javadoc for the same reasoning.
 */
public final class Orientation {

    private final double w;
    private final double x;
    private final double y;
    private final double z;

    public Orientation(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getW() {
        return w;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}
