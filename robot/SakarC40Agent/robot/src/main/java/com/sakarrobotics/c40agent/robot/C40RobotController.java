package com.sakarrobotics.c40agent.robot;

import android.content.Context;

import com.sakarrobotics.c40agent.charging.ChargingBridge;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.navigation.NavigationBridge;
import com.sakarrobotics.c40agent.sdk.DestinationsCallback;
import com.sakarrobotics.c40agent.sdk.PeanutSdkBridge;
import com.sakarrobotics.c40agent.sdk.SdkCallback;
import com.sakarrobotics.c40agent.sdk.SdkConnectionConfig;
import com.sakarrobotics.c40agent.sdk.SdkInitCallback;
import com.sakarrobotics.c40agent.sdk.SdkRuntimeEventListener;
import com.sakarrobotics.c40agent.telemetry.ConnectionStatus;
import com.sakarrobotics.c40agent.telemetry.HealthEvent;
import com.sakarrobotics.c40agent.telemetry.RuntimeSnapshot;

/**
 * Main abstraction between the application and the Peanut SDK. This is
 * the class the rest of the app (ui/) is expected to hold and call -
 * nothing outside of :sdk, :navigation and :charging talks to the SDK
 * bridges directly.
 *
 * Safety: every action that could move the robot, touch the motors, or
 * start/stop charging is gated by {@link #getOperatingMode()}. The
 * default and only mode currently reachable from the UI is
 * {@link OperatingMode#DIAGNOSTIC_ONLY} - see that enum's docs.
 */
public final class C40RobotController {

    public static final int ERROR_BLOCKED_BY_OPERATING_MODE = -1001;

    /**
     * Client-side readiness check - the SDK was never called. Same
     * local, non-SDK error-code family as {@link #ERROR_BLOCKED_BY_OPERATING_MODE}
     * and {@code PeanutSdkBridge.ERROR_INVALID_MAP_DATA}/{@code ERROR_MALFORMED_DESTINATIONS}.
     * Returned instead of letting a read-only query race {@link #connect}
     * and NPE inside the vendor SDK before {@code PeanutSDK.init()} has
     * completed.
     */
    public static final int ERROR_SDK_NOT_READY = -1004;

    private final Context appContext;
    private final SdkConnectionConfig config;
    private final PeanutSdkBridge sdkBridge = PeanutSdkBridge.getInstance();
    private final NavigationBridge navigationBridge = new NavigationBridge();
    private final ChargingBridge chargingBridge = new ChargingBridge();

    private volatile OperatingMode operatingMode = OperatingMode.DIAGNOSTIC_ONLY;
    private volatile ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    public C40RobotController(Context context, SdkConnectionConfig config) {
        this.appContext = context.getApplicationContext();
        this.config = config;
    }

    public OperatingMode getOperatingMode() {
        return operatingMode;
    }

    /**
     * Deliberately not wired to any UI control in this build. Exists so a
     * future, explicitly supervised on-robot test session can opt in
     * without changing this class.
     */
    public void setOperatingMode(OperatingMode operatingMode) {
        this.operatingMode = operatingMode;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public SdkConnectionConfig getConnectionConfig() {
        return config;
    }

    // ---------------------------------------------------------------
    // Connection lifecycle - safe in any operating mode: this only
    // establishes the SDK link, it never moves the robot.
    // ---------------------------------------------------------------

    public void connect(ConnectionCallback callback) {
        connectionStatus = ConnectionStatus.CONNECTING;
        sdkBridge.init(appContext, config, new SdkInitCallback() {
            @Override
            public void onInitSuccess() {
                connectionStatus = ConnectionStatus.CONNECTED;
                sdkBridge.startRuntime(new SdkRuntimeEventListener() {
                    @Override
                    public void onEvent(int event, String content) {
                        // Surfaced to the UI only via SdkCallLogger's scrolling log.
                    }

                    @Override
                    public void onHealth(String content) {
                    }

                    @Override
                    public void onHeartbeat(String content) {
                    }
                });
                callback.onConnected();
            }

            @Override
            public void onInitError(int errorCode) {
                connectionStatus = ConnectionStatus.INIT_FAILED;
                callback.onConnectionFailed(errorCode);
            }
        });
    }

    public void disconnect() {
        sdkBridge.release();
        connectionStatus = ConnectionStatus.DISCONNECTED;
    }

    // ---------------------------------------------------------------
    // Read-only diagnostics - allowed in every operating mode.
    // ---------------------------------------------------------------

    public ConnectionStatus getStatus() {
        return connectionStatus;
    }

    public RuntimeSnapshot getRuntimeInfo() {
        return sdkBridge.getRuntimeSnapshot();
    }

    public void getBattery(SdkCallback callback) {
        if (!checkSdkReady("getBattery", callback)) {
            return;
        }
        sdkBridge.queryBatteryStatus(callback);
    }

    /**
     * Shared by every read that would otherwise call straight into a Sensor*Api/Component that
     * never responds (no error, no success - just silence) when the SDK was never initialized.
     * getBattery already had this guard; getLidar/getDepth/getSonar/getImu previously did not,
     * which left their callers (e.g. the Debug screen's IMU card) stuck on "Reading..." forever
     * on an unconnected robot instead of surfacing the same controlled error every gated
     * actuation method already returns.
     */
    private boolean checkSdkReady(String methodName, SdkCallback callback) {
        if (sdkBridge.isSdkInitialized()) {
            return true;
        }
        String message = "Blocked: SDK not initialized yet (call connect() first)";
        SdkCallLogger.getInstance().logError(
                "C40RobotController." + methodName, "n/a", ERROR_SDK_NOT_READY, message);
        callback.onError(ERROR_SDK_NOT_READY, message);
        return false;
    }

    /** UNCONFIRMED on the physical C40 - see COMPATIBILITY_REPORT.md. */
    public void getPosition(SdkCallback callback) {
        sdkBridge.queryRobotPosition(callback);
    }

    public void getMotorStatus(SdkCallback callback) {
        sdkBridge.queryMotorStatus(callback);
    }

    public void getMotorHealth(SdkCallback callback) {
        sdkBridge.queryMotorHealth(callback);
    }

    /** Manual Drive support - verified read-only MotorComponent.getEncoder(). */
    public void getMotorEncoder(SdkCallback callback) {
        sdkBridge.queryMotorEncoder(callback);
    }

    /** Manual Drive support - verified read-only MotorComponent.getSpeed(). */
    public void getMotorSpeed(SdkCallback callback) {
        sdkBridge.queryMotorSpeed(callback);
    }

    /** Manual Drive support - verified read-only MotorComponent.getState(). */
    public void getMotorState(SdkCallback callback) {
        sdkBridge.queryMotorState(callback);
    }

    public HealthEvent getHealth() {
        return sdkBridge.getLastHealthEvent();
    }

    public HealthEvent getHeartbeat() {
        return sdkBridge.getLastHeartbeatEvent();
    }

    /**
     * Roadmap addition - raw LiDAR read ({@code com.keenon.sdk.api.SensorLidarApi},
     * confirmed present in the officially-distributed AAR, previously unused
     * by this project). Read-only, so no operating-mode guard.
     */
    public void getLidar(SdkCallback callback) {
        if (!checkSdkReady("getLidar", callback)) {
            return;
        }
        sdkBridge.queryLidar(callback);
    }

    public void getDepth(SdkCallback callback) {
        if (!checkSdkReady("getDepth", callback)) {
            return;
        }
        sdkBridge.queryDepth(callback);
    }

    public void getSonar(SdkCallback callback) {
        if (!checkSdkReady("getSonar", callback)) {
            return;
        }
        sdkBridge.querySonar(callback);
    }

    public void getImu(SdkCallback callback) {
        if (!checkSdkReady("getImu", callback)) {
            return;
        }
        sdkBridge.queryImu(callback);
    }

    /**
     * Roadmap addition - {@code MapComponent.getMapInfo}/{@code downloadOpt},
     * confirmed present in the officially-distributed AAR, previously
     * unused by this project. Both only read map data off the robot - see
     * {@link #uploadMap(byte[], SdkCallback)} for the write side, which is
     * gated.
     */
    public void getMapInfo(SdkCallback callback) {
        sdkBridge.getMapInfo(callback);
    }

    public void downloadMap(SdkCallback callback) {
        sdkBridge.downloadMap(callback);
    }

    /**
     * Roadmap Phase 8 addition (see {@code
     * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md}) - {@code
     * NavigationComponent.getAllDestPose}, confirmed present in the
     * officially-distributed AAR, previously unused by this project.
     * Read-only, so no operating-mode guard: it only fetches
     * pre-registered destinations off the robot's currently loaded map,
     * it never sends a movement command. Feeds {@link #goToPoint(int, SdkCallback)}
     * a real {@code destinationId} instead of requiring one to be
     * invented.
     */
    public void getAllDestinations(DestinationsCallback callback) {
        navigationBridge.getAllDestinations(callback);
    }

    // ---------------------------------------------------------------
    // Motion / charging actions - blocked unless operatingMode is
    // explicitly HARDWARE_TEST. Nothing in this build ever sets that mode.
    // ---------------------------------------------------------------

    public void goToPoint(int targetId, SdkCallback callback) {
        if (!guard("goToPoint", "targetId=" + targetId, callback)) {
            return;
        }
        navigationBridge.goToPoint(targetId, callback);
    }

    public void pauseNavigation(SdkCallback callback) {
        if (!guard("pauseNavigation", "n/a", callback)) {
            return;
        }
        navigationBridge.pause(callback);
    }

    public void resumeNavigation(SdkCallback callback) {
        if (!guard("resumeNavigation", "n/a", callback)) {
            return;
        }
        navigationBridge.resume(callback);
    }

    public void stopNavigation(SdkCallback callback) {
        if (!guard("stopNavigation", "n/a", callback)) {
            return;
        }
        navigationBridge.stop(callback);
    }

    public void startCharging(SdkCallback callback) {
        if (!guard("startCharging", "n/a", callback)) {
            return;
        }
        chargingBridge.startCharging(callback);
    }

    public void stopCharging(SdkCallback callback) {
        if (!guard("stopCharging", "n/a", callback)) {
            return;
        }
        chargingBridge.stopCharging(callback);
    }

    /**
     * Roadmap Phase 7 "RETURN_TO_DOCK" - commands the robot to navigate
     * back to a charging dock (Peanut SDK {@code BatteryComponent.autoCharge()},
     * see {@code ChargingBridge.returnToDock}). Gated by {@code
     * OperatingMode.HARDWARE_TEST} exactly like every other actuation
     * method in this class - nothing about this method is exempt from
     * that guard.
     */
    public void returnToDock(SdkCallback callback) {
        if (!guard("returnToDock", "n/a", callback)) {
            return;
        }
        chargingBridge.returnToDock(callback);
    }

    /**
     * Roadmap addition - {@code MapComponent.uploadOpt}, pushes new map
     * data to the robot. Gated exactly like every other write/actuation
     * method in this class: it changes robot-side state even though it
     * does not cause physical motion, so it is not exempt from the guard.
     */
    public void uploadMap(byte[] mapData, SdkCallback callback) {
        if (!guard("uploadMap", "bytes=" + (mapData != null ? mapData.length : 0), callback)) {
            return;
        }
        sdkBridge.uploadMap(mapData, callback);
    }

    /**
     * Manual Drive - discrete motion primitives verified on MotorComponent
     * (forward/backward/turnLeft/turnRight take only a callback, no speed
     * parameter). Gated by {@link #guard} exactly like every other
     * actuation method in this class, so these remain blocked in every
     * OperatingMode except HARDWARE_TEST, which nothing in this build ever
     * sets - the emulator (and any build shipped as-is) can never send a
     * real motor command through these methods.
     */
    public void motorForward(String source, SdkCallback callback) {
        if (!readyForMotion("motorForward", callback)) {
            return;
        }
        String request = "direction=FORWARD,source=" + source + ",hardware=REAL";
        if (!guard("motorForward", request, callback)) {
            return;
        }
        sdkBridge.motorForward(request, callback);
    }

    public void motorBackward(String source, SdkCallback callback) {
        if (!readyForMotion("motorBackward", callback)) {
            return;
        }
        String request = "direction=REVERSE,source=" + source + ",hardware=REAL";
        if (!guard("motorBackward", request, callback)) {
            return;
        }
        sdkBridge.motorBackward(request, callback);
    }

    public void motorTurnLeft(String source, SdkCallback callback) {
        if (!readyForMotion("motorTurnLeft", callback)) {
            return;
        }
        String request = "direction=LEFT,source=" + source + ",hardware=REAL";
        if (!guard("motorTurnLeft", request, callback)) {
            return;
        }
        sdkBridge.motorTurnLeft(request, callback);
    }

    public void motorTurnRight(String source, SdkCallback callback) {
        if (!readyForMotion("motorTurnRight", callback)) {
            return;
        }
        String request = "direction=RIGHT,source=" + source + ",hardware=REAL";
        if (!guard("motorTurnRight", request, callback)) {
            return;
        }
        sdkBridge.motorTurnRight(request, callback);
    }

    /**
     * Manual Drive - STOP. Deliberately bypasses {@link #guard}, unlike
     * every other method in this section: the OperatingMode gate exists to
     * prevent unintended/accidental motion, not to prevent a robot that is
     * already moving from being stopped. If motion were ever started under
     * HARDWARE_TEST and the mode changed mid-motion for any reason, STOP
     * must still be able to reach the SDK. STOP is not itself a motion
     * command, so the SDK-readiness check still applies (there is nothing
     * to stop if the SDK was never initialized), but the operating-mode
     * guard does not.
     *
     * See PeanutSdkBridge.motorStop for the [INFERRED, NOT VERIFIED]
     * caveat on whether moveControl(reset=1) actually halts the motors.
     */
    public void motorStop(String source, SdkCallback callback) {
        if (!readyForMotion("motorStop", callback)) {
            return;
        }
        String request = "direction=STOP,source=" + source + ",hardware=REAL";
        sdkBridge.motorStop(request, callback);
    }

    /**
     * Safety rule: if the SDK was never initialized, return a controlled
     * error instead of letting a motion call race {@link #connect} or NPE
     * inside the vendor SDK. Applied to every Manual Drive entry point,
     * gated or not.
     */
    private boolean readyForMotion(String methodName, SdkCallback callback) {
        if (sdkBridge.isSdkInitialized()) {
            return true;
        }
        String message = "Blocked: SDK not initialized yet (call connect() first)";
        SdkCallLogger.getInstance().logError(
                "C40RobotController." + methodName, "n/a", ERROR_SDK_NOT_READY, message);
        callback.onError(ERROR_SDK_NOT_READY, message);
        return false;
    }

    /** @return true if the call is allowed to proceed. */
    private boolean guard(String methodName, String request, SdkCallback callback) {
        if (operatingMode == OperatingMode.HARDWARE_TEST) {
            return true;
        }
        String message = "Blocked: operating mode is " + operatingMode + ", not HARDWARE_TEST";
        SdkCallLogger.getInstance().logError(
                "C40RobotController." + methodName, request, ERROR_BLOCKED_BY_OPERATING_MODE, message);
        callback.onError(ERROR_BLOCKED_BY_OPERATING_MODE, message);
        return false;
    }
}
