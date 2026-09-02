package com.sakarrobotics.c40agent.telemetry;

/**
 * A single pre-registered navigation destination on the robot's own
 * currently-loaded map, as returned by {@code
 * NavigationComponent.getAllDestPose()} (verified by decompiling
 * peanut-sdk-release.aar - see {@code
 * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md} for the full evidence
 * trail, including exactly which vendor fields this class does and does
 * not mirror). Only the sdk module is allowed to import com.keenon.*
 * types; this class is how that data crosses the module boundary -
 * nothing Keenon-specific (class names, CoAP paths, field names like
 * {@code bind_map_md5}) appears here or in anything built on top of it.
 *
 * <p>{@code id} is the value {@code GO_TO_POINT}'s {@code destinationId}
 * must supply - this class exists specifically so that id no longer has
 * to be guessed or invented by a caller.
 */
public final class Destination {

    private final int id;
    private final String name;
    private final Pose pose;
    private final String mapId;
    private final int floor;
    private final String type;

    public Destination(int id, String name, Pose pose, String mapId, int floor, String type) {
        this.id = id;
        this.name = name;
        this.pose = pose;
        this.mapId = mapId;
        this.floor = floor;
        this.type = type;
    }

    /** The value to pass as GO_TO_POINT's {@code destinationId}. */
    public int getId() {
        return id;
    }

    /**
     * May be {@code null} or empty - the vendor response has a {@code
     * name} field, but this project has not confirmed it is always
     * populated on a physical robot (not physically validated). Callers
     * must not assume a non-empty label.
     */
    public String getName() {
        return name;
    }

    /** May be {@code null} if the vendor response omitted pose data for this destination. */
    public Pose getPose() {
        return pose;
    }

    /**
     * The map this destination is registered against (mirrors the
     * vendor's {@code bind_map_md5} field) - may be {@code null}. This is
     * the only verified map-relationship field; no map id/name beyond
     * this hash is available from this API.
     */
    public String getMapId() {
        return mapId;
    }

    public int getFloor() {
        return floor;
    }

    /** Vendor-supplied destination type string (e.g. a landmark/area classification), may be {@code null}. */
    public String getType() {
        return type;
    }
}
