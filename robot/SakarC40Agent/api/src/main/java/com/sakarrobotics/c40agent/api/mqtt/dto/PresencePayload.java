package com.sakarrobotics.c40agent.api.mqtt.dto;

/** Published on connect ({@code ONLINE}, retained) and as the broker's Last Will ({@code OFFLINE}, retained). */
public final class PresencePayload {

    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";

    private final String status;

    public PresencePayload(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
