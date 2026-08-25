package com.sakarrobotics.c40agent.sdk;

/**
 * Decoupled mirror of com.keenon.sdk.component.runtime.PeanutRuntime.Listener.
 */
public interface SdkRuntimeEventListener {
    void onEvent(int event, String content);

    void onHealth(String content);

    void onHeartbeat(String content);
}
