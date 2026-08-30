package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Software-test-only coverage of {@link PeanutSdkReturnToDockExecutor}
 * (Roadmap Phase 7 "RETURN_TO_DOCK"). {@link ReturnToDockGateway} is faked
 * here — no Android runtime, no Peanut SDK, no physical robot is involved
 * anywhere in this test. This is deliberately SOFTWARE TEST VERIFIED
 * coverage of the executor's own logic (what it reports, and when), not a
 * claim about what a real gateway implementation does on a real robot.
 */
@ExtendWith(MockitoExtension.class)
class PeanutSdkReturnToDockExecutorTest {

    @Mock
    private RobotCommandResultReporter reporter;

    @Test
    void execute_reportsExecutingBeforeCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkReturnToDockExecutor executor = new PeanutSdkReturnToDockExecutor(gateway);

        executor.execute("RETURN_TO_DOCK", Map.of(), reporter);

        verify(reporter).reportExecuting();
        assertTrue(gateway.autoChargeCalled);
    }

    @Test
    void gatewayAccepts_reportsDispatched_neverReportsCompleted() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkReturnToDockExecutor executor = new PeanutSdkReturnToDockExecutor(gateway);

        executor.execute("RETURN_TO_DOCK", Map.of(), reporter);
        gateway.succeed("{\"code\":0}");

        verify(reporter).reportDispatched(org.mockito.ArgumentMatchers.contains("does NOT confirm"));
        verify(reporter, never()).reportCompleted(any());
        verify(reporter, never()).reportFailed(any());
    }

    @Test
    void gatewayErrors_reportsFailed_neverReportsDispatchedOrCompleted() {
        FakeGateway gateway = new FakeGateway();
        PeanutSdkReturnToDockExecutor executor = new PeanutSdkReturnToDockExecutor(gateway);

        executor.execute("RETURN_TO_DOCK", Map.of(), reporter);
        gateway.fail(42, "blocked by operating mode");

        verify(reporter).reportFailed(org.mockito.ArgumentMatchers.contains("errorCode=42"));
        verify(reporter, never()).reportDispatched(any());
        verify(reporter, never()).reportCompleted(any());
    }

    /**
     * A minimal fake of the SDK boundary — deliberately not a real
     * BatteryComponent/ChargeAutoApi call. Captures the callback so the
     * test can simulate either outcome synchronously.
     */
    private static final class FakeGateway implements ReturnToDockGateway {
        private boolean autoChargeCalled;
        private Callback callback;

        @Override
        public void autoCharge(Callback callback) {
            this.autoChargeCalled = true;
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
