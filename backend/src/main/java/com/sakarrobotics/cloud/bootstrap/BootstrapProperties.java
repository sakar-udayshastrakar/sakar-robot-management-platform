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

    private String adminEmail = "admin@sakarrobotics.com";

    /**
     * Optional local-dev override for the bootstrap admin's password
     * (env var {@code SAKAR_BOOTSTRAP_ADMIN_PASSWORD}). Left unset by
     * default — never set a real value here or in any committed config
     * file. See {@link DevAdminSeeder}'s doc comment for what happens
     * when this is left unset.
     */
    private String adminPassword;
}
