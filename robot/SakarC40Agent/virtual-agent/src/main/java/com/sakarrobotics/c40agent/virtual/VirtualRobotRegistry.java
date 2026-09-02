package com.sakarrobotics.c40agent.virtual;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds independent {@link VirtualRobotEngine} instances (Roadmap Phase 9
 * "Multi-robot", see ../../VIRTUAL_C40_SIMULATOR.md). Each engine owns its
 * own position/battery/map/destination/command state - a command issued
 * against one robotId never touches any other engine in this registry,
 * because each is a fully separate object with no shared mutable state.
 */
public final class VirtualRobotRegistry {

    public static final String DEFAULT_ROBOT_1 = "VIRTUAL-C40-001";
    public static final String DEFAULT_ROBOT_2 = "VIRTUAL-C40-002";
    public static final String DEFAULT_ROBOT_3 = "VIRTUAL-C40-003";

    private final Map<String, VirtualRobotEngine> enginesByRobotId = new LinkedHashMap<>();

    /** Creates a registry pre-populated with the standard 3 default virtual robots. */
    public static VirtualRobotRegistry withDefaultFleet() {
        VirtualRobotRegistry registry = new VirtualRobotRegistry();
        registry.register(new VirtualRobotEngine(DEFAULT_ROBOT_1, "Virtual C40 #1"));
        registry.register(new VirtualRobotEngine(DEFAULT_ROBOT_2, "Virtual C40 #2"));
        registry.register(new VirtualRobotEngine(DEFAULT_ROBOT_3, "Virtual C40 #3"));
        return registry;
    }

    public void register(VirtualRobotEngine engine) {
        enginesByRobotId.put(engine.getRobotId(), engine);
    }

    /** {@code null} if no engine is registered under this robotId - never fabricated. */
    public VirtualRobotEngine get(String robotId) {
        return enginesByRobotId.get(robotId);
    }

    public Map<String, VirtualRobotEngine> all() {
        return Collections.unmodifiableMap(enginesByRobotId);
    }
}
