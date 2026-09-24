package com.sakarrobotics.cloud.remotedeployment;

/**
 * RECORDED means an operator logged that they manually deployed a
 * configuration to a robot. COMPLETED/FAILED are only ever set by that same
 * operator's own explicit follow-up action — never inferred from a device
 * response, because no remote-configuration-push channel exists in this
 * codebase (Operation And Maintenance Platform → Remote Deployment).
 */
public enum RemoteDeploymentStatus {
    RECORDED,
    COMPLETED,
    FAILED
}
