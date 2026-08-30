package com.sakarrobotics.c40agent.sdk;

import android.content.Context;
import android.util.Log;

import com.keenon.common.constant.PeanutConstants;
import com.keenon.common.external.PeanutConfig;
import com.keenon.sdk.component.runtime.PeanutRuntime;
import com.keenon.sdk.component.runtime.RuntimeInfo;
import com.keenon.sdk.constant.TopicName;
import com.keenon.sdk.external.IDataCallback;
import com.keenon.sdk.external.PeanutSDK;
import com.keenon.sdk.hedera.model.ApiError;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.telemetry.HealthEvent;
import com.sakarrobotics.c40agent.telemetry.RuntimeSnapshot;

/**
 * The ONLY class in this project allowed to import com.keenon.* types.
 * Everything above this layer (robot/, navigation/, charging/, ui/) talks
 * to the Keenon C40 exclusively through this bridge and the decoupled
 * types in this package (SdkCallback, SdkConnectionConfig, ...) and in
 * :telemetry.
 *
 * Every method here is a thin, verified pass-through to the compiled
 * peanut-sdk-release.aar (v1.5.0-bate1) API surface confirmed by
 * decompiling it with javap - see COMPATIBILITY_REPORT.md for the full
 * list and evidence. No method here invokes a com.keenon.* API that was
 * not actually found in the compiled AAR.
 */
public final class PeanutSdkBridge {

    /** From the SDK's own AndroidManifest (android:versionName), verified by unzipping the AAR. */
    public static final String SDK_VERSION = "1.5.0-bate1";

    private static final String TAG = "PeanutSdkBridge";
    private static final PeanutSdkBridge INSTANCE = new PeanutSdkBridge();

    private volatile boolean sdkInitialized;
    private volatile boolean runtimeStarted;
    private volatile HealthEvent lastHealthEvent;
    private volatile HealthEvent lastHeartbeatEvent;

    private PeanutSdkBridge() {
    }

    public static PeanutSdkBridge getInstance() {
        return INSTANCE;
    }

    public boolean isSdkInitialized() {
        return sdkInitialized;
    }

    public boolean isRuntimeStarted() {
        return runtimeStarted;
    }

    // ---------------------------------------------------------------
    // Initialization (PeanutSDK.init) and runtime start (PeanutRuntime.start)
    // are two separate steps in the compiled SDK - confirmed in
    // KeenonApiDemoMain from the vendor's own SampleApp, and in
    // PeanutRuntime$Listener / PeanutSDK$ErrorListener via javap.
    // ---------------------------------------------------------------

    public void init(Context context, SdkConnectionConfig config, SdkInitCallback callback) {
        PeanutConstants.LinkType linkType = toSdkLinkType(config.getLinkType());

        PeanutConfig.Config cfg = PeanutConfig.getConfig()
                .setLinkType(linkType)
                .enableLog(true)
                .setLogLevel(Log.DEBUG)
                .setAppId(config.getAppId())
                .setSecret(config.getAppSecret())
                .enableUMLog(false);

        if (linkType == PeanutConstants.LinkType.COM) {
            cfg.setLinkCOM(config.getLinkHost());
        } else {
            cfg.setLinkIP(config.getLinkHost());
            if (config.getLinkPort() > 0) {
                cfg.setLinkPort(config.getLinkPort());
            }
        }

        String request = "linkType=" + config.getLinkType() + " host=" + config.getLinkHost();
        PeanutSDK.getInstance().init(context.getApplicationContext(), errorCode -> {
            if (errorCode == PeanutSDK.SDK_INIT_SUCCESS) {
                sdkInitialized = true;
                SdkCallLogger.getInstance().logSuccess("PeanutSDK.init", request, "errorCode=" + errorCode);
                callback.onInitSuccess();
            } else {
                sdkInitialized = false;
                SdkCallLogger.getInstance().logError("PeanutSDK.init", request, errorCode, "SDK init failed");
                callback.onInitError(errorCode);
            }
        });
    }

    public void startRuntime(SdkRuntimeEventListener listener) {
        PeanutRuntime.getInstance().start(new PeanutRuntime.Listener() {
            @Override
            public void onEvent(int event, Object obj) {
                runtimeStarted = true;
                String content = String.valueOf(obj);
                SdkCallLogger.getInstance().logSuccess("PeanutRuntime.Listener.onEvent",
                        "event=" + event, content);
                listener.onEvent(event, content);
            }

            @Override
            public void onHealth(Object content) {
                String text = String.valueOf(content);
                lastHealthEvent = new HealthEvent(HealthEvent.Kind.HEALTH, text);
                SdkCallLogger.getInstance().logSuccess("PeanutRuntime.Listener.onHealth", "n/a", text);
                listener.onHealth(text);
            }

            @Override
            public void onHeartbeat(Object content) {
                String text = String.valueOf(content);
                lastHeartbeatEvent = new HealthEvent(HealthEvent.Kind.HEARTBEAT, text);
                SdkCallLogger.getInstance().logSuccess("PeanutRuntime.Listener.onHeartbeat", "n/a", text);
                listener.onHeartbeat(text);
            }
        });
    }

    public void release() {
        PeanutSDK.getInstance().release();
        sdkInitialized = false;
        runtimeStarted = false;
        SdkCallLogger.getInstance().logSuccess("PeanutSDK.release", "n/a", "released");
    }

    // ---------------------------------------------------------------
    // Read-only diagnostic queries. Every one of these is a verified,
    // existing method on the compiled AAR's component classes.
    // ---------------------------------------------------------------

    public RuntimeSnapshot getRuntimeSnapshot() {
        RuntimeInfo info = PeanutRuntime.getInstance().getRuntimeInfo();
        return new RuntimeSnapshot(
                info.getWorkMode(),
                info.getSyncStatus(),
                info.getPower(),
                info.getTotalOdo(),
                info.isEmergencyEnable(),
                info.isEmergencyOpen(),
                info.getMotorStatus(),
                info.getRobotArmInfo(),
                info.getRobotStm32Info(),
                info.getRobotIp(),
                info.getRobotProperties(),
                info.getDestList());
    }

    public HealthEvent getLastHealthEvent() {
        return lastHealthEvent;
    }

    public HealthEvent getLastHeartbeatEvent() {
        return lastHeartbeatEvent;
    }

    public void queryBatteryStatus(SdkCallback callback) {
        PeanutSDK.getInstance().battery().getStatus(wrap("BatteryComponent.getStatus", "n/a", callback));
    }

    public void queryMotorStatus(SdkCallback callback) {
        PeanutSDK.getInstance().motor().getStatus(wrap("MotorComponent.getStatus", "n/a", callback));
    }

    public void queryMotorHealth(SdkCallback callback) {
        PeanutSDK.getInstance().motor().getHealth(wrap("MotorComponent.getHealth", "n/a", callback));
    }

    public void queryNavigationStatus(SdkCallback callback) {
        PeanutSDK.getInstance().navigation().getStatus(wrap("NavigationComponent.getStatus", "n/a", callback));
    }

    /**
     * UNCONFIRMED on the physical C40 - see COMPATIBILITY_REPORT.md. The
     * method itself is verified to exist on RuntimeComponent.
     */
    public void queryRobotPosition(SdkCallback callback) {
        PeanutSDK.getInstance().runtime().getRobotPosition(wrap("RuntimeComponent.getRobotPosition", "n/a", callback));
    }

    // ---------------------------------------------------------------
    // Action pass-throughs. These exist so :robot can gate them behind
    // OperatingMode.HARDWARE_TEST - this class does not decide when it is
    // safe to call them, it only forwards the call.
    // ---------------------------------------------------------------

    public void startManualCharge(SdkCallback callback) {
        PeanutSDK.getInstance().battery().manualCharge(wrap("BatteryComponent.manualCharge", "n/a", callback));
    }

    /**
     * Roadmap Phase 7 "RETURN_TO_DOCK" - {@code BatteryComponent.autoCharge(IDataCallback, int)},
     * confirmed present in the AAR via {@code javap} (delegates to {@code
     * com.keenon.sdk.api.ChargeAutoApi}, {@code @CoapCommond(path="/charge/auto")}).
     * Passes pile {@code 0} deliberately, not a guessed pile id: {@code
     * ChargeAutoApi.CoapParams()}'s own decompiled bytecode builds a null
     * request body (no "dst" field) whenever the pile argument is
     * {@code <= 0}, which is the closest verified "no specific pile"
     * behavior available without inventing a pile number this project has
     * not confirmed for any C40 install - see {@code
     * PeanutSdkReturnToDockExecutor}'s Javadoc and {@code
     * ROBOT_AGENT_COMMAND_LOOP_INVESTIGATION_AND_DESIGN.md}.
     */
    public void autoCharge(SdkCallback callback) {
        PeanutSDK.getInstance().battery().autoCharge(wrap("BatteryComponent.autoCharge", "pile=0", callback), 0);
    }

    public void stopCharge(SdkCallback callback) {
        PeanutSDK.getInstance().battery().stopCharge(wrap("BatteryComponent.stopCharge", "n/a", callback));
    }

    public void setNavigationTarget(int targetId, SdkCallback callback) {
        PeanutSDK.getInstance().navigation().setTarget(
                wrap("NavigationComponent.setTarget", "targetId=" + targetId, callback), targetId);
    }

    public void pauseNavigation(SdkCallback callback) {
        PeanutSDK.getInstance().navigation().pause(wrap("NavigationComponent.pause", "n/a", callback));
    }

    public void resumeNavigation(SdkCallback callback) {
        PeanutSDK.getInstance().navigation().resume(wrap("NavigationComponent.resume", "n/a", callback));
    }

    public void stopNavigation(SdkCallback callback) {
        PeanutSDK.getInstance().navigation().stop(wrap("NavigationComponent.stop", "n/a", callback));
    }

    // ---------------------------------------------------------------

    private static PeanutConstants.LinkType toSdkLinkType(SdkLinkType linkType) {
        switch (linkType) {
            case COM:
                return PeanutConstants.LinkType.COM;
            case COM_COAP:
                return PeanutConstants.LinkType.COM_COAP;
            case COAP:
                return PeanutConstants.LinkType.COAP;
            case HTTP:
                return PeanutConstants.LinkType.HTTP;
            default:
                return PeanutConstants.LinkType.DEFAULT;
        }
    }

    private static IDataCallback wrap(String api, String request, SdkCallback callback) {
        return new IDataCallback() {
            @Override
            public void success(String result) {
                SdkCallLogger.getInstance().logSuccess(api, request, result);
                callback.onSuccess(result);
            }

            @Override
            public void error(ApiError error) {
                int code = error != null ? error.getCode() : -1;
                String message = error != null ? error.getMsg() : "unknown error";
                SdkCallLogger.getInstance().logError(api, request, code, message);
                callback.onError(code, message);
            }
        };
    }

    /** Verified topic name constants (TopicName.*) exposed without leaking com.keenon.* to callers. */
    public static final class Topics {
        public static final String BATTERY_STATUS = TopicName.BATTERY_STATUS;
        public static final String BUTTON_STATUS = TopicName.BUTTON_STATUS;
        public static final String MOTOR_STATUS = TopicName.MOTOR_STATUS;
        public static final String NAVIGATION_STATUS = TopicName.NAVIGATION_STATUS;
        public static final String POSITION_STATUS = TopicName.POSITION_STATUS;
        public static final String RUNTIME_HEALTH = TopicName.RUNTIME_HEALTH;

        private Topics() {
        }
    }
}
