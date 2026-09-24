package com.sakarrobotics.cloud.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.dashboard.dto.DailyTaskCount;
import com.sakarrobotics.cloud.dashboard.dto.HotelTaskRecordResponse;
import com.sakarrobotics.cloud.dashboard.dto.OperationRankingResponse;
import com.sakarrobotics.cloud.dashboard.dto.RankingEntry;
import com.sakarrobotics.cloud.dashboard.dto.RetentionRow;
import com.sakarrobotics.cloud.dashboard.dto.StoreRealtimeStatsResponse;
import com.sakarrobotics.cloud.dashboard.dto.TaskTypeShare;
import com.sakarrobotics.cloud.org.Site;
import com.sakarrobotics.cloud.org.SiteRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;
import com.sakarrobotics.cloud.task.RobotTask;
import com.sakarrobotics.cloud.task.RobotTaskRepository;
import com.sakarrobotics.cloud.telemetry.RobotConnectionStatus;
import com.sakarrobotics.cloud.telemetry.RobotConnectivityService;

import lombok.RequiredArgsConstructor;

/**
 * Operational Dashboard — every figure here is computed from real {@code
 * robot_tasks}/{@code robots}/{@code sites} rows, never simulated. Mileage,
 * call counts, and hotel room counts are the one exception: no
 * distance/odometer, call/summon, or room concept exists anywhere in this
 * codebase, so those fields are always {@code null} ("not tracked") rather
 * than a fabricated zero or invented number — see each DTO's own Javadoc.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RobotTaskRepository robotTaskRepository;
    private final RobotRepository robotRepository;
    private final SiteRepository siteRepository;
    private final RobotConnectivityService robotConnectivityService;
    private final TenantAccessGuard tenantAccessGuard;

    private List<RobotTask> allAccessibleTasks(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null ? robotTaskRepository.findAll() : robotTaskRepository.findByOrganizationIdIn(orgIds);
    }

    private List<RobotTask> accessibleTasksSince(UserPrincipal principal, Instant since) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? robotTaskRepository.findByCreatedAtGreaterThanEqual(since)
                : robotTaskRepository.findByOrganizationIdInAndCreatedAtGreaterThanEqual(orgIds, since);
    }

    private List<Robot> allAccessibleRobots(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null ? robotRepository.findAll() : robotRepository.findByOrganizationIdIn(orgIds);
    }

    private List<Site> allAccessibleSites(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null ? siteRepository.findAll() : siteRepository.findByOrganizationIdIn(orgIds);
    }

    private static List<RankingEntry> topRankings(Map<String, Long> counts, Map<String, String> labels) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new RankingEntry(e.getKey(), labels.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .toList();
    }

    public OperationRankingResponse operationRanking(UserPrincipal principal) {
        List<RobotTask> tasks = allAccessibleTasks(principal);
        List<Robot> robots = allAccessibleRobots(principal);
        List<Site> sites = allAccessibleSites(principal);

        Map<UUID, Robot> robotsById = robots.stream().collect(java.util.stream.Collectors.toMap(Robot::getId, r -> r));
        Map<String, String> siteNames = sites.stream().collect(java.util.stream.Collectors.toMap(s -> s.getId().toString(), Site::getName));
        Map<String, String> robotSerials = robots.stream().collect(java.util.stream.Collectors.toMap(r -> r.getId().toString(), Robot::getSerialNumber));

        Map<String, Long> tasksBySite = new HashMap<>();
        Map<String, Long> tasksByRobot = new HashMap<>();
        for (RobotTask task : tasks) {
            tasksByRobot.merge(task.getRobotId().toString(), 1L, Long::sum);
            Robot robot = robotsById.get(task.getRobotId());
            if (robot != null && robot.getSiteId() != null) {
                tasksBySite.merge(robot.getSiteId().toString(), 1L, Long::sum);
            }
        }

        return new OperationRankingResponse(
                tasks.size(),
                null,
                null,
                topRankings(tasksBySite, siteNames),
                topRankings(tasksByRobot, robotSerials),
                null,
                null);
    }

    public StoreRealtimeStatsResponse storeRealtimeStats(UserPrincipal principal, UUID siteId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<RobotTask> todaysTasks = accessibleTasksSince(principal, startOfToday);
        List<Robot> robots = allAccessibleRobots(principal);
        Map<UUID, Robot> robotsById = robots.stream().collect(java.util.stream.Collectors.toMap(Robot::getId, r -> r));

        if (siteId != null) {
            todaysTasks = todaysTasks.stream()
                    .filter(t -> {
                        Robot robot = robotsById.get(t.getRobotId());
                        return robot != null && siteId.equals(robot.getSiteId());
                    })
                    .toList();
            robots = robots.stream().filter(r -> siteId.equals(r.getSiteId())).toList();
        }

        Map<String, Long> byTaskType = new HashMap<>();
        for (RobotTask task : todaysTasks) {
            byTaskType.merge(task.getTaskType(), 1L, Long::sum);
        }
        long total = todaysTasks.size();
        List<TaskTypeShare> shares = byTaskType.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new TaskTypeShare(e.getKey(), e.getValue(), total == 0 ? 0.0 : (e.getValue() * 100.0) / total))
                .toList();

        long activeMachines = robots.stream()
                .filter(r -> robotConnectivityService.statusFor(r.getId()) == RobotConnectionStatus.ONLINE)
                .count();

        return new StoreRealtimeStatsResponse(total, shares, null, activeMachines, null, null);
    }

    public List<RetentionRow> retentionAnalytics(UserPrincipal principal, int displayDays) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int maxStreak = 15;
        LocalDate earliestNeeded = today.minusDays(displayDays - 1L).minusDays(maxStreak - 1L);
        Instant since = earliestNeeded.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<RobotTask> tasks = accessibleTasksSince(principal, since);
        List<Robot> robots = allAccessibleRobots(principal);
        List<Site> sites = allAccessibleSites(principal);
        Map<UUID, UUID> robotToSite = new HashMap<>();
        for (Robot robot : robots) {
            if (robot.getSiteId() != null) {
                robotToSite.put(robot.getId(), robot.getSiteId());
            }
        }

        Map<UUID, Set<LocalDate>> activeDaysBySite = new HashMap<>();
        for (RobotTask task : tasks) {
            UUID siteId = robotToSite.get(task.getRobotId());
            if (siteId == null) {
                continue;
            }
            LocalDate day = task.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            activeDaysBySite.computeIfAbsent(siteId, k -> new HashSet<>()).add(day);
        }

        List<RetentionRow> rows = new ArrayList<>();
        for (int i = 0; i < displayDays; i++) {
            LocalDate date = today.minusDays(i);
            long used3 = 0;
            long used7 = 0;
            long used15 = 0;
            long unused3 = 0;
            long unused7 = 0;
            long unused15 = 0;
            for (Site site : sites) {
                Set<LocalDate> activeDays = activeDaysBySite.getOrDefault(site.getId(), Set.of());
                boolean streak3 = hasStreak(activeDays, date, 3);
                boolean streak7 = hasStreak(activeDays, date, 7);
                boolean streak15 = hasStreak(activeDays, date, 15);
                if (streak3) {
                    used3++;
                }
                if (streak7) {
                    used7++;
                }
                if (streak15) {
                    used15++;
                }
                if (noneActive(activeDays, date, 3)) {
                    unused3++;
                }
                if (noneActive(activeDays, date, 7)) {
                    unused7++;
                }
                if (noneActive(activeDays, date, 15)) {
                    unused15++;
                }
            }
            rows.add(new RetentionRow(date, used3, used7, used15, unused3, unused7, unused15));
        }
        rows.sort(Comparator.comparing(RetentionRow::date).reversed());
        return rows;
    }

    private static boolean hasStreak(Set<LocalDate> activeDays, LocalDate endInclusive, int length) {
        for (int i = 0; i < length; i++) {
            if (!activeDays.contains(endInclusive.minusDays(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean noneActive(Set<LocalDate> activeDays, LocalDate endInclusive, int length) {
        for (int i = 0; i < length; i++) {
            if (activeDays.contains(endInclusive.minusDays(i))) {
                return false;
            }
        }
        return true;
    }

    public HotelTaskRecordResponse hotelTaskRecord(UserPrincipal principal, UUID siteId, UUID robotId, String taskType,
            Instant from, Instant to) {
        List<RobotTask> tasks = allAccessibleTasks(principal);
        List<Robot> robots = allAccessibleRobots(principal);
        Map<UUID, Robot> robotsById = robots.stream().collect(java.util.stream.Collectors.toMap(Robot::getId, r -> r));

        List<RobotTask> filtered = tasks.stream()
                .filter(t -> robotId == null || robotId.equals(t.getRobotId()))
                .filter(t -> taskType == null || taskType.equalsIgnoreCase(t.getTaskType()))
                .filter(t -> from == null || !t.getCreatedAt().isBefore(from))
                .filter(t -> to == null || !t.getCreatedAt().isAfter(to))
                .filter(t -> {
                    if (siteId == null) {
                        return true;
                    }
                    Robot robot = robotsById.get(t.getRobotId());
                    return robot != null && siteId.equals(robot.getSiteId());
                })
                .toList();

        long cumulativeDurationSeconds = filtered.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getUpdatedAt()).getSeconds())
                .sum();

        Map<LocalDate, Long> byDay = new HashMap<>();
        for (RobotTask task : filtered) {
            LocalDate day = task.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDay.merge(day, 1L, Long::sum);
        }
        List<DailyTaskCount> dailyBreakdown = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyTaskCount(e.getKey(), e.getValue()))
                .toList();

        return new HotelTaskRecordResponse(filtered.size(), null, cumulativeDurationSeconds, null, dailyBreakdown);
    }
}
