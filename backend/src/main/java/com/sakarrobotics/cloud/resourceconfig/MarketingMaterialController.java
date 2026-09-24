package com.sakarrobotics.cloud.resourceconfig;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.resourceconfig.dto.CreateMarketingMaterialRequest;
import com.sakarrobotics.cloud.resourceconfig.dto.UpdateMarketingMaterialRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * New Resource Configuration → Marketing materials. Named material packages
 * only — no file/asset storage in this pass (the reference product's own
 * upload flow is a separate, larger scope not attempted here).
 */
@RestController
@RequestMapping("/api/v1/marketing-materials")
@RequiredArgsConstructor
@Tag(name = "Marketing Materials")
public class MarketingMaterialController {

    private final MarketingMaterialService marketingMaterialService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List marketing materials within the caller's organization scope")
    public ApiResponse<List<MarketingMaterial>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(marketingMaterialService.listAccessible(principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Add a marketing material")
    public ResponseEntity<ApiResponse<MarketingMaterial>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateMarketingMaterialRequest request) {
        MarketingMaterial material = marketingMaterialService.create(principal, request.organizationId(), request.name(),
                request.materialType());
        return ResponseEntity.status(201).body(ApiResponse.ok(material));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Edit a marketing material")
    public ApiResponse<MarketingMaterial> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMarketingMaterialRequest request) {
        return ApiResponse.ok(marketingMaterialService.update(principal, id, request.name(), request.materialType(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Delete a marketing material")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        marketingMaterialService.delete(principal, id);
        return ApiResponse.ok(null);
    }
}
