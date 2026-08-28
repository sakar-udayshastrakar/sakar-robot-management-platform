package com.sakarrobotics.cloud.robot.registry.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Locks in the "external clients never see the vendor identifier" rule
 * (Master Requirements "Keenon Cloud Integration": "do not expose vendor
 * naming to external clients") at the type level, so a future edit that
 * adds {@code externalRobotId} back onto the public response shape fails
 * this test rather than silently shipping.
 */
class RobotResponseSecrecyTest {

    @Test
    void publicRobotResponse_neverCarriesTheVendorExternalId() {
        RecordComponent[] components = RobotResponse.class.getRecordComponents();
        boolean leaksVendorId = Arrays.stream(components)
                .anyMatch(c -> c.getName().toLowerCase().contains("external"));
        assertThat(leaksVendorId).as("RobotResponse must never expose the vendor's external robot id").isFalse();
    }
}
