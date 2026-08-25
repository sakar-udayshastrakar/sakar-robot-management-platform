package com.sakarrobotics.c40agent.sdk;

/**
 * Mirrors com.keenon.common.constant.PeanutConstants.LinkType, verified by
 * decompiling peanut-sdk-release.aar (5 values: DEFAULT, COM, COM_COAP,
 * COAP, HTTP - see COMPATIBILITY_REPORT.md). Kept as our own enum so that
 * no module outside :sdk needs to import a com.keenon.* type.
 *
 * IMPORTANT: which of these the Keenon C40 actually uses has NOT been
 * confirmed. Do not assume one - determine it on the physical robot.
 */
public enum SdkLinkType {
    DEFAULT,
    COM,
    COM_COAP,
    COAP,
    HTTP
}
