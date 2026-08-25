package com.sakarrobotics.c40agent.diagnostics;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only existence checks for the serial device nodes the Peanut SDK's
 * COM link type would use on the physical robot. This class NEVER opens,
 * reads from, writes to, or otherwise claims these device nodes - it only
 * calls File#exists(), which is safe even while the stock Keenon software
 * is holding the port open.
 */
public final class SerialPortInspector {

    private static final String[] CANDIDATE_PORTS = {"/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3"};

    public static Map<String, Boolean> checkSerialPorts() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String path : CANDIDATE_PORTS) {
            result.put(path, new File(path).exists());
        }
        return result;
    }

    private SerialPortInspector() {
    }
}
