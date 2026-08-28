package com.sakarrobotics.cloud.robot.adapter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.AdapterType;

/**
 * Resolves the correct {@link RobotAdapter} for a robot model's configured
 * {@link AdapterType} — the runtime counterpart of
 * SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md §8's adapter-layer diagram.
 */
@Component
public class RobotAdapterRegistry {

    private final Map<AdapterType, RobotAdapter> adaptersByType;

    public RobotAdapterRegistry(List<RobotAdapter> adapters) {
        this.adaptersByType = adapters.stream()
                .collect(Collectors.toMap(RobotAdapter::adapterType, Function.identity()));
    }

    public RobotAdapter resolve(AdapterType adapterType) {
        RobotAdapter adapter = adaptersByType.get(adapterType);
        if (adapter == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "No adapter implementation is registered for " + adapterType);
        }
        return adapter;
    }
}
