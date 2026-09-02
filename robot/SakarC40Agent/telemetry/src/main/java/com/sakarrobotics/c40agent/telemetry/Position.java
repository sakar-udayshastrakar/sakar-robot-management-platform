package com.sakarrobotics.c40agent.telemetry;

/**
 * Decoupled mirror of a position field inside the SDK's own {@code
 * NavigationDestPoseApi.Bean.DataBean.PoseBean.PositionBean} (verified by
 * decompiling peanut-sdk-release.aar - see {@code
 * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md}). Only the sdk module is
 * allowed to import com.keenon.* types; this class is how that data
 * crosses the module boundary.
 *
 * <p>All three fields are {@code double} here even though the verified
 * vendor bean's {@code z} field is an {@code int} - Gson maps a JSON
 * integer into a Java {@code double} without loss for any coordinate
 * range this project has seen, and a single consistent numeric type is
 * simpler for callers than mirroring the vendor's inconsistency.
 */
public final class Position {

    private final double x;
    private final double y;
    private final double z;

    public Position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
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
