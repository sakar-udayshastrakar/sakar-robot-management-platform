package com.sakarrobotics.cloud.dashboard.dto;

import java.time.LocalDate;

public record DailyTaskCount(LocalDate date, long count) {
}
