package com.sakarrobotics.cloud.security.jwt;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.security.UserPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Request attribute the entry point reads to distinguish "expired" from "invalid/missing". */
    public static final String AUTH_ERROR_ATTRIBUTE = "sakar.auth.error";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                UserPrincipal principal = jwtService.parseAccessToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ApiException ex) {
                // Deliberately not thrown here: exceptions raised before the DispatcherServlet
                // bypass GlobalExceptionHandler. Stash the reason and leave the request
                // unauthenticated so Spring Security's own JwtAuthenticationEntryPoint renders it.
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex.getErrorCode());
            }
        }
        chain.doFilter(request, response);
    }
}
