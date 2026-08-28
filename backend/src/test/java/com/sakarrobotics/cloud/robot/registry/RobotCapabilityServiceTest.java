package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

@ExtendWith(MockitoExtension.class)
class RobotCapabilityServiceTest {

    @Mock
    private RobotCapabilityRepository repository;

    @Test
    void assertSupported_whenFlaggedSupported_doesNotThrow() {
        UUID modelId = UUID.randomUUID();
        when(repository.findByRobotModelIdAndCapability(modelId, RobotCapabilityType.GET_BATTERY))
                .thenReturn(Optional.of(new RobotCapability(modelId, RobotCapabilityType.GET_BATTERY, true)));

        new RobotCapabilityService(repository).assertSupported(modelId, RobotCapabilityType.GET_BATTERY);
    }

    @Test
    void assertSupported_whenFlaggedUnsupported_throwsUnsupportedCapability() {
        UUID modelId = UUID.randomUUID();
        when(repository.findByRobotModelIdAndCapability(modelId, RobotCapabilityType.LOCK))
                .thenReturn(Optional.of(new RobotCapability(modelId, RobotCapabilityType.LOCK, false)));

        assertThatThrownBy(() -> new RobotCapabilityService(repository).assertSupported(modelId, RobotCapabilityType.LOCK))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNSUPPORTED_CAPABILITY));
    }

    @Test
    void assertSupported_whenNoRowAtAll_throwsUnsupportedCapability() {
        UUID modelId = UUID.randomUUID();
        when(repository.findByRobotModelIdAndCapability(modelId, RobotCapabilityType.UNLOCK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RobotCapabilityService(repository).assertSupported(modelId, RobotCapabilityType.UNLOCK))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNSUPPORTED_CAPABILITY));
    }
}
