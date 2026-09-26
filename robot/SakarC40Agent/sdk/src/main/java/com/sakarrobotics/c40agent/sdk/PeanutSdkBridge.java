package com.sakarrobotics.c40agent.sdk;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.keenon.common.constant.PeanutConstants;
import com.keenon.common.external.PeanutConfig;
import com.keenon.sdk.api.DevicesMoveControlApi;
import com.keenon.sdk.api.NavigationDestPoseApi;
import com.keenon.sdk.api.SensorDepthApi;
import com.keenon.sdk.api.SensorImuApi;
import com.keenon.sdk.api.SensorLidarApi;
import com.keenon.sdk.api.SensorSonarApi;
import com.keenon.sdk.component.runtime.PeanutRuntime;
import com.keenon.sdk.component.runtime.RuntimeInfo;
import com.keenon.sdk.constant.TopicName;
import com.keenon.sdk.external.IDataCallback;
import com.keenon.sdk.external.IProgressCallback;
import com.keenon.sdk.external.PeanutSDK;
import com.keenon.sdk.hedera.model.ApiError;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.telemetry.Destination;
import com.sakarrobotics.c40agent.telemetry.HealthEvent;
import com.sakarrobotics.c40agent.telemetry.Orientation;
import com.sakarrobotics.c40agent.telemetry.Pose;
import com.sakarrobotics.c40agent.telemetry.Position;
import com.sakarrobotics.c40agent.telemetry.RuntimeSnapshot;

import org.eclipse.californium.core.coap.Request;

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

    /**
     * Client-side validation failure - the SDK was never called. Same
     * negative-code convention as {@code C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE}
     * ({@code -1001}); this is the next code in that local, non-SDK
     * error-code family.
     */
    public static final int ERROR_INVALID_MAP_DATA = -1002;

    /**
     * Client-side parsing failure - the SDK returned a response but it
     * could not be parsed into {@link Destination}s. Same local,
     * non-SDK error-code family as {@link #ERROR_INVALID_MAP_DATA}.
     */
    public static final int ERROR_MALFORMED_DESTINATIONS = -1003;

    /**
     * Client-side wrapper failure - the SDK was never fully called. Same
     * local, non-SDK error-code family as {@link #ERROR_INVALID_MAP_DATA}/
     * {@link #ERROR_MALFORMED_DESTINATIONS}. Used when {@code
     * PeanutSDK.getInstance().init(...)} throws synchronously instead of
     * invoking its own error callback - observed on the Android Studio
     * emulator (no physical C40 present): {@code PeanutSDK.initPermissionCheck()}
     * throws a bare {@code RuntimeException} for a missing runtime
     * permission before the SDK's async callback machinery ever engages,
     * so that failure cannot be reported any other way.
     */
    public static final int ERROR_INIT_THREW = -1005;

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
        try {
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
        } catch (RuntimeException thrownBeforeCallback) {
            // See ERROR_INIT_THREW's Javadoc: PeanutSDK.init() can throw
            // synchronously instead of ever reaching the callback above.
            // Routed into the same onInitError() path as a real async
            // failure would use, so callers cannot tell the difference.
            sdkInitialized = false;
            String message = "PeanutSDK.init threw before invoking its callback: " + thrownBeforeCallback;
            SdkCallLogger.getInstance().logError("PeanutSDK.init", request, ERROR_INIT_THREW, message);
            callback.onInitError(ERROR_INIT_THREW);
        }
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

    /**
     * PeanutRuntime.getRuntimeInfo() returns null until the runtime has actually started (i.e.
     * before a successful connect()/startRuntime() - always true on the emulator, where
     * PeanutSDK.init() never succeeds). Returns an honest all-unset snapshot instead of crashing.
     */
    public RuntimeSnapshot getRuntimeSnapshot() {
        RuntimeInfo info = PeanutRuntime.getInstance().getRuntimeInfo();
        if (info == null) {
            return new RuntimeSnapshot(0, 0, 0, null, false, false, 0, null, null, null, null, null);
        }
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

    /**
     * VERIFIED via {@code javap -p} against the actual licensed
     * peanut-sdk-release.aar: {@code MotorComponent.getEncoder(IDataCallback)}
     * takes only a callback. Read-only, no operating-mode gate needed.
     */
    public void queryMotorEncoder(SdkCallback callback) {
        PeanutSDK.getInstance().motor().getEncoder(wrap("MotorComponent.getEncoder", "n/a", callback));
    }

    /**
     * VERIFIED via {@code javap -p}: {@code MotorComponent.getSpeed(IDataCallback)}
     * takes only a callback. Read-only, no operating-mode gate needed.
     */
    public void queryMotorSpeed(SdkCallback callback) {
        PeanutSDK.getInstance().motor().getSpeed(wrap("MotorComponent.getSpeed", "n/a", callback));
    }

    /**
     * VERIFIED via {@code javap -p}: {@code MotorComponent.getState(IDataCallback)}
     * takes only a callback. Read-only, no operating-mode gate needed.
     */
    public void queryMotorState(SdkCallback callback) {
        PeanutSDK.getInstance().motor().getState(wrap("MotorComponent.getState", "n/a", callback));
    }

    /**
     * VERIFIED via {@code javap -p}: {@code MotorComponent.forward(IDataCallback)}
     * takes only a callback - no speed/duration parameter. This is a real
     * motion command; callers must gate it behind OperatingMode.HARDWARE_TEST
     * (see C40RobotController.motorForward).
     */
    public void motorForward(String request, SdkCallback callback) {
        PeanutSDK.getInstance().motor().forward(wrap("MotorComponent.forward", request, callback));
    }

    /**
     * VERIFIED via {@code javap -p}: {@code MotorComponent.backward(IDataCallback)}
     * takes only a callback. Real motion command - see motorForward's note.
     */
    public void motorBackward(String request, SdkCallback callback) {
        PeanutSDK.getInstance().motor().backward(wrap("MotorComponent.backward", request, callback));
    }

    /**
     * VERIFIED via {@code javap -p}: {@code MotorComponent.turnLeft(IDataCallback)}
     * takes only a callback. Real motion command - see motorForward's note.
     */
    public void motorTurnLeft(String request, SdkCallback callback) {
        PeanutSDK.getInstance().motor().turnLeft(wrap("MotorComponent.turnLeft", request, callback));
    }

    /**
     * VERIFIED via {@code javap -p}: {@code MotorComponent.turnRight(IDataCallback)}
     * takes only a callback. Real motion command - see motorForward's note.
     */
    public void motorTurnRight(String request, SdkCallback callback) {
        PeanutSDK.getInstance().motor().turnRight(wrap("MotorComponent.turnRight", request, callback));
    }

    /**
     * MotorComponent has no dedicated stop() method in the verified AAR
     * surface. This uses the verified moveControl(IDataCallback, ParamBean)
     * entry point with reset=1 and all velocities zeroed, which is the
     * closest available primitive to a stop command.
     *
     * [INFERRED, NOT VERIFIED against hardware or vendor documentation] -
     * whether reset=1 actually halts the robot's motors has not been
     * confirmed against a physical C40 or vendor docs; it is only known
     * that ParamBean's fields are (reset:int, linear:double, angular:double,
     * direction:double, time:double) and that zero linear/angular velocity
     * with reset=1 is the most conservative interpretation of "stop" this
     * API surface offers. Flag this for supervised hardware validation
     * before relying on it operationally.
     */
    public void motorStop(String request, SdkCallback callback) {
        DevicesMoveControlApi.ParamBean stopParam =
                new DevicesMoveControlApi.ParamBean(1, 0d, 0d, 0d, 0d);
        PeanutSDK.getInstance().motor().moveControl(
                wrap("MotorComponent.moveControl(stop)", request, callback), stopParam);
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
    // Destination discovery (Roadmap Phase 8 addition, see
    // C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md). NavigationComponent.
    // getAllDestPose(IDataCallback) is confirmed present in the officially-
    // distributed AAR via javap, internally delegates to
    // com.keenon.sdk.api.NavigationDestPoseApi, @CoapCommond(path=
    // "/navigation/dest_poses") - a plain GET, so this is a read-only
    // query exactly like the diagnostic queries above: no operating-mode
    // guard, and it never calls setTarget/pause/resume/stop or any other
    // actuation method. (getAllDestPoseV2 exists too, at
    // /navigation/dest_posesV2, but its response has no pose/map field at
    // all - grouped building->floor->{id,name,type,phoneStr} only - so it
    // cannot build a usable Destination and is deliberately not wired
    // here; see the investigation doc for the full comparison.)
    // ---------------------------------------------------------------

    /**
     * Fetches every destination pre-registered on the robot's currently
     * loaded map. An empty list is a valid, successful result (the robot
     * has none registered right now) - this bridge does not treat that as
     * an error, unlike Keenon's own Peanut Clean app ({@code
     * PeanutResourceManager.getLocalResource()}, decompiled), which
     * treats an empty raw response string as a business-level failure
     * ("point empty"). A malformed/unparsable response is reported via
     * {@link #ERROR_MALFORMED_DESTINATIONS}, never silently swallowed or
     * substituted with invented data.
     */
    public void getAllDestinations(DestinationsCallback callback) {
        PeanutSDK.getInstance().navigation().getAllDestPose(new IDataCallback() {
            @Override
            public void success(String result) {
                SdkCallLogger.getInstance().logSuccess("NavigationComponent.getAllDestPose", "n/a", result);
                try {
                    callback.onSuccess(parseDestinations(result));
                } catch (RuntimeException malformed) {
                    String message = "Malformed getAllDestPose response: " + malformed.getMessage();
                    SdkCallLogger.getInstance().logError("NavigationComponent.getAllDestPose", "n/a",
                            ERROR_MALFORMED_DESTINATIONS, message);
                    callback.onError(ERROR_MALFORMED_DESTINATIONS, message);
                }
            }

            @Override
            public void error(ApiError error) {
                int code = error != null ? error.getCode() : -1;
                String message = error != null ? error.getMsg() : "unknown error";
                SdkCallLogger.getInstance().logError("NavigationComponent.getAllDestPose", "n/a", code, message);
                callback.onError(code, message);
            }
        });
    }

    // ---------------------------------------------------------------
    // Raw sensor reads (Roadmap addition, evidenced by the C40 S
    // reverse-engineering pass - see
    // C40_S_LS_M014C00_RW_F00_V246_ROS_INTERFACE_ANALYSIS.md section 18).
    // com.keenon.sdk.api.SensorLidarApi/SensorDepthApi/SensorSonarApi/
    // SensorImuApi are public, standalone classes in this AAR - confirmed
    // present via javap, NOT part of any Component facade, and NOT the
    // same package as the internal-only com.keenon.sdk.coapapi.api.sensor.*
    // classes found bundled inside Keenon's own apps. Each is a one-shot
    // CoAP GET (send()); observe() (continuous subscription) exists on
    // each class too but is intentionally not wired here yet - this pass
    // only adds the same one-shot-query shape already used above for
    // battery/motor/navigation status.
    // ---------------------------------------------------------------

    public void queryLidar(SdkCallback callback) {
        new SensorLidarApi().send(wrap("SensorLidarApi.send", "n/a", callback));
    }

    public void queryDepth(SdkCallback callback) {
        new SensorDepthApi().send(wrap("SensorDepthApi.send", "n/a", callback));
    }

    public void querySonar(SdkCallback callback) {
        new SensorSonarApi().send(wrap("SensorSonarApi.send", "n/a", callback));
    }

    public void queryImu(SdkCallback callback) {
        new SensorImuApi().send(wrap("SensorImuApi.send", "n/a", callback));
    }

    // ---------------------------------------------------------------
    // MapComponent read-only calls (Roadmap addition, same evidence as
    // above). getMapInfo/downloadOpt only pull data FROM the robot - they
    // do not modify robot state, so they are safe in every OperatingMode
    // exactly like the diagnostic queries above. uploadOpt (below, in the
    // action pass-throughs section) pushes data TO the robot and is gated.
    //
    // VENDOR CLASS-NAME TRAP (verified by bytecode, not vendor docs):
    // Keenon's own internal API classes are named opposite to what they
    // do. MapComponent.downloadOpt(IDataCallback) - a read - actually
    // constructs and calls com.keenon.sdk.api.MapUploadOptApi, which has
    // no byte[]/request-body field at all (a bodyless fetch). Conversely,
    // MapComponent.uploadOpt(IProgressCallback, byte[]) - a write - calls
    // com.keenon.sdk.api.MapDownloadOptApi, which does carry a
    // "private byte[] file" field serialized into the CoAP request body.
    // The read/write split used in this bridge (downloadMap ungated,
    // uploadMap gated) is based on this verified payload evidence, not on
    // the (reversed) vendor class names - do not "fix" this bridge by
    // matching method names to Keenon's internal class names.
    // ---------------------------------------------------------------

    public void getMapInfo(SdkCallback callback) {
        PeanutSDK.getInstance().map().getMapInfo(wrap("MapComponent.getMapInfo", "n/a", callback));
    }

    public void downloadMap(SdkCallback callback) {
        PeanutSDK.getInstance().map().downloadOpt(wrap("MapComponent.downloadOpt", "n/a", callback));
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

    /**
     * Roadmap addition - {@code MapComponent.uploadOpt(IProgressCallback, byte[])},
     * confirmed present in the AAR via javap (see the vendor class-name
     * trap note above this method's siblings). Pushes new map data to the
     * robot, so it lives in this gated section like every other write:
     * this class does not decide whether it is safe to call, {@code
     * C40RobotController} does.
     *
     * Validates {@code mapData} before touching the SDK: {@code null} or
     * empty is rejected here rather than silently becoming an empty
     * vendor request body (Keenon's {@code MapDownloadOptApi.CoapParams()}
     * degrades a null {@code file} field to an empty string, which this
     * bridge does not want to rely on implicitly).
     */
    public void uploadMap(byte[] mapData, SdkCallback callback) {
        if (mapData == null || mapData.length == 0) {
            String message = "uploadMap rejected: mapData is " + (mapData == null ? "null" : "empty");
            SdkCallLogger.getInstance().logError("MapComponent.uploadOpt", "n/a", ERROR_INVALID_MAP_DATA, message);
            callback.onError(ERROR_INVALID_MAP_DATA, message);
            return;
        }
        PeanutSDK.getInstance().map().uploadOpt(
                wrapProgress("MapComponent.uploadOpt", "bytes=" + mapData.length, callback),
                mapData);
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

    /**
     * Same success/error contract as {@link #wrap}, for the SDK's upload
     * methods which require the wider {@code IProgressCallback} interface.
     * {@code readyToSend}/{@code progress} are logged only - nothing above
     * :sdk needs the raw Californium request object or a progress bar yet.
     */
    private static IProgressCallback wrapProgress(String api, String request, SdkCallback callback) {
        return new IProgressCallback() {
            @Override
            public void readyToSend(Request coapRequest) {
                // No-op: intentionally not surfaced above :sdk - see class Javadoc.
            }

            @Override
            public void progress(int percent) {
                SdkCallLogger.getInstance().logSuccess(api, request, "progress=" + percent + "%");
            }

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

    /**
     * Parses a raw {@code NavigationComponent.getAllDestPose} success
     * payload into vendor-neutral {@link Destination}s. Package-private
     * (not private) specifically so it is directly unit-testable from
     * this module's own test source set without ever touching {@code
     * PeanutSDK.getInstance()} - see {@code
     * PeanutSdkBridgeDestinationParsingTest}.
     *
     * <p>A {@code null}/empty raw string is treated as "no destinations"
     * (returns an empty list), not an error - this project's own,
     * deliberate interpretation, distinct from Keenon's Peanut Clean app
     * treating that same shape as a failure (see {@link
     * #getAllDestinations}'s Javadoc). Any other unparsable input
     * propagates the underlying {@link com.google.gson.JsonSyntaxException}
     * (or a {@link NullPointerException} for a structurally-wrong-but-
     * valid-JSON payload) to the caller, which {@link #getAllDestinations}
     * catches and reports as {@link #ERROR_MALFORMED_DESTINATIONS}.
     */
    static List<Destination> parseDestinations(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) {
            return Collections.emptyList();
        }
        NavigationDestPoseApi.Bean bean = new Gson().fromJson(rawJson, NavigationDestPoseApi.Bean.class);
        if (bean == null || bean.getData() == null) {
            return Collections.emptyList();
        }
        List<Destination> destinations = new ArrayList<>();
        for (NavigationDestPoseApi.Bean.DataBean data : bean.getData()) {
            destinations.add(toDestination(data));
        }
        return destinations;
    }

    /**
     * Field-for-field mapping from the verified vendor bean
     * (NavigationDestPoseApi.Bean.DataBean) to the vendor-neutral {@link
     * Destination} - see that class's Javadoc for which vendor field each
     * one mirrors (in particular {@code bind_map_md5} -> {@code mapId}).
     */
    private static Destination toDestination(NavigationDestPoseApi.Bean.DataBean data) {
        Pose pose = null;
        NavigationDestPoseApi.Bean.DataBean.PoseBean vendorPose = data.getPose();
        if (vendorPose != null) {
            Position position = vendorPose.getPosition() != null
                    ? new Position(vendorPose.getPosition().getX(), vendorPose.getPosition().getY(),
                            vendorPose.getPosition().getZ())
                    : null;
            Orientation orientation = vendorPose.getOrientation() != null
                    ? new Orientation(vendorPose.getOrientation().getW(), vendorPose.getOrientation().getX(),
                            vendorPose.getOrientation().getY(), vendorPose.getOrientation().getZ())
                    : null;
            pose = new Pose(position, orientation);
        }
        return new Destination(data.getId(), data.getName(), pose, data.getBind_map_md5(), data.getFloor(),
                data.getType());
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
