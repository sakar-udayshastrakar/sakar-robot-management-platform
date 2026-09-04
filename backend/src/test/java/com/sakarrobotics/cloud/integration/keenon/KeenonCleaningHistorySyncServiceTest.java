package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.cleaning.CleaningSession;
import com.sakarrobotics.cloud.cleaning.CleaningSessionService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.Robot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keenon cleaning-history-sync slice. Verifies field parsing against only
 * evidenced fields, bounded pagination, and that history is append-only —
 * never overwritten, never destroyed by a vendor failure or empty response.
 */
@ExtendWith(MockitoExtension.class)
class KeenonCleaningHistorySyncServiceTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private CleaningSessionService cleaningSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonCleaningHistorySyncService service(int pageSize, int maxPages) throws ReflectiveOperationException {
        KeenonCleaningHistorySyncService service = new KeenonCleaningHistorySyncService(client, cleaningSessionService);
        setField(service, "pageSize", pageSize);
        setField(service, "maxPages", maxPages);
        return service;
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = KeenonCleaningHistorySyncService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Robot aKeenonRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(UUID.randomUUID());
        robot.setSiteId(UUID.randomUUID());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private void stubRecordAlwaysCreatesNew() {
        when(cleaningSessionService.recordFromKeenonHistory(any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(Optional.of(new CleaningSession()));
    }

    @Test
    void sync_parsesOnlyEvidencedFields_andRecordsThem() throws Exception {
        Robot robot = aKeenonRobot();
        stubRecordAlwaysCreatesNew();
        JsonNode page1 = objectMapper.readTree("{\"count\":1,\"currentPage\":1,\"pageSize\":50,\"entities\":["
                + "{\"cleanArea\":13.24,\"cleanEfficiency\":429.57,\"cleanTiming\":229,\"mState\":\"1\","
                + "\"failDescCode\":\"0\",\"mapInfo\":[{\"mapId\":\"m1\",\"taskSnapshot\":\"https://example.com/s.png\"}]}]}");
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 50)).thenReturn(page1);

        int newRecords = service(50, 5).sync(robot, "C00715655");

        assertThat(newRecords).isEqualTo(1);
        // failDesc is absent in this entry, so failureReason falls back to failDescCode ("0")
        // — not null — matching the documented failDesc-then-failDescCode fallback.
        verify(cleaningSessionService).recordFromKeenonHistory(eq(robot), eq(13.24), eq(429.57), eq(229L),
                eq("mState:1"), eq("0"), eq("https://example.com/s.png"),
                eq("keenon-log:area=13.24;efficiency=429.57;duration=229;mState=1;failDescCode=0"));
    }

    @Test
    void sync_usesExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setSerialNumber("SR-CB-2026-000001");
        when(client.getCleaningLogs(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(objectMapper.readTree("{\"entities\":[]}"));

        service(50, 5).sync(robot, "C00715655");

        ArgumentCaptor<String> robotSnArg = ArgumentCaptor.forClass(String.class);
        verify(client).getCleaningLogs(eq("C00715655"), robotSnArg.capture(), eq(1), eq(50));
        assertThat(robotSnArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");
    }

    @Test
    void sync_repeatedSync_doesNotCreateADuplicateRow() throws Exception {
        Robot robot = aKeenonRobot();
        JsonNode page = objectMapper.readTree("{\"entities\":[{\"cleanArea\":13.24,\"mState\":\"1\"}]}");
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 50)).thenReturn(page);

        // First sync: new row.
        when(cleaningSessionService.recordFromKeenonHistory(any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(Optional.of(new CleaningSession()));
        int firstRun = service(50, 5).sync(robot, "C00715655");

        // Second sync: the dedup check inside CleaningSessionService itself would return
        // empty for the same vendorReference — simulate that here at this service's boundary.
        when(cleaningSessionService.recordFromKeenonHistory(any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(Optional.empty());
        int secondRun = service(50, 5).sync(robot, "C00715655");

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(0);
    }

    @Test
    void sync_missingExternalRobotId_throwsIntegrationUnavailable_neverCallsTheVendor() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service(50, 5).sync(robot, "C00715655"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void sync_vendorApiThrowsOnASecondPage_propagates_butFirstPagesRecordsAreNotUndone() throws Exception {
        Robot robot = aKeenonRobot();
        stubRecordAlwaysCreatesNew();
        // Page 1: a full page (pageSize=1) — triggers an attempt at page 2.
        JsonNode page1 = objectMapper.readTree("{\"entities\":[{\"cleanArea\":1.0,\"mState\":\"1\"}]}");
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 1)).thenReturn(page1);
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 2, 1))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service(1, 5).sync(robot, "C00715655"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        // Page 1's entry was already recorded (a separate, already-committed unit of work)
        // before the page-2 failure — never undone by the later failure.
        verify(cleaningSessionService, times(1)).recordFromKeenonHistory(any(), anyDouble(), any(), any(), any(),
                any(), any(), anyString());
    }

    @Test
    void sync_emptyVendorResponse_recordsNothing_doesNotThrow() throws Exception {
        Robot robot = aKeenonRobot();
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 50))
                .thenReturn(objectMapper.readTree("{\"entities\":[]}"));

        int newRecords = service(50, 5).sync(robot, "C00715655");

        assertThat(newRecords).isEqualTo(0);
        org.mockito.Mockito.verifyNoInteractions(cleaningSessionService);
    }

    @Test
    void sync_malformedEntryMissingEveryField_isHandledSafely_neverThrows() throws Exception {
        Robot robot = aKeenonRobot();
        stubRecordAlwaysCreatesNew();
        JsonNode page = objectMapper.readTree("{\"entities\":[{}]}");
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 50)).thenReturn(page);

        int newRecords = service(50, 5).sync(robot, "C00715655");

        assertThat(newRecords).isEqualTo(1);
        verify(cleaningSessionService).recordFromKeenonHistory(eq(robot), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq("keenon-log:area=null;efficiency=null;duration=null;mState=null;failDescCode=null"));
    }

    @Test
    void sync_multiplePages_stopsAtTheFirstPartialPage() throws Exception {
        Robot robot = aKeenonRobot();
        stubRecordAlwaysCreatesNew();
        JsonNode fullPage = objectMapper.readTree("{\"entities\":[{\"cleanArea\":1.0},{\"cleanArea\":2.0}]}");
        JsonNode partialPage = objectMapper.readTree("{\"entities\":[{\"cleanArea\":3.0}]}");
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 2)).thenReturn(fullPage);
        when(client.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 2, 2)).thenReturn(partialPage);

        int newRecords = service(2, 5).sync(robot, "C00715655");

        assertThat(newRecords).isEqualTo(3);
        verify(client, never()).getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 3, 2);
    }

    @Test
    void sync_everyPageFull_stopsAtTheMaxPagesBound_neverLoopsIndefinitely() throws Exception {
        Robot robot = aKeenonRobot();
        stubRecordAlwaysCreatesNew();
        JsonNode fullPage = objectMapper.readTree("{\"entities\":[{\"cleanArea\":1.0}]}");
        when(client.getCleaningLogs(eq("C00715655"), eq("94:BA:06:CA:99:F3"), anyInt(), eq(1))).thenReturn(fullPage);

        int newRecords = service(1, 3).sync(robot, "C00715655");

        assertThat(newRecords).isEqualTo(3);
        verify(client, times(3)).getCleaningLogs(eq("C00715655"), eq("94:BA:06:CA:99:F3"), anyInt(), eq(1));
        verify(client, never()).getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 4, 1);
    }
}
