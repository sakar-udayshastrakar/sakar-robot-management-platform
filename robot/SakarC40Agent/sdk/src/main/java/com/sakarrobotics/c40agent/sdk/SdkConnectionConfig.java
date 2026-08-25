package com.sakarrobotics.c40agent.sdk;

/**
 * App-level connection configuration for the Peanut SDK. Values are never
 * hardcoded here - {@link #fromBuildConfig()} reads them from this
 * module's BuildConfig, which in turn is generated from the git-ignored
 * secrets.properties file at the project root (see
 * secrets.properties.example).
 */
public final class SdkConnectionConfig {

    private final String appId;
    private final String appSecret;
    private final SdkLinkType linkType;
    private final String linkHost;
    private final int linkPort;

    public SdkConnectionConfig(String appId, String appSecret, SdkLinkType linkType,
                                String linkHost, int linkPort) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.linkType = linkType;
        this.linkHost = linkHost;
        this.linkPort = linkPort;
    }

    /**
     * Builds config from this module's BuildConfig fields (APP_ID,
     * APP_SECRET, LINK_TYPE, LINK_HOST, LINK_PORT), which Gradle generates
     * from secrets.properties at build time. This is the only supported
     * way to obtain a config in app code - do not construct credentials
     * inline.
     */
    public static SdkConnectionConfig fromBuildConfig() {
        SdkLinkType linkType;
        try {
            linkType = SdkLinkType.valueOf(BuildConfig.LINK_TYPE);
        } catch (IllegalArgumentException e) {
            linkType = SdkLinkType.DEFAULT;
        }
        return new SdkConnectionConfig(
                BuildConfig.APP_ID,
                BuildConfig.APP_SECRET,
                linkType,
                BuildConfig.LINK_HOST,
                BuildConfig.LINK_PORT);
    }

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public SdkLinkType getLinkType() {
        return linkType;
    }

    public String getLinkHost() {
        return linkHost;
    }

    public int getLinkPort() {
        return linkPort;
    }

    public boolean hasCredentials() {
        return appId != null && !appId.isEmpty() && appSecret != null && !appSecret.isEmpty();
    }
}
