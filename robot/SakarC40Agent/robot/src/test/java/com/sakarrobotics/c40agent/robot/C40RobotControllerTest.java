package com.sakarrobotics.c40agent.robot;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.sakarrobotics.c40agent.sdk.DestinationsCallback;
import com.sakarrobotics.c40agent.sdk.PeanutSdkBridge;
import com.sakarrobotics.c40agent.sdk.SdkCallback;
import com.sakarrobotics.c40agent.sdk.SdkConnectionConfig;
import com.sakarrobotics.c40agent.sdk.SdkLinkType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Software-test-only coverage of {@link C40RobotController#uploadMap} and
 * {@link C40RobotController#goToPoint}'s safety gating - the pieces of the
 * MapComponent/Sensor*Api (Roadmap addition) and GO_TO_POINT (Roadmap
 * Phase 8, see {@code C40_S_GO_TO_POINT_SDK_INVESTIGATION.md}) work that
 * are actually testable without a physical robot. No Android runtime, no
 * Peanut SDK connection, and no physical robot is involved anywhere in
 * this test class.
 *
 * {@code C40RobotController} has no dependency-injection seam for {@code
 * PeanutSdkBridge} (it is a hard singleton, unlike {@code
 * PeanutSdkReturnToDockExecutor}'s injectable {@code ReturnToDockGateway}
 * in :api - see that module's equivalent test), so the HARDWARE_TEST /
 * valid-data cases below deliberately let the call reach the real {@code
 * com.keenon.sdk.external.PeanutSDK} singleton and assert that it throws:
 * that singleton is never initialized in this pure-JVM unit test (no
 * {@code PeanutSDK.init()} was called, and this project sets no {@code
 * testOptions.unitTests.returnDefaultValues}, so the Android Gradle
 * Plugin's default unit-test stubs throw on first use - here, {@code
 * ComponentManager.getComponent()}'s call to {@code
 * android.text.TextUtils.isEmpty(...)}). The exception itself is the
 * evidence that {@link OperatingMode#DIAGNOSTIC_ONLY}'s guard did NOT
 * block the call and the vendor SDK layer really was reached - these
 * tests do not, and cannot, assert anything about what a real upload or
 * navigation command does on a real robot. This is SOFTWARE TEST VERIFIED
 * coverage of the gating logic only.
 */
@ExtendWith(MockitoExtension.class)
class C40RobotControllerTest {

    private static final byte[] VALID_MAP_BYTES = {1, 2, 3};

    @Mock
    private Context context;

    @Mock
    private SdkCallback callback;

    @Mock
    private DestinationsCallback destinationsCallback;

    private C40RobotController newController() {
        SdkConnectionConfig config = new SdkConnectionConfig("", "", SdkLinkType.DEFAULT, "", 0);
        return new C40RobotController(context, config);
    }

    @Test
    void uploadMap_nullData_blockedBeforeSdkInvocation_underHardwareTest() {
        C40RobotController controller = newController();
        controller.setOperatingMode(OperatingMode.HARDWARE_TEST);

        // Must not throw: PeanutSdkBridge's null/empty validation rejects
        // this before ever touching PeanutSDK.getInstance().
        controller.uploadMap(null, callback);

        verify(callback).onError(eq(PeanutSdkBridge.ERROR_INVALID_MAP_DATA), any());
        verify(callback, never()).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    @Test
    void uploadMap_emptyData_blockedBeforeSdkInvocation_underHardwareTest() {
        C40RobotController controller = newController();
        controller.setOperatingMode(OperatingMode.HARDWARE_TEST);

        controller.uploadMap(new byte[0], callback);

        verify(callback).onError(eq(PeanutSdkBridge.ERROR_INVALID_MAP_DATA), any());
        verify(callback, never()).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    @Test
    void uploadMap_validData_underHardwareTest_reachesTheSdkLayer() {
        C40RobotController controller = newController();
        controller.setOperatingMode(OperatingMode.HARDWARE_TEST);

        // See class Javadoc: this is expected to throw here because the
        // real vendor SDK singleton is never initialized in this test -
        // that is the proof the guard let the call through.
        assertThrows(RuntimeException.class, () -> controller.uploadMap(VALID_MAP_BYTES, callback));

        verify(callback, never()).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    @Test
    void uploadMap_validData_underDiagnosticOnly_isBlockedAndSdkIsNeverReached() {
        C40RobotController controller = newController();
        // OperatingMode.DIAGNOSTIC_ONLY is the default - not set explicitly,
        // exactly like every real instance of this class today.

        // Must not throw: the guard returns before PeanutSdkBridge (and
        // therefore the real vendor SDK) is ever called.
        controller.uploadMap(VALID_MAP_BYTES, callback);

        verify(callback).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    // ---------------------------------------------------------------
    // goToPoint (Roadmap Phase 8, GO_TO_POINT). destinationId=7 below is an
    // arbitrary int used only to exercise the gating code path - it is NOT
    // a claim that "7" is a real, confirmed destination on any physical
    // C40's map. See C40_S_GO_TO_POINT_SDK_INVESTIGATION.md.
    // ---------------------------------------------------------------

    @Test
    void goToPoint_underHardwareTest_reachesTheSdkLayer() {
        C40RobotController controller = newController();
        controller.setOperatingMode(OperatingMode.HARDWARE_TEST);

        // See class Javadoc: this is expected to throw here because the
        // real vendor SDK singleton is never initialized in this test -
        // that is the proof the guard let the call through.
        assertThrows(RuntimeException.class, () -> controller.goToPoint(7, callback));

        verify(callback, never()).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    @Test
    void goToPoint_underDiagnosticOnly_isBlockedAndSdkIsNeverReached() {
        C40RobotController controller = newController();
        // OperatingMode.DIAGNOSTIC_ONLY is the default - not set explicitly,
        // exactly like every real instance of this class today.

        // Must not throw: the guard returns before PeanutSdkBridge (and
        // therefore the real vendor SDK) is ever called.
        controller.goToPoint(7, callback);

        verify(callback).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    // ---------------------------------------------------------------
    // getAllDestinations (Roadmap Phase 8, destination discovery). This
    // is a read-only query - it must be reachable in EVERY OperatingMode,
    // unlike goToPoint/uploadMap. It never calls guard() at all: code
    // inspection confirms NavigationBridge.getAllDestinations only calls
    // PeanutSdkBridge.getAllDestinations, never setTarget/pause/resume/stop
    // or any other actuation method - this test's job is to confirm the
    // OperatingMode gate specifically does not block it, not to re-derive
    // that code-inspection fact by other means.
    // ---------------------------------------------------------------

    @Test
    void getAllDestinations_underDiagnosticOnly_isNotBlockedByOperatingMode() {
        C40RobotController controller = newController();
        // OperatingMode.DIAGNOSTIC_ONLY is the default - not set explicitly.

        // See class Javadoc: this is expected to throw here because the
        // real vendor SDK singleton is never initialized in this test -
        // that is the proof this call was never routed through guard().
        assertThrows(RuntimeException.class, () -> controller.getAllDestinations(destinationsCallback));

        verify(callback, never()).onError(eq(C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE), any());
    }

    @Test
    void getAllDestinations_underHardwareTest_alsoReachesTheSdkLayer() {
        C40RobotController controller = newController();
        controller.setOperatingMode(OperatingMode.HARDWARE_TEST);

        // Same behavior as DIAGNOSTIC_ONLY above - OperatingMode has no effect
        // on this read-only call either way.
        assertThrows(RuntimeException.class, () -> controller.getAllDestinations(destinationsCallback));
    }
}
