package com.accenture.nexcharge.simulator.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class OpenApiSmokeIT {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void apiDocsEndpointIsReachableAndReturnsJson() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/v3/api-docs", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/json");
    }
}
