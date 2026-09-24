package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.robot.registry.dto.RobotModelResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Read-only robot-model reference list — no schema change, this data
 * (`robot_models` joined with `robot_manufacturers`) already existed but had
 * no REST endpoint, so the frontend could only ever show a raw, truncated
 * {@code robotModelId} UUID (see {@code RobotDetailPage.tsx}'s hardcoded
 * "Sakar CleanBot 5000 Plus" workaround). Gated by {@code ROBOT_VIEW},
 * matching every other robot-registry read endpoint.
 */
@RestController
@RequestMapping("/api/v1/robot-models")
@RequiredArgsConstructor
@Tag(name = "Robot Models")
public class RobotModelController {

    private final RobotModelRepository robotModelRepository;
    private final RobotManufacturerRepository robotManufacturerRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List all robot models (vendor-neutral reference data)")
    public ApiResponse<List<RobotModelResponse>> list() {
        List<RobotModel> models = robotModelRepository.findAll();
        Map<UUID, String> manufacturerNames = robotManufacturerRepository.findAllById(
                models.stream().map(RobotModel::getManufacturerId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(RobotManufacturer::getId, RobotManufacturer::getName));
        return ApiResponse.ok(models.stream()
                .map(m -> RobotModelResponse.from(m, manufacturerNames.get(m.getManufacturerId())))
                .toList());
    }
}
