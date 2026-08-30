package com.sakarrobotics.c40agent.api.mqtt;

/**
 * Callback a {@link RobotCommandExecutor} uses to report a command's
 * lifecycle transitions back through {@link CommandDispatcher} (Roadmap
 * Phase 6/7 "Robot Agent Command Loop"). An executor MUST call exactly one
 * of {@link #reportCompleted(String)} / {@link #reportFailed(String)}
 * exactly once per command, and MAY call {@link #reportExecuting()} any
 * number of times before that (typically once, when real work begins).
 *
 * <p>These calls are the only way a command's outcome reaches the Sakar
 * Cloud backend — never assume the backend otherwise knows anything about
 * what an executor did.
 */
public interface RobotCommandResultReporter {

    /** Call when actual execution begins (after any queuing/validation). */
    void reportExecuting();

    /**
     * Call exactly once, on success, but ONLY when the executor has actual
     * evidence the command's real-world effect completed — not merely that
     * a local control interface accepted the request. Conflating "accepted"
     * with "completed" is exactly the mistake Roadmap Phase 6/7 exists to
     * prevent (an MQTT publish succeeding is not a robot doing anything).
     * If only acceptance/dispatch evidence is available, call {@link
     * #reportDispatched(String)} instead — do not call this method.
     */
    void reportCompleted(String detail);

    /**
     * Call exactly once, when the executor has confirmation that a local
     * robot control interface (e.g. the Peanut SDK) accepted and dispatched
     * the command, but has no further signal confirming the command's
     * real-world effect actually completed (e.g. the robot physically
     * arriving at a charging dock). This is a genuinely distinct, honest
     * outcome from both {@link #reportCompleted(String)} (unconfirmed
     * effect) and {@link #reportFailed(String)} (the interface itself
     * rejected or errored on the request).
     */
    void reportDispatched(String detail);

    /** Call exactly once, on failure. {@code detail} should explain why. */
    void reportFailed(String detail);
}
