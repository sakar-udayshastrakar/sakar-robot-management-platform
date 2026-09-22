package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * "Automatic Keenon Data Sync — Every 15 Minutes" slice. Pure orchestration
 * unit test: every category dependency is mocked, so this only verifies
 * {@link KeenonSyncOrchestrationService}'s own behavior (which categories
 * it calls, failure isolation, overlap prevention, result reporting) —
 * never re-exercises any category's own real sync/upsert logic, which is
 * already covered by that category's own test class (e.g. {@code
 * KeenonAreaSyncSchedulerTest}).
 */
@ExtendWith(MockitoExtension.class)
class KeenonSyncOrchestrationServiceTest {

    @Mock
    private KeenonProperties keenonProperties;
    @Mock
    private KeenonStatusSyncService keenonStatusSyncService;
    @Mock
    private KeenonAreaSyncScheduler keenonAreaSyncScheduler;
    @Mock
    private KeenonCleaningModeSyncScheduler keenonCleaningModeSyncScheduler;
    @Mock
    private KeenonBackPointSyncScheduler keenonBackPointSyncScheduler;
    @Mock
    private KeenonCleaningHistorySyncScheduler keenonCleaningHistorySyncScheduler;
    @Mock
    private KeenonMapMetadataSyncScheduler keenonMapMetadataSyncScheduler;
    @Mock
    private KeenonMapPointSyncScheduler keenonMapPointSyncScheduler;
    @Mock
    private KeenonMapImageSyncScheduler keenonMapImageSyncScheduler;

    private ListAppender<ILoggingEvent> logAppender;

    private KeenonSyncOrchestrationService service() {
        return new KeenonSyncOrchestrationService(keenonProperties, keenonStatusSyncService, keenonAreaSyncScheduler,
                keenonCleaningModeSyncScheduler, keenonBackPointSyncScheduler, keenonCleaningHistorySyncScheduler,
                keenonMapMetadataSyncScheduler, keenonMapPointSyncScheduler, keenonMapImageSyncScheduler);
    }

    @BeforeEach
    void captureLogs() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(KeenonSyncOrchestrationService.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(KeenonSyncOrchestrationService.class)).detachAppender(logAppender);
    }

    private static void setEnabled(KeenonSyncOrchestrationService orchestrator, boolean value) throws ReflectiveOperationException {
        var field = KeenonSyncOrchestrationService.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.set(orchestrator, value);
    }

    // ------------------------------------------------------------------
    // Scheduler gating.
    // ------------------------------------------------------------------

    @Test
    void scheduledSync_ownFlagDisabled_neverRunsACycle() throws ReflectiveOperationException {
        KeenonSyncOrchestrationService orchestrator = service();
        setEnabled(orchestrator, false);

        orchestrator.scheduledSync();

        verifyNoInteractions(keenonStatusSyncService, keenonAreaSyncScheduler, keenonCleaningModeSyncScheduler,
                keenonBackPointSyncScheduler, keenonCleaningHistorySyncScheduler, keenonMapMetadataSyncScheduler,
                keenonMapPointSyncScheduler, keenonMapImageSyncScheduler);
    }

    @Test
    void scheduledSync_ownFlagEnabled_butKeenonIntegrationItselfDisabled_neverRunsACycle() throws ReflectiveOperationException {
        KeenonSyncOrchestrationService orchestrator = service();
        setEnabled(orchestrator, true);
        when(keenonProperties.isEnabled()).thenReturn(false);

        orchestrator.scheduledSync();

        verifyNoInteractions(keenonStatusSyncService, keenonAreaSyncScheduler);
    }

    @Test
    void scheduledSync_bothFlagsEnabled_runsACycle() throws ReflectiveOperationException {
        KeenonSyncOrchestrationService orchestrator = service();
        setEnabled(orchestrator, true);
        when(keenonProperties.isEnabled()).thenReturn(true);

        orchestrator.scheduledSync();

        verify(keenonStatusSyncService).syncAll();
    }

    // ------------------------------------------------------------------
    // Central orchestration — every category called, independently.
    // ------------------------------------------------------------------

    @Test
    void syncAll_callsEveryCategoryExactlyOnce() {
        service().syncAll();

        verify(keenonStatusSyncService, times(1)).syncAll();
        verify(keenonAreaSyncScheduler, times(1)).syncAll();
        verify(keenonCleaningModeSyncScheduler, times(1)).syncAll();
        verify(keenonBackPointSyncScheduler, times(1)).syncAll();
        verify(keenonCleaningHistorySyncScheduler, times(1)).syncAll();
        verify(keenonMapMetadataSyncScheduler, times(1)).syncAll();
        verify(keenonMapPointSyncScheduler, times(1)).syncAll();
        verify(keenonMapImageSyncScheduler, times(1)).syncAll();
    }

    @Test
    void syncAll_neverCallsRobotDiscovery() {
        // KeenonRobotSyncService is deliberately not a constructor dependency at all —
        // this is a structural guarantee (no field, no injection point exists to call),
        // documented here as the explicit regression guard for that design decision.
        var fields = KeenonSyncOrchestrationService.class.getDeclaredFields();
        boolean hasRobotSyncServiceField = List.of(fields).stream()
                .anyMatch(f -> f.getType().equals(KeenonRobotSyncService.class));
        assertThat(hasRobotSyncServiceField).isFalse();
    }

    @Test
    void syncAll_oneCategoryFails_doesNotStopTheOthers() {
        doThrow(new RuntimeException("Keenon area sync unexpectedly failed"))
                .when(keenonAreaSyncScheduler).syncAll();

        KeenonSyncOrchestrationService.SyncCycleResult result = service().syncAll();

        verify(keenonAreaSyncScheduler).syncAll();
        verify(keenonCleaningModeSyncScheduler).syncAll();
        verify(keenonBackPointSyncScheduler).syncAll();
        verify(keenonCleaningHistorySyncScheduler).syncAll();
        verify(keenonMapMetadataSyncScheduler).syncAll();
        verify(keenonMapPointSyncScheduler).syncAll();
        verify(keenonMapImageSyncScheduler).syncAll();
        verify(keenonStatusSyncService).syncAll();
        assertThat(result.categoryStatus().get("areas")).isEqualTo("FAILED");
        assertThat(result.overallStatus()).isEqualTo("SUCCESS_WITH_WARNINGS");
    }

    @Test
    void syncAll_multipleCategoriesFail_theyAreAllIndependentlyIsolated() {
        doThrow(new RuntimeException("timeout")).when(keenonMapImageSyncScheduler).syncAll();
        doThrow(new RuntimeException("Keenon auth failed")).when(keenonCleaningHistorySyncScheduler).syncAll();

        KeenonSyncOrchestrationService.SyncCycleResult result = service().syncAll();

        assertThat(result.categoryStatus().get("mapImages")).isEqualTo("FAILED");
        assertThat(result.categoryStatus().get("cleaningHistory")).isEqualTo("FAILED");
        assertThat(result.categoryStatus().get("areas")).isEqualTo("SUCCESS");
        assertThat(result.categoryStatus().get("robotStatus")).isEqualTo("SUCCESS");
        assertThat(result.overallStatus()).isEqualTo("SUCCESS_WITH_WARNINGS");
        verify(keenonMapPointSyncScheduler).syncAll(); // never aborted by the two failures above
    }

    // ------------------------------------------------------------------
    // Result / status reporting.
    // ------------------------------------------------------------------

    @Test
    void syncAll_everyCategorySucceeds_reportsOverallSuccess() {
        KeenonSyncOrchestrationService.SyncCycleResult result = service().syncAll();

        assertThat(result.ran()).isTrue();
        assertThat(result.overallStatus()).isEqualTo("SUCCESS");
        assertThat(result.categoryStatus()).hasSize(8);
        assertThat(result.categoryStatus().values()).allMatch("SUCCESS"::equals);
        assertThat(result.completedAt()).isAfterOrEqualTo(result.startedAt());
    }

    // ------------------------------------------------------------------
    // Overlap prevention (C1-style concurrency proof, mirroring
    // RobotTaskControllerTest.concurrentStart_...).
    // ------------------------------------------------------------------

    @Test
    void syncAll_secondCallWhileFirstStillRunning_isSkippedNotQueued() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        doAnswerBlocking(keenonAreaSyncScheduler, firstEntered, releaseFirst);

        KeenonSyncOrchestrationService orchestrator = service();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<KeenonSyncOrchestrationService.SyncCycleResult> first = executor.submit(orchestrator::syncAll);

            assertThat(firstEntered.await(10, TimeUnit.SECONDS)).isTrue();
            KeenonSyncOrchestrationService.SyncCycleResult second = orchestrator.syncAll();

            assertThat(second.ran()).isFalse();
            assertThat(second.overallStatus()).isEqualTo("SKIPPED_ALREADY_RUNNING");

            releaseFirst.countDown();
            KeenonSyncOrchestrationService.SyncCycleResult firstResult = first.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.ran()).isTrue();
            assertThat(firstResult.overallStatus()).isEqualTo("SUCCESS");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void doAnswerBlocking(KeenonAreaSyncScheduler mock, CountDownLatch entered, CountDownLatch release) {
        org.mockito.Mockito.doAnswer(inv -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting for the release latch");
            }
            return null;
        }).when(mock).syncAll();
    }

    // ------------------------------------------------------------------
    // Secrets/tokens are never logged.
    // ------------------------------------------------------------------

    @Test
    void syncAll_neverLogsCredentialsOrTokens() {
        // This class has no field/reference to a client id, secret, or token at all
        // (unlike KeenonOAuthTokenService/KeenonApiClient, which own that concern) — this
        // asserts the observable log output contains none of the values a compromised
        // implementation might have leaked, as a concrete regression guard rather than a
        // purely structural one.
        lenient().when(keenonProperties.getClientId()).thenReturn("test-client-id-should-never-appear");
        lenient().when(keenonProperties.getClientSecret()).thenReturn("test-client-secret-should-never-appear");

        service().syncAll();

        String allLogs = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(allLogs).doesNotContain("test-client-id-should-never-appear");
        assertThat(allLogs).doesNotContain("test-client-secret-should-never-appear");
        assertThat(allLogs).containsIgnoringCase("KEENON_SYNC_START");
        assertThat(allLogs).containsIgnoringCase("KEENON_SYNC_COMPLETE");
    }

    // ------------------------------------------------------------------
    // The 15-minute schedule itself.
    // ------------------------------------------------------------------

    /**
     * The actual product requirement is "every 15 minutes, aligned to the wall
     * clock" — a regression here (an accidental hourly cron, a half-hour cron, or
     * a switch to fixedDelay/fixedRate, none of which guarantee clock alignment)
     * would be completely invisible to every other test in this class, since they
     * all invoke {@code syncAll()} directly. This asserts the trigger declared on
     * {@link KeenonSyncOrchestrationService#scheduledSync()} itself.
     */
    @Test
    void scheduledSync_isCronAlignedToEveryFifteenMinutesOnTheClock() throws NoSuchMethodException {
        Scheduled scheduled = KeenonSyncOrchestrationService.class.getMethod("scheduledSync").getAnnotation(Scheduled.class);

        // A cron trigger specifically — fixedDelay/fixedRate cannot guarantee that a
        // cycle lands on :00/:15/:30/:45 regardless of application start time.
        assertThat(scheduled.fixedDelay()).isEqualTo(-1L);
        assertThat(scheduled.fixedDelayString()).isEmpty();
        assertThat(scheduled.fixedRate()).isEqualTo(-1L);
        assertThat(scheduled.fixedRateString()).isEmpty();
        assertThat(scheduled.cron()).isNotEmpty();

        // The annotation carries a ${property:default} placeholder; this pins the
        // default that ships in application.yml, which is what actually runs unless an
        // operator deliberately overrides SAKAR_KEENON_SYNC_ORCHESTRATION_CRON.
        String placeholder = scheduled.cron();
        assertThat(placeholder).startsWith("${sakar.integration.keenon.sync-orchestration.cron:").endsWith("}");
        String cron = placeholder.substring(placeholder.indexOf(':') + 1, placeholder.length() - 1);

        // Prove the behavior, not just the string: from an arbitrary instant, the next
        // four firings must be exactly the next :00/:15/:30/:45 boundaries, 15 minutes
        // apart, always at second 0.
        LocalDateTime next = LocalDateTime.of(2026, 9, 22, 12, 7, 42);
        List<LocalDateTime> firings = new java.util.ArrayList<>();
        CronExpression expression = CronExpression.parse(cron);
        for (int i = 0; i < 4; i++) {
            next = expression.next(next);
            firings.add(next);
        }

        assertThat(firings).containsExactly(
                LocalDateTime.of(2026, 9, 22, 12, 15, 0),
                LocalDateTime.of(2026, 9, 22, 12, 30, 0),
                LocalDateTime.of(2026, 9, 22, 12, 45, 0),
                LocalDateTime.of(2026, 9, 22, 13, 0, 0));

        // And the boundary is crossed hourly without drift, not just within one hour.
        LocalDateTime acrossMidnight = expression.next(LocalDateTime.of(2026, 9, 22, 23, 46, 1));
        assertThat(acrossMidnight).isEqualTo(LocalDateTime.of(2026, 9, 23, 0, 0, 0));
    }
}
