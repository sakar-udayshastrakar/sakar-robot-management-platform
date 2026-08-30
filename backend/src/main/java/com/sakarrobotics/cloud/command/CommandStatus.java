package com.sakarrobotics.cloud.command;

/**
 * The command lifecycle, verbatim from the implementation brief (extends
 * Master Requirements Part 20's lifecycle with the explicit
 * pre-dispatch states this backend's command-signing pipeline needs).
 * {@code COMMAND_RECEIVED} is deliberately NOT {@code COMMAND_SUCCESS} —
 * the backend must never collapse "the agent got it" into "it worked."
 *
 * <p>{@code COMMAND_DISPATCHED} (Roadmap Phase 7 "RETURN_TO_DOCK") is a
 * second, equally deliberate non-collapse: some commands only ever have
 * evidence that a local robot control interface (e.g. the Peanut SDK)
 * accepted and dispatched the request, never evidence that the command's
 * real-world effect actually completed. Reporting such a command as
 * {@code COMMAND_SUCCESS} would be a fabricated claim; {@code
 * COMMAND_DISPATCHED} is the honest terminal state for that case — see
 * {@code CommandResultIngestionService}'s status mapping and
 * {@code PeanutSdkReturnToDockExecutor}'s Javadoc (agent side) for exactly
 * when it applies.
 */
public enum CommandStatus {
    REQUESTED,
    AUTHORIZED,
    SIGNED,
    SENT,
    COMMAND_RECEIVED,
    RUNNING,
    COMMAND_DISPATCHED,
    COMMAND_SUCCESS,
    COMMAND_FAILED,
    COMMAND_TIMEOUT,
    CANCELLED;

    /**
     * Once true, {@link CommandResultIngestionService} and {@link
     * CommandExpiryService} never move a command's status again — a
     * terminal outcome is frozen (Roadmap Phase 6/7 idempotency rule: a
     * late/out-of-order agent report must never resurrect or overwrite an
     * already-final result).
     */
    public boolean isTerminal() {
        return this == COMMAND_DISPATCHED || this == COMMAND_SUCCESS || this == COMMAND_FAILED
                || this == COMMAND_TIMEOUT || this == CANCELLED;
    }

    /**
     * Ordinal-independent progression rank used to reject an out-of-order
     * agent report (e.g. a delayed {@code RECEIVED} arriving after a
     * {@code COMPLETED} was already applied) without rejecting legitimate
     * forward progress. Pre-dispatch states ({@code REQUESTED..SENT}) are
     * never reachable from an agent report, so they all rank below every
     * agent-reported state. {@code COMMAND_DISPATCHED} ranks alongside the
     * other terminal outcomes — it is this project's honest final word for
     * a command whose acceptance (but not completion) was confirmed.
     */
    public int progressRank() {
        return switch (this) {
            case REQUESTED, AUTHORIZED, SIGNED, SENT -> 0;
            case COMMAND_RECEIVED -> 1;
            case RUNNING -> 2;
            case COMMAND_DISPATCHED, COMMAND_SUCCESS, COMMAND_FAILED, COMMAND_TIMEOUT, CANCELLED -> 3;
        };
    }
}
