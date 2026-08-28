package com.sakarrobotics.cloud.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "sakar.bootstrap")
public class BootstrapProperties {

    /** Off by default in any environment where SAKAR_BOOTSTRAP_ADMIN is not explicitly set to true. */
    private boolean adminEnabled = false;

    private String adminEmail = "admin@sakarrobotics.local";
}
