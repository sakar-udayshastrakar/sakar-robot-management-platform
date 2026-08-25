package com.sakarrobotics.c40agent.diagnostics;

import android.os.Build;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Read-only report of the Android device/environment this agent is
 * running on. Never opens, writes to, or otherwise touches hardware -
 * every value here comes from android.os.Build or standard java.net
 * introspection.
 */
public final class DeviceEnvironmentInspector {

    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static String getBuildFingerprint() {
        return Build.FINGERPRINT;
    }

    public static String getManufacturer() {
        return Build.MANUFACTURER;
    }

    public static String getModel() {
        return Build.MODEL;
    }

    public static String getPrimaryCpuAbi() {
        String[] abis = getSupportedAbis();
        return abis.length > 0 ? abis[0] : "unknown";
    }

    public static String[] getSupportedAbis() {
        return Build.SUPPORTED_ABIS != null ? Build.SUPPORTED_ABIS : new String[0];
    }

    /**
     * The Peanut SDK's compiled native libraries only ship armeabi and
     * armeabi-v7a (no arm64-v8a) - see COMPATIBILITY_REPORT.md. This flags
     * whether the running device's preferred ABI is 32-bit ARM.
     */
    public static boolean isSupportedByNativeSdkLibs() {
        for (String abi : getSupportedAbis()) {
            if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    /** Read-only listing of network interfaces and their bound addresses. */
    public static List<String> getNetworkInterfaceSummaries() {
        List<String> results = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return results;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                List<String> addresses = new ArrayList<>();
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    addresses.add(addrs.nextElement().getHostAddress());
                }
                results.add(iface.getName() + ": " + addresses);
            }
        } catch (Exception e) {
            results.add("error reading network interfaces: " + e.getMessage());
        }
        return Collections.unmodifiableList(results);
    }

    private DeviceEnvironmentInspector() {
    }
}
