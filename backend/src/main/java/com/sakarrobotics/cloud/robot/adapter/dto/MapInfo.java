package com.sakarrobotics.cloud.robot.adapter.dto;

import java.util.List;

public record MapInfo(String vendorMapId, String imageUrl, List<AreaInfo> areas) {
}
