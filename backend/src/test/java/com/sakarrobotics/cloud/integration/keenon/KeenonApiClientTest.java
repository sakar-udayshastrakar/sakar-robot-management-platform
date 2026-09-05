package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Pins the exact outbound HTTP request {@link KeenonApiClient} sends for the robot-status
 * call, using a real embedded {@link HttpServer} rather than mocking at the interface
 * boundary (as {@code KeenonRobotAdapterTest} does) - the migration this covers (SEC-2026
 * Keenon status-endpoint migration) is entirely about *which URL* gets called, something
 * an interface-level mock of this class can never catch. No new test dependency was added:
 * {@code com.sun.net.httpserver.HttpServer} ships with the JDK.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeenonApiClientTest {

    @Mock
    private KeenonOAuthTokenService tokenService;

    private HttpServer server;
    private KeenonProperties properties;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        properties = new KeenonProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

        when(tokenService.currentAccessToken()).thenReturn("test-token");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Registers a handler that records the request path+query and replies with the given body. */
    private void respondWith(String responseBody) {
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getRawQuery());
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private KeenonApiClient client() {
        return new KeenonApiClient(properties, tokenService);
    }

    @Test
    void getRobotStatus_callsTheCleaningFamilyEndpoint_notTheSceneEndpoint() throws Exception {
        respondWith("{\"code\":610000,\"msg\":\"success\",\"data\":{\"mainState\":-1,\"subState\":-1}}");

        callGetRobotStatus(client(), "94:BA:06:CA:99:F3");

        assertThat(requestedPaths).hasSize(1);
        String requested = requestedPaths.get(0);
        assertThat(requested).startsWith("/api/open/custom/clean/robot/status?");
        assertThat(requested).doesNotContain("/api/open/scene/v1/robot/status");
    }

    @Test
    void getRobotStatus_sendsRobotSnAsTheQueryParameterName() throws Exception {
        respondWith("{\"code\":610000,\"data\":{}}");

        callGetRobotStatus(client(), "94:BA:06:CA:99:F3");

        assertThat(requestedPaths.get(0)).contains("robotSn=");
        assertThat(requestedPaths.get(0)).doesNotContain("robotId=");
    }

    @Test
    void getRobotStatus_singlyEncodesTheColonInTheMacStyleId_neverDoubleEncodesOrLeavesLiteral() throws Exception {
        respondWith("{\"code\":610000,\"data\":{}}");

        callGetRobotStatus(client(), "94:BA:06:CA:99:F3");

        String requested = requestedPaths.get(0);
        // Single percent-encoding of ':' -> %3A. Neither the literal colon (rejected
        // earlier in this investigation as untested) nor the double-encoded %253A (the
        // bug this class's uri(URI) fix already prevents) should appear.
        assertThat(requested).contains("robotSn=94%3ABA%3A06%3ACA%3A99%3AF3");
        assertThat(requested).doesNotContain("94:BA:06:CA:99:F3");
        assertThat(requested).doesNotContain("%253A");
    }

    /**
     * {@link KeenonApiClient#getRobotStatus(String)} is package-private and called
     * internally by {@code KeenonRobotAdapter} - reflection keeps this test focused on the
     * client's HTTP behavior without needing to go through the adapter or widen visibility
     * for a URL-shape test alone.
     */
    private static Object callGetRobotStatus(KeenonApiClient client, String robotSn) throws Exception {
        var method = KeenonApiClient.class.getDeclaredMethod("getRobotStatus", String.class);
        method.setAccessible(true);
        AtomicReference<Object> result = new AtomicReference<>();
        result.set(method.invoke(client, robotSn));
        return result.get();
    }
}
