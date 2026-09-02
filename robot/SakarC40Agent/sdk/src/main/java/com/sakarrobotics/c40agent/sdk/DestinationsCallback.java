package com.sakarrobotics.c40agent.sdk;

import java.util.List;

import com.sakarrobotics.c40agent.telemetry.Destination;

/**
 * Decoupled mirror of {@code com.keenon.sdk.external.IDataCallback} for
 * {@link PeanutSdkBridge#getAllDestinations}, so that callers outside
 * :sdk never need to import a com.keenon.* type - same pattern as {@link
 * SdkCallback}, but carrying an already-parsed, vendor-neutral result
 * instead of a raw response string.
 */
public interface DestinationsCallback {

    /**
     * An empty {@code destinations} list is a valid, successful result
     * (the robot has no pre-registered destinations on its currently
     * loaded map right now) - it is never reported as an error. See
     * {@link PeanutSdkBridge#getAllDestinations} for how this differs
     * from Keenon's own Peanut Clean app, which treats an empty raw
     * response string as a business-level failure.
     */
    void onSuccess(List<Destination> destinations);

    void onError(int errorCode, String errorMessage);
}
