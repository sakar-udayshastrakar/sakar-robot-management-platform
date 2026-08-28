package com.sakarrobotics.cloud.command;

/**
 * The command lifecycle, verbatim from the implementation brief (extends
 * Master Requirements Part 20's lifecycle with the explicit
 * pre-dispatch states this backend's command-signing pipeline needs).
 * {@code COMMAND_RECEIVED} is deliberately NOT {@code COMMAND_SUCCESS} —
 * the backend must never collapse "the agent got it" into "it worked."
 */
public enum CommandStatus {
    REQUESTED,
    AUTHORIZED,
    SIGNED,
    SENT,
    COMMAND_RECEIVED,
    RUNNING,
    COMMAND_SUCCESS,
    COMMAND_FAILED,
    COMMAND_TIMEOUT,
    CANCELLED
}
