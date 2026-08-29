package com.sakarrobotics.cloud.mqtt.dto;

/**
 * {@code messageType: PRESENCE} payload — published by the agent on
 * connect ({@code ONLINE}, retained) and by the broker's Last Will and
 * Testament on an unclean disconnect ({@code OFFLINE}, retained), per
 * {@code SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md} §4's "MQTT last-will is the
 * primary offline-detection mechanism" recommendation.
 */
public record PresencePayload(String status) {

    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";
}
