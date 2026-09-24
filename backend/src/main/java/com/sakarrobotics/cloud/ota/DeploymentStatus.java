package com.sakarrobotics.cloud.ota;

/**
 * {@code RECORDED} (the default) means exactly what it says — an operator
 * logged that this push happened — never that a robot actually received or
 * applied it: no real OTA delivery channel exists anywhere in this
 * codebase (no MQTT OTA topic, no agent-side updater). {@code FAILED} is
 * for an operator recording a known failure (with {@code errorMessage}).
 * Never conflate this with a live device-confirmed rollout status.
 */
public enum DeploymentStatus {
    RECORDED,
    FAILED
}
