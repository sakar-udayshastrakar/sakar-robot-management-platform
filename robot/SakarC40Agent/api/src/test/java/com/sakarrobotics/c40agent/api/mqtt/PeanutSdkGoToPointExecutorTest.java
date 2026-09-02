package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Software-test-only coverage of {@link PeanutSdkGoToPointExecutor}
 * (Roadmap Phase 8 "GO_TO_POINT", see {@code
 * C40_S_GO_TO_POINT_SDK_INVESTIGATION.md}). {@link GoToPointGateway} is
 * faked here — no Android runtime, no Peanut SDK, no physical robot is
 * involved anywhere in this test. This is deliberately SOFTWARE TEST
 * VERIFIED coverage of the executor's own logic (what it reports, and
 * when, and how it validates {@code destinationId}), not a claim about
 * what a real gateway implementation does on a real robot.
 */
@ExtendWith(MockitoExtension.class)
class PeanutSdkGoToPointExecutorTest {

    @Mock
    private RobotCommandResultReporter reporter;

    @Test
    void execute_reportsExecutingBeforeCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", 5), reporter);

        verify(reporter).reportExecuting();
        assertTrue(gateway.goToPointCalled);
        assertEqualsInt(5, gateway.lastDestinationId);
    }

    @Test
    void execute_acceptsAnIntegerDestinationId() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", 5), reporter);

        assertEqualsInt(5, gateway.lastDestinationId);
    }

    @Test
    void execute_acceptsANumericStringDestinationId() {
        // Gson deserializes JSON numbers as Double by default, but a defensively-typed
        // upstream (or a hand-built test payload) might supply a String - both must work.
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", "5"), reporter);

        assertEqualsInt(5, gateway.lastDestinationId);
    }

    @Test
    void execute_missingDestinationId_reportsFailedWithoutEverCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of(), reporter);

        verify(reporter).reportFailed(contains("destinationId"));
        verify(reporter, never()).reportExecuting();
        assertFalse(gateway.goToPointCalled);
    }

    @Test
    void execute_nullParams_reportsFailedWithoutEverCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", null, reporter);

        verify(reporter).reportFailed(contains("destinationId"));
        verify(reporter, never()).reportExecuting();
        assertFalse(gateway.goToPointCalled);
    }

    @Test
    void execute_negativeDestinationId_reportsFailedWithoutEverCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", -1), reporter);

        verify(reporter).reportFailed(contains("destinationId"));
        verify(reporter, never()).reportExecuting();
        assertFalse(gateway.goToPointCalled);
    }

    @Test
    void execute_nonNumericDestinationId_reportsFailedWithoutEverCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", "not-a-number"), reporter);

        verify(reporter).reportFailed(contains("destinationId"));
        verify(reporter, never()).reportExecuting();
        assertFalse(gateway.goToPointCalled);
    }

    @Test
    void gatewayAccepts_reportsDispatched_neverReportsCompleted() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", 5), reporter);
        gateway.succeed("{\"code\":0}");

        verify(reporter).reportDispatched(contains("does NOT confirm"));
        verify(reporter, never()).reportCompleted(any());
        verify(reporter, never()).reportFailed(any());
    }

    @Test
    void gatewayErrors_reportsFailed_neverReportsDispatchedOrCompleted() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkGoToPointExecutor executor = new PeanutSdkGoToPointExecutor(gateway);

        executor.execute("GO_TO_POINT", Map.of("destinationId", 5), reporter);
        gateway.fail(42, "blocked by operating mode");

        verify(reporter).reportFailed(contains("errorCode=42"));
        verify(reporter, never()).reportDispatched(any());
        verify(reporter, never()).reportCompleted(any());
    }

    private static void assertEqualsInt(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    /**
     * A minimal fake of the SDK boundary — deliberately not a real
     * NavigationComponent/NavigationSetTargetApi call. Captures the
     * callback so the test can simulate either outcome synchronously.
     */
    private static final class FakeGateway implements GoToPointGateway {
        private boolean goToPointCalled;
        private int lastDestinationId = Integer.MIN_VALUE;
        private Callback callback;

        @Override
        public void goToPoint(int destinationId, Callback callback) {
            this.goToPointCalled = true;
            this.lastDestinationId = destinationId;
            this.callback = callback;
        }

        void succeed(String rawResponse) {
            callback.onAccepted(rawResponse);
        }

        void fail(int errorCode, String errorMessage) {
            callback.onError(errorCode, errorMessage);
        }
    }
}
