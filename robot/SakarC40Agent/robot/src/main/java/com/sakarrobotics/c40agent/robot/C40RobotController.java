package com.sakarrobotics.c40agent.robot;

import android.content.Context;

import com.sakarrobotics.c40agent.charging.ChargingBridge;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.navigation.NavigationBridge;
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
        sdkBridge.queryBatteryStatus(callback);
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

    public HealthEvent getHealth() {
        return sdkBridge.getLastHealthEvent();
    }

    public HealthEvent getHeartbeat() {
        return sdkBridge.getLastHeartbeatEvent();
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
