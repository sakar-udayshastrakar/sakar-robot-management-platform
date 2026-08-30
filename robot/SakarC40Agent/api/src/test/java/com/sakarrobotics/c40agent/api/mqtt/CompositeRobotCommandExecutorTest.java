package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Software-test-only coverage of command-type routing (Roadmap Phase 7). */
@ExtendWith(MockitoExtension.class)
class CompositeRobotCommandExecutorTest {

    @Mock
    private RobotCommandExecutor startTaskExecutor;
    @Mock
    private RobotCommandExecutor returnToDockExecutor;
    @Mock
    private RobotCommandResultReporter reporter;

    @Test
    void routesToTheExecutorRegisteredForItsCommandType() {
        CompositeRobotCommandExecutor composite = new CompositeRobotCommandExecutor(
                Map.of("START_TASK", startTaskExecutor, "RETURN_TO_DOCK", returnToDockExecutor));

        composite.execute("RETURN_TO_DOCK", Map.of(), reporter);

        verify(returnToDockExecutor).execute(eq("RETURN_TO_DOCK"), any(), eq(reporter));
        verify(startTaskExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void anotherCommandType_routesToItsOwnExecutor() {
        CompositeRobotCommandExecutor composite = new CompositeRobotCommandExecutor(
                Map.of("START_TASK", startTaskExecutor, "RETURN_TO_DOCK", returnToDockExecutor));

        composite.execute("START_TASK", Map.of(), reporter);

        verify(startTaskExecutor).execute(eq("START_TASK"), any(), eq(reporter));
        verify(returnToDockExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void unregisteredCommandType_reportsFailedWithoutThrowingOrCallingAnyExecutor() {
        CompositeRobotCommandExecutor composite = new CompositeRobotCommandExecutor(
                Map.of("RETURN_TO_DOCK", returnToDockExecutor));

        assertDoesNotThrow(() -> composite.execute("STOP_TASK", Map.of(), reporter));

        verify(reporter).reportFailed(contains("STOP_TASK"));
        verify(returnToDockExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void nullCommandType_reportsFailedWithoutThrowing() {
        CompositeRobotCommandExecutor composite = new CompositeRobotCommandExecutor(
                Map.of("RETURN_TO_DOCK", returnToDockExecutor));

        assertDoesNotThrow(() -> composite.execute(null, Map.of(), reporter));

        verify(reporter).reportFailed(any());
    }
}
