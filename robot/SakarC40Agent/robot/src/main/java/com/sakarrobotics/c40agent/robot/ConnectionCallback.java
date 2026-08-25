package com.sakarrobotics.c40agent.robot;

public interface ConnectionCallback {
    void onConnected();

    void onConnectionFailed(int errorCode);
}
