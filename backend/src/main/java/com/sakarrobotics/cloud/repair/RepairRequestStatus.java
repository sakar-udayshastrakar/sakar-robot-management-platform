package com.sakarrobotics.cloud.repair;

/** Vendor-neutral work-order lifecycle for {@link RepairRequest} (Operation And Maintenance Platform). */
public enum RepairRequestStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
