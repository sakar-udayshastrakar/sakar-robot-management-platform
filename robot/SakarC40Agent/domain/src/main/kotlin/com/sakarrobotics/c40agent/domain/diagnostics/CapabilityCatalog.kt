package com.sakarrobotics.c40agent.domain.diagnostics

import com.sakarrobotics.c40agent.domain.model.Capability

/** One feature of the app and how real its current backing is. [note] says why, in plain words. */
data class CapabilityEntry(val name: String, val capability: Capability, val note: String)

/**
 * The app's own statement of what is real, simulated, local, gated, unknown or unavailable.
 * It describes the *state of the implementation in this build*, not hardware verification: nothing
 * here is marked REAL unless the code path is a plain read of an in-process SDK/controller state.
 * Anything that depends on robot hardware answering is UNKNOWN until verified on the robot.
 * Update this list whenever a feature's backing changes - the Diagnostics screen renders it as-is.
 */
object CapabilityCatalog {

    val entries: List<CapabilityEntry> = listOf(
        CapabilityEntry(
            "SDK link state", Capability.REAL,
            "Read from the in-process controller; says whether SDK initialisation succeeded, not that the robot is healthy."
        ),
        CapabilityEntry(
            "Battery percentage and charging state", Capability.UNKNOWN,
            "The SDK answers, but its payload shape is unverified on hardware, so it is shown raw and never parsed."
        ),
        CapabilityEntry(
            "Runtime info (work mode, power, odometer, e-stop)", Capability.UNKNOWN,
            "Taken from the SDK runtime snapshot; field meanings are unverified on hardware."
        ),
        CapabilityEntry(
            "Sensor reads (LiDAR, depth, sonar, IMU)", Capability.UNKNOWN,
            "Raw SDK responses shown verbatim on request; not interpreted. A failed read is reported as not available."
        ),
        CapabilityEntry(
            "Map information", Capability.UNKNOWN,
            "Raw map-info response only; its format is not confirmed, so no map details are inferred."
        ),
        CapabilityEntry(
            "Map image preview", Capability.UNAVAILABLE,
            "No renderable image format is confirmed for the robot's map."
        ),
        CapabilityEntry(
            "SLAM mapping and map deployment", Capability.UNAVAILABLE,
            "No ROS mapping path or confirmed map file format exists yet."
        ),
        CapabilityEntry(
            "ROS integration", Capability.UNAVAILABLE,
            "Adapter is a placeholder; nothing is read from or sent to a ROS computer."
        ),
        CapabilityEntry(
            "Cleaning run (start, pause, stop, progress)", Capability.SIMULATED,
            "Local simulation - no cleaning command reaches the robot."
        ),
        CapabilityEntry(
            "Manual drive", Capability.SIMULATED,
            "Drives a virtual robot in this build; the real motor backend is not selected."
        ),
        CapabilityEntry(
            "Navigation to a point, pause and resume", Capability.GATED,
            "Locked unless the controller is in HARDWARE_TEST mode, which the app never enables by itself."
        ),
        CapabilityEntry(
            "Return to dock and charging control", Capability.GATED,
            "Locked unless the controller is in HARDWARE_TEST mode."
        ),
        CapabilityEntry(
            "Workstation (water refill, drainage)", Capability.UNAVAILABLE,
            "No workstation API exists in the vendored SDK."
        ),
        CapabilityEntry(
            "Sakar Cloud sync", Capability.UNAVAILABLE,
            "No cloud client is wired into the operator UI; always reported offline."
        ),
        CapabilityEntry(
            "Schedules, consumables, routes and cleaning zones", Capability.LOCAL,
            "Stored on this tablet only; never sent to the robot."
        )
    )
}
