package com.accenture.nexcharge.simulator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the security response headers are correctly applied
 * to {@code /api/**} responses and are NOT present on {@code /h2-console/**}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHeadersFilterIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void apiEndpointHasAllSecurityHeaders() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/stats"), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(response.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Frame-Options"))
                .isEqualTo("DENY");
        assertThat(response.getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("no-referrer");
        assertThat(response.getHeaders().getFirst("Permissions-Policy"))
                .isEqualTo("geolocation=(), microphone=(), camera=()");
    }

    @Test
    void h2ConsoleDoesNotHaveXFrameOptions() {
        // The H2 console uses iframes internally; X-Frame-Options must NOT be set for it.
        // We just do a GET on the login page (it may redirect or return 200/404 but the
        // response headers are what matter).
        ResponseEntity<String> response = rest.getForEntity(url("/h2-console/"), String.class);

        // Whatever the HTTP status, the filter must not have added X-Frame-Options
        assertThat(response.getHeaders().getFirst("X-Frame-Options"))
                .as("X-Frame-Options must be absent on h2-console to allow iframe usage")
                .isNull();
    }
}
