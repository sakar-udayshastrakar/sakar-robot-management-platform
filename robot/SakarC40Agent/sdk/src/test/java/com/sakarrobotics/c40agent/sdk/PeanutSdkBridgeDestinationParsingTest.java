package com.sakarrobotics.c40agent.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonSyntaxException;
import com.sakarrobotics.c40agent.telemetry.Destination;

/**
 * Software-test-only coverage of {@link PeanutSdkBridge#parseDestinations}
 * (Roadmap Phase 8 "Destination discovery", see {@code
 * C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md}). This is a pure function
 * (String in, List&lt;Destination&gt; out) - it never touches {@code
 * PeanutSDK.getInstance()}, so unlike every other test in this module
 * (see {@code C40RobotControllerTest} in :robot for why), it needs no
 * "the real SDK singleton throws" workaround. No Android runtime, no
 * Peanut SDK connection, no physical robot anywhere in this test.
 *
 * <p>The JSON fixtures below shape-match the verified vendor bean
 * ({@code com.keenon.sdk.api.NavigationDestPoseApi.Bean.DataBean},
 * confirmed via {@code javap} against this project's own vendored AAR) -
 * they are hand-built to match that confirmed shape, not captured from
 * any real robot response.
 */
class PeanutSdkBridgeDestinationParsingTest {

    @Test
    void parseDestinations_validResponse_mapsEveryField() {
        String json = "{\"status\":0,\"code\":0,\"msg\":\"ok\",\"data\":["
                + "{\"id\":5,\"name\":\"Kitchen\",\"type\":\"landmark\",\"floor\":1,"
                + "\"bind_map_md5\":\"abc123\","
                + "\"pose\":{\"position\":{\"x\":1.5,\"y\":2.5,\"z\":0},"
                + "\"orientation\":{\"w\":1.0,\"x\":0,\"y\":0,\"z\":0.0}}}"
                + "]}";

        List<Destination> destinations = PeanutSdkBridge.parseDestinations(json);

        assertEquals(1, destinations.size());
        Destination destination = destinations.get(0);
        assertEquals(5, destination.getId());
        assertEquals("Kitchen", destination.getName());
        assertEquals("landmark", destination.getType());
        assertEquals(1, destination.getFloor());
        assertEquals("abc123", destination.getMapId());
        assertEquals(1.5, destination.getPose().getPosition().getX());
        assertEquals(2.5, destination.getPose().getPosition().getY());
        assertEquals(0.0, destination.getPose().getPosition().getZ());
        assertEquals(1.0, destination.getPose().getOrientation().getW());
        assertEquals(0.0, destination.getPose().getOrientation().getZ());
    }

    @Test
    void parseDestinations_multipleDestinations_preservesOrderAndDistinctIds() {
        String json = "{\"data\":["
                + "{\"id\":1,\"name\":\"A\",\"floor\":0},"
                + "{\"id\":2,\"name\":\"B\",\"floor\":0}"
                + "]}";

        List<Destination> destinations = PeanutSdkBridge.parseDestinations(json);

        assertEquals(2, destinations.size());
        assertEquals(1, destinations.get(0).getId());
        assertEquals(2, destinations.get(1).getId());
    }

    @Test
    void parseDestinations_destinationWithNoPose_returnsNullPoseNotAnInventedOne() {
        String json = "{\"data\":[{\"id\":9,\"name\":\"No pose here\",\"floor\":0}]}";

        List<Destination> destinations = PeanutSdkBridge.parseDestinations(json);

        assertNull(destinations.get(0).getPose());
    }

    @Test
    void parseDestinations_emptyDataArray_returnsEmptyListNotAnError() {
        String json = "{\"status\":0,\"code\":0,\"msg\":\"ok\",\"data\":[]}";

        List<Destination> destinations = PeanutSdkBridge.parseDestinations(json);

        assertTrue(destinations.isEmpty());
    }

    @Test
    void parseDestinations_missingDataField_returnsEmptyListNotAnError() {
        String json = "{\"status\":0,\"code\":0,\"msg\":\"ok\"}";

        List<Destination> destinations = PeanutSdkBridge.parseDestinations(json);

        assertTrue(destinations.isEmpty());
    }

    @Test
    void parseDestinations_nullRawResponse_returnsEmptyListNotAnError() {
        assertTrue(PeanutSdkBridge.parseDestinations(null).isEmpty());
    }

    @Test
    void parseDestinations_emptyStringRawResponse_returnsEmptyListNotAnError() {
        // Deliberately different from Keenon's own Peanut Clean app, which treats this exact
        // shape as a business-level failure ("point empty") - see parseDestinations' Javadoc.
        assertTrue(PeanutSdkBridge.parseDestinations("").isEmpty());
    }

    @Test
    void parseDestinations_malformedJson_throwsRatherThanInventingData() {
        assertThrows(JsonSyntaxException.class, () -> PeanutSdkBridge.parseDestinations("{not valid json"));
    }

    @Test
    void parseDestinations_jsonOfTheWrongShape_throwsRatherThanInventingData() {
        // A JSON array where an object is expected - Gson throws on the shape mismatch itself.
        assertThrows(JsonSyntaxException.class, () -> PeanutSdkBridge.parseDestinations("[1,2,3]"));
    }
}
