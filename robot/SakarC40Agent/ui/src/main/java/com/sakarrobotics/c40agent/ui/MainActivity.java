package com.sakarrobotics.c40agent.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sakarrobotics.c40agent.diagnostics.DeviceEnvironmentInspector;
import com.sakarrobotics.c40agent.diagnostics.SerialPortInspector;
import com.sakarrobotics.c40agent.logging.LogEntry;
import com.sakarrobotics.c40agent.logging.SdkCallLogger;
import com.sakarrobotics.c40agent.robot.C40RobotController;
import com.sakarrobotics.c40agent.robot.C40RobotControllerHolder;
import com.sakarrobotics.c40agent.robot.ConnectionCallback;
import com.sakarrobotics.c40agent.sdk.PeanutSdkBridge;
import com.sakarrobotics.c40agent.sdk.SdkCallback;
import com.sakarrobotics.c40agent.sdk.SdkConnectionConfig;
import com.sakarrobotics.c40agent.telemetry.HealthEvent;
import com.sakarrobotics.c40agent.telemetry.RuntimeSnapshot;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Diagnostic-only dashboard. Every action reachable from this screen is a
 * read-only status query, or connect()/disconnect() (which only opens the
 * SDK link - it never moves the robot, touches motors, or starts
 * charging). There is deliberately no navigation, motor, or charging
 * control on this screen - see OperatingMode.DIAGNOSTIC_ONLY.
 */
public class MainActivity extends AppCompatActivity {

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private C40RobotController controller;

    private TextView tvOperatingMode;
    private TextView tvConnectionStatus;
    private TextView tvDeviceInfo;
    private TextView tvSdkInfo;
    private TextView tvRuntimeInfo;
    private TextView tvBattery;
    private TextView tvMotor;
    private TextView tvPosition;
    private TextView tvHealth;
    private TextView tvSerialPorts;
    private TextView tvRawLog;
    private ScrollView svRawLog;

    private final StringBuilder rawLogBuilder = new StringBuilder();

    private final SdkCallLogger.Listener logListener = entry -> runOnUiThread(() -> appendLogEntry(entry));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        controller = C40RobotControllerHolder.get();

        bindViews();
        showDeviceInfo();
        showSdkAndAppInfo();
        showSerialPorts();
        updateOperatingModeAndConnectionText();
        renderRuntimeInfoAndHealth();

        findViewById(R.id.btn_connect).setOnClickListener(this::onConnectClicked);
        findViewById(R.id.btn_refresh).setOnClickListener(v -> refreshDiagnostics());
    }

    @Override
    protected void onStart() {
        super.onStart();
        SdkCallLogger.getInstance().addListener(logListener);
    }

    @Override
    protected void onStop() {
        SdkCallLogger.getInstance().removeListener(logListener);
        super.onStop();
    }

    private void bindViews() {
        tvOperatingMode = findViewById(R.id.tv_operating_mode);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvDeviceInfo = findViewById(R.id.tv_device_info);
        tvSdkInfo = findViewById(R.id.tv_sdk_info);
        tvRuntimeInfo = findViewById(R.id.tv_runtime_info);
        tvBattery = findViewById(R.id.tv_battery);
        tvMotor = findViewById(R.id.tv_motor);
        tvPosition = findViewById(R.id.tv_position);
        tvHealth = findViewById(R.id.tv_health);
        tvSerialPorts = findViewById(R.id.tv_serial_ports);
        tvRawLog = findViewById(R.id.tv_raw_log);
        svRawLog = findViewById(R.id.sv_raw_log);
    }

    private void showDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android version: ").append(DeviceEnvironmentInspector.getAndroidVersion()).append('\n');
        sb.append("Manufacturer: ").append(DeviceEnvironmentInspector.getManufacturer()).append('\n');
        sb.append("Model: ").append(DeviceEnvironmentInspector.getModel()).append('\n');
        sb.append("CPU ABI: ").append(DeviceEnvironmentInspector.getPrimaryCpuAbi()).append('\n');
        sb.append("Supported ABIs: ").append(String.join(", ", DeviceEnvironmentInspector.getSupportedAbis())).append('\n');
        sb.append("Native SDK libs (armeabi/armeabi-v7a) supported: ")
                .append(DeviceEnvironmentInspector.isSupportedByNativeSdkLibs()).append('\n');
        sb.append("Build fingerprint: ").append(DeviceEnvironmentInspector.getBuildFingerprint()).append('\n');
        sb.append("Network interfaces: ").append(DeviceEnvironmentInspector.getNetworkInterfaceSummaries());
        tvDeviceInfo.setText(sb.toString());
    }

    private void showSdkAndAppInfo() {
        String appVersion;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = info.versionName + " (" + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            appVersion = "unknown";
        }
        SdkConnectionConfig config = controller != null ? controller.getConnectionConfig() : null;
        StringBuilder sb = new StringBuilder();
        sb.append("App version: ").append(appVersion).append('\n');
        sb.append("Peanut SDK version: ").append(PeanutSdkBridge.SDK_VERSION).append('\n');
        if (config != null) {
            sb.append("Link type: ").append(config.getLinkType()).append('\n');
            sb.append("Robot host/COM: ").append(emptyToPlaceholder(config.getLinkHost())).append('\n');
            sb.append("Credentials configured: ").append(config.hasCredentials());
        } else {
            sb.append("Connection config unavailable");
        }
        tvSdkInfo.setText(sb.toString());
    }

    private void showSerialPorts() {
        Map<String, Boolean> ports = SerialPortInspector.checkSerialPorts();
        StringBuilder sb = new StringBuilder("Serial ports (existence only):\n");
        for (Map.Entry<String, Boolean> entry : ports.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        tvSerialPorts.setText(sb.toString().trim());
    }

    private void updateOperatingModeAndConnectionText() {
        if (controller == null) {
            tvOperatingMode.setText("Controller unavailable");
            return;
        }
        tvOperatingMode.setText("Operating mode: " + controller.getOperatingMode());
        tvConnectionStatus.setText("Connection status: " + controller.getStatus());
    }

    private void onConnectClicked(View v) {
        if (controller == null) {
            return;
        }
        tvConnectionStatus.setText("Connection status: CONNECTING");
        controller.connect(new ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    updateOperatingModeAndConnectionText();
                    refreshDiagnostics();
                });
            }

            @Override
            public void onConnectionFailed(int errorCode) {
                runOnUiThread(() -> tvConnectionStatus.setText(
                        "Connection status: INIT_FAILED (errorCode=" + errorCode + ")"));
            }
        });
    }

    private void refreshDiagnostics() {
        if (controller == null) {
            return;
        }
        renderRuntimeInfoAndHealth();

        controller.getBattery(uiCallback(response -> tvBattery.setText("Battery: " + response),
                (code, msg) -> tvBattery.setText("Battery: error " + code + " " + msg)));

        controller.getMotorStatus(uiCallback(
                status -> {
                    controller.getMotorHealth(uiCallback(
                            health -> tvMotor.setText("Motor status: " + status + "\nMotor health: " + health),
                            (code, msg) -> tvMotor.setText("Motor status: " + status
                                    + "\nMotor health: error " + code + " " + msg)));
                },
                (code, msg) -> tvMotor.setText("Motor status: error " + code + " " + msg)));

        controller.getPosition(uiCallback(
                response -> tvPosition.setText("Position [UNCONFIRMED API on C40]: " + response),
                (code, msg) -> tvPosition.setText("Position [UNCONFIRMED API on C40]: error " + code + " " + msg)));
    }

    private void renderRuntimeInfoAndHealth() {
        if (controller == null) {
            return;
        }
        RuntimeSnapshot info = controller.getRuntimeInfo();
        StringBuilder sb = new StringBuilder();
        sb.append("Work mode: ").append(info.getWorkMode()).append('\n');
        sb.append("Sync status: ").append(info.getSyncStatus()).append('\n');
        sb.append("Power: ").append(info.getPower()).append('\n');
        sb.append("Total odometer: ").append(info.getTotalOdo()).append('\n');
        sb.append("Emergency enable: ").append(info.isEmergencyEnable()).append('\n');
        sb.append("Emergency open: ").append(info.isEmergencyOpen()).append('\n');
        sb.append("Motor status (runtime cache): ").append(info.getMotorStatus()).append('\n');
        sb.append("Robot IP: ").append(emptyToPlaceholder(info.getRobotIp())).append('\n');
        sb.append("Robot arm board: ").append(emptyToPlaceholder(info.getRobotArmInfo())).append('\n');
        sb.append("STM32 board: ").append(emptyToPlaceholder(info.getRobotStm32Info())).append('\n');
        sb.append("Robot properties: ").append(emptyToPlaceholder(info.getRobotProperties())).append('\n');
        sb.append("Destination list: ").append(emptyToPlaceholder(info.getDestList()));
        tvRuntimeInfo.setText(sb.toString());

        HealthEvent health = controller.getHealth();
        HealthEvent heartbeat = controller.getHeartbeat();
        StringBuilder healthSb = new StringBuilder();
        healthSb.append("Last health event: ").append(health != null ? health.getRawContent() : "none yet").append('\n');
        healthSb.append("Last heartbeat: ").append(heartbeat != null ? heartbeat.getRawContent() : "none yet");
        tvHealth.setText(healthSb.toString());
    }

    private interface SuccessHandler {
        void onSuccess(String response);
    }

    private interface ErrorHandler {
        void onError(int code, String message);
    }

    private SdkCallback uiCallback(SuccessHandler success, ErrorHandler error) {
        return new SdkCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                runOnUiThread(() -> success.onSuccess(rawResponse));
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                runOnUiThread(() -> error.onError(errorCode, errorMessage));
            }
        };
    }

    private void appendLogEntry(LogEntry entry) {
        rawLogBuilder.append(TIME_FORMAT.format(entry.getTimestamp()))
                .append(' ')
                .append(entry.toString())
                .append('\n');
        tvRawLog.setText(rawLogBuilder.toString());
        svRawLog.post(() -> svRawLog.fullScroll(View.FOCUS_DOWN));
    }

    private static String emptyToPlaceholder(String value) {
        return (value == null || value.isEmpty()) ? "(unknown)" : value;
    }
}
