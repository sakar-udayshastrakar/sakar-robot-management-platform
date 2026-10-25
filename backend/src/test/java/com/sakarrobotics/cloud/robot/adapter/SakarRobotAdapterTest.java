package com.sakarrobotics.cloud.robot.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;

/**
 * Locks in {@link SakarRobotAdapter}'s deliberate fail-closed contract: while the
 * local MQTT -> Sakar Robot Agent -> Peanut SDK path is unimplemented, every
 * capability call must throw {@code FEATURE_NOT_YET_IMPLEMENTED} rather than
 * return fabricated data. This mirrors the Android side's RosRobotAdapterStubTest
 * and guards against a future "make it work" change silently returning fake
 * telemetry/results before the real integration (and hardware) exist.
 */
class SakarRobotAdapterTest {

    private final SakarRobotAdapter adapter = new SakarRobotAdapter();

    @Test
    void identifiesAsSakarNativeAdapter() {
        assertThat(adapter.adapterType()).isEqualTo(AdapterType.SAKAR_NATIVE);
    }

    @Test
    void declaresFullCapabilitySetButStillFailsClosed() {
        // It advertises the eventual full set, yet every call below must fail —
        // advertising a capability must never imply it actually works today.
        assertThat(adapter.supportedCapabilities())
                .containsExactlyInAnyOrder(RobotCapabilityType.values());
    }

    @Test
    void everyReadCapabilityThrowsNotYetImplemented() {
        assertFailsClosed(() -> adapter.getStatus(null));
        assertFailsClosed(() -> adapter.getBattery(null));
        assertFailsClosed(() -> adapter.getTelemetry(null));
        assertFailsClosed(() -> adapter.getAreas(null));
        assertFailsClosed(() -> adapter.getMap(null));
    }

    @Test
    void everyWriteCapabilityThrowsNotYetImplemented() {
        assertFailsClosed(() -> adapter.startTask(null, (AdapterTaskRequest) null));
        assertFailsClosed(() -> adapter.stopTask(null));
        assertFailsClosed(() -> adapter.pauseTask(null));
        assertFailsClosed(() -> adapter.resumeTask(null));
        assertFailsClosed(() -> adapter.returnToDock(null));
    }

    private static void assertFailsClosed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.FEATURE_NOT_YET_IMPLEMENTED));
    }
}
