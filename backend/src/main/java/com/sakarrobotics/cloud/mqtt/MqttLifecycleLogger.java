package com.sakarrobotics.cloud.mqtt;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.srels.ApplicationLog;
import com.sakarrobotics.cloud.srels.ApplicationLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Routes every MQTT lifecycle/ingestion event into the existing {@code
 * application_logs} table (SRELS — Master Requirements Part 12.A) rather
 * than inventing a parallel logging mechanism (Phase 3 Part 13). {@code
 * robotId} is {@code null} for broker-connection-level events (connect,
 * disconnect, reconnecting) that are not about any one robot.
 *
 * <p>Never logs a credential, token, or raw payload — only the fixed event
 * codes and identifiers listed in {@code SAKAR_MQTT_ARCHITECTURE.md}
 * "Observability" (Phase 3 Part 13: "Do not log passwords, tokens, private
 * keys, MQTT credentials, authorization headers, sensitive payloads
 * unnecessarily"). Runs in its own transaction so a logging failure never
 * rolls back the ingestion it is reporting on.
 */
@Component
@RequiredArgsConstructor
public class MqttLifecycleLogger {

    private final ApplicationLogRepository applicationLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String level, UUID robotId, String eventCode, String detail) {
        ApplicationLog entry = new ApplicationLog();
        entry.setSource("mqtt");
        entry.setRobotId(robotId);
        entry.setLevel(level);
        entry.setMessage(eventCode);
        entry.setContext(detail);
        applicationLogRepository.save(entry);
    }

    public void info(UUID robotId, String eventCode, String detail) {
        log("INFO", robotId, eventCode, detail);
    }

    public void warn(UUID robotId, String eventCode, String detail) {
        log("WARN", robotId, eventCode, detail);
    }

    public void error(UUID robotId, String eventCode, String detail) {
        log("ERROR", robotId, eventCode, detail);
    }
}
