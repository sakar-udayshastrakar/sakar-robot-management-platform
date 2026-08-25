package com.sakarrobotics.c40agent.sdk;

/** Result of {@link PeanutSdkBridge#init}. */
public interface SdkInitCallback {
    void onInitSuccess();

    void onInitError(int errorCode);
}
