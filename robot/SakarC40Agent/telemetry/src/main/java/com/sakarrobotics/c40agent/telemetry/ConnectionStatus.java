package com.sakarrobotics.c40agent.telemetry;

/** Local, app-tracked connection state - not a Peanut SDK type. */
public enum ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    INIT_FAILED
}
