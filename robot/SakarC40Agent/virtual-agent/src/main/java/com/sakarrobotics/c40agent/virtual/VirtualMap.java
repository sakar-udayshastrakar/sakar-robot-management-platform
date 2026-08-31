package com.sakarrobotics.c40agent.virtual;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.sakarrobotics.c40agent.telemetry.Destination;
import com.sakarrobotics.c40agent.telemetry.Orientation;
import com.sakarrobotics.c40agent.telemetry.Pose;
import com.sakarrobotics.c40agent.telemetry.Position;

/**
 * A deterministic, clearly-fake test map for the Virtual C40 Robot
 * Simulator (Roadmap Phase 9, see ../../VIRTUAL_C40_SIMULATOR.md).
 *
 * <p><strong>These destination IDs are simulation-only and must never be
 * used on a physical C40.</strong> They were not read from, and do not
 * correspond to, any real C40 S map (per {@code
 * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md}, real destination ids
 * require a live {@code getAllDestPose()} call against a specific
 * physical robot's currently-loaded map - this class deliberately does
 * not attempt to look like that data).
 */
public final class VirtualMap {

    /** Deliberately not a plausible-looking real map hash - see class Javadoc. */
    public static final String MAP_ID = "VIRTUAL_TEST_MAP";
    public static final String MAP_MD5 = "VIRTUAL-SIMULATOR-NOT-A-REAL-MD5";

    public static final int DESTINATION_ID_LOBBY = 1001;
    public static final int DESTINATION_ID_RECEPTION = 1002;
    public static final int DESTINATION_ID_ROOM_A = 1003;
    public static final int DESTINATION_ID_CHARGING_STATION = 1004;

    private static final List<Destination> DESTINATIONS = Collections.unmodifiableList(Arrays.asList(
            destination(DESTINATION_ID_LOBBY, "Lobby", "landmark", 0.0, 0.0, 0.0),
            destination(DESTINATION_ID_RECEPTION, "Reception", "landmark", 3.0, 0.0, 0.0),
            destination(DESTINATION_ID_ROOM_A, "Room A", "landmark", 3.0, 4.0, 0.0),
            destination(DESTINATION_ID_CHARGING_STATION, "Charging Station", "charger", 0.0, 4.0, 0.0)));

    private VirtualMap() {
    }

    /** The robot's starting position - the Lobby, matching {@link #DESTINATION_ID_LOBBY}. */
    public static Destination home() {
        return DESTINATIONS.get(0);
    }

    public static Destination chargingStation() {
        return byId(DESTINATION_ID_CHARGING_STATION);
    }

    /** All destinations on {@link #MAP_ID} - the virtual equivalent of a real getAllDestPose() response. */
    public static List<Destination> allDestinations() {
        return DESTINATIONS;
    }

    /** {@code null} if no destination with this id exists on this map - never invented. */
    public static Destination byId(int destinationId) {
        for (Destination destination : DESTINATIONS) {
            if (destination.getId() == destinationId) {
                return destination;
            }
        }
        return null;
    }

    private static Destination destination(int id, String name, String type, double x, double y, double z) {
        Pose pose = new Pose(new Position(x, y, z), new Orientation(1.0, 0.0, 0.0, 0.0));
        return new Destination(id, name, pose, MAP_MD5, 0, type);
    }
}
