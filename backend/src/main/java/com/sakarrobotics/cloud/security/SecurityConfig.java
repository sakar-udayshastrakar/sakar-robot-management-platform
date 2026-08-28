package com.sakarrobotics.cloud.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.sakarrobotics.cloud.security.jwt.JwtAccessDeniedHandler;
import com.sakarrobotics.cloud.security.jwt.JwtAuthenticationEntryPoint;
import com.sakarrobotics.cloud.security.jwt.JwtAuthenticationFilter;
import com.sakarrobotics.cloud.security.jwt.JwtProperties;
import com.sakarrobotics.cloud.security.jwt.JwtService;

import lombok.RequiredArgsConstructor;

/**
 * Stateless-JWT security foundation (Master Requirements Part 19 / Part 16).
 * The web/mobile client authenticates once via {@code /api/v1/auth/login}
 * and thereafter presents a bearer access token; every other endpoint is
 * closed by default ({@code anyRequest().authenticated()}) with
 * fine-grained permission checks applied at the method level via
 * {@code @PreAuthorize} (Part 19.B's permission matrix).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/actuator/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            // Vendor-facing, authenticated via shared secret header — not a Sakar user JWT.
            "/integrations/keenon/webhooks/**",
            // The WebSocket handshake itself; STOMP CONNECT/SUBSCRIBE frames are authenticated
            // and authorized by WebSocketAuthChannelInterceptor at the protocol level instead.
            "/ws/**"
    };

    private final JwtService jwtService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Login is authenticated manually in AuthService (direct BCrypt comparison against
    // User.passwordHash) rather than via AuthenticationManager/UserDetailsService — this is a
    // stateless bearer-token API, not a form-login session, and UserPrincipal (the JWT-derived
    // principal used for authorization) deliberately carries no password hash to begin with.

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Locked down to explicit origins per deployment (Part 16) — never "*" with credentials.
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API, no cookie-based session
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
