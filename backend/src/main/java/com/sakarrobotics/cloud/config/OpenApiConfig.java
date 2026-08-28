package com.sakarrobotics.cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sakarCloudOpenApi() {
        String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Sakar Cloud Backend API")
                        .description("Sakar Robot Management Platform — vendor-neutral REST API. "
                                + "See SAKAR_ROBOT_PLATFORM_API_SPEC.md for the full specification this "
                                + "implementation tracks.")
                        .version("phase-1"))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .components(new Components().addSecuritySchemes(bearerScheme,
                        new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
