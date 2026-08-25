package com.sakarrobotics.c40agent.sdk;

/**
 * Decoupled mirror of com.keenon.sdk.external.IDataCallback so that
 * callers outside :sdk never need to import a com.keenon.* type.
 */
public interface SdkCallback {
    void onSuccess(String rawResponse);

    void onError(int errorCode, String errorMessage);
}
