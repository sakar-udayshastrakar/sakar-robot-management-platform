package com.sakarrobotics.cloud.org.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSiteRequest(
        @NotBlank String name,
        String address,
        String timezone,
        String area,
        String contactName,
        String phone,
        String email,
        String sceneType,
        Boolean chainBrand) {
}
