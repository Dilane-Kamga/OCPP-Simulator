package com.accenture.nexcharge.simulator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds hardening response headers to every {@code /api/**} response.
 *
 * <p>Excluded paths:
 * <ul>
 *   <li>{@code /h2-console/**} — H2 web console uses {@code <iframe>} internally and
 *       would break if {@code X-Frame-Options: DENY} were applied.</li>
 * </ul>
 *
 * <p>Headers intentionally omitted:
 * <ul>
 *   <li>{@code Content-Security-Policy} — would break the H2 console and Swagger UI.</li>
 *   <li>{@code Strict-Transport-Security} — this service runs over plain HTTP in dev/demo.</li>
 *   <li>{@code X-XSS-Protection} — per modern guidance the mitigating value is
 *       {@code 0} (disable the legacy browser XSS auditor); we omit it entirely since
 *       CSP is the correct defence and we are not adding CSP here.</li>
 * </ul>
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String H2_CONSOLE_PREFIX = "/h2-console";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(H2_CONSOLE_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        filterChain.doFilter(request, response);
    }
}
