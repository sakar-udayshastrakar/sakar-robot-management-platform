package com.sakarrobotics.c40agent.api.mqtt;

/**
 * This agent's identity, as registered with the Sakar Cloud backend
 * (Phase 3 Part 4 - "Every robot has unique robotId", "Every agent has
 * unique agentId", "Robot must belong to an organization/site"). Never
 * constructed with a hardcoded value in app code - see
 * {@code SakarC40Application} for how this is built from BuildConfig
 * fields generated from the git-ignored {@code secrets.properties}.
 */
public final class AgentIdentity {

    private final String organizationId;
    private final String siteId;
    private final String robotId;
    private final String agentId;

    public AgentIdentity(String organizationId, String siteId, String robotId, String agentId) {
        this.organizationId = organizationId;
        this.siteId = siteId;
        this.robotId = robotId;
        this.agentId = agentId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    /** {@code null} or blank if this robot has no site assigned. */
    public String getSiteId() {
        return siteId;
    }

    public String getRobotId() {
        return robotId;
    }

    public String getAgentId() {
        return agentId;
    }

    public boolean isComplete() {
        return notBlank(organizationId) && notBlank(robotId) && notBlank(agentId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
