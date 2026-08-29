package com.sakarrobotics.cloud.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Pure unit tests — no Spring context, no broker (Phase 3). */
class MqttTopicResolverTest {

    private final MqttTopicResolver resolver = new MqttTopicResolver(new MqttProperties());

    @Test
    void buildsAndParsesATopicWithASite_roundTrip() {
        UUID org = UUID.randomUUID();
        UUID site = UUID.randomUUID();
        UUID robot = UUID.randomUUID();

        String topic = resolver.topic(org, site, robot, MqttTopicKind.TELEMETRY);
        assertThat(topic).isEqualTo("sakar/" + org + "/" + site + "/" + robot + "/telemetry");

        ParsedMqttTopic parsed = resolver.parse(topic);
        assertThat(parsed.organizationId()).isEqualTo(org);
        assertThat(parsed.siteId()).isEqualTo(site);
        assertThat(parsed.robotId()).isEqualTo(robot);
        assertThat(parsed.kind()).isEqualTo(MqttTopicKind.TELEMETRY);
    }

    @Test
    void aRobotWithNoSite_usesTheNoSiteSegment_roundTrip() {
        UUID org = UUID.randomUUID();
        UUID robot = UUID.randomUUID();

        String topic = resolver.topic(org, null, robot, MqttTopicKind.HEARTBEAT);
        assertThat(topic).isEqualTo("sakar/" + org + "/_/" + robot + "/heartbeat");

        ParsedMqttTopic parsed = resolver.parse(topic);
        assertThat(parsed.siteId()).isNull();
    }

    @Test
    void subscriptionFilter_usesWildcardsForOrgSiteRobot() {
        assertThat(resolver.subscriptionFilter(MqttTopicKind.EVENTS)).isEqualTo("sakar/+/+/+/events");
    }

    @Test
    void inboundSubscriptionFilters_coversEveryAgentAuthoredKind_butNotAck() {
        String[] filters = resolver.inboundSubscriptionFilters();
        assertThat(filters).containsExactlyInAnyOrder(
                "sakar/+/+/+/presence", "sakar/+/+/+/heartbeat", "sakar/+/+/+/telemetry",
                "sakar/+/+/+/events", "sakar/+/+/+/errors");
        assertThat(filters).doesNotContain("sakar/+/+/+/ack");
    }

    @Test
    void parse_rejectsAMalformedTopic() {
        assertThatThrownBy(() -> resolver.parse("not/a/valid/sakar/topic/at/all"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.parse("sakar/not-a-uuid/_/" + UUID.randomUUID() + "/telemetry"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.parse("wrongprefix/" + UUID.randomUUID() + "/_/" + UUID.randomUUID() + "/telemetry"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
