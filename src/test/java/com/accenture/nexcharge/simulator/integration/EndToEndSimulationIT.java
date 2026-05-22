package com.accenture.nexcharge.simulator.integration;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.CommandResponse;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class EndToEndSimulationIT {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void simulatorBootsAndRespondsToScenarios() {
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ResponseEntity<List<ChargePointDto>> resp = rest.exchange(
                    "http://localhost:" + port + "/api/chargepoints",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(resp.getBody()).extracting(ChargePointDto::chargePointId).contains("BORNE_E2E");
            assertThat(resp.getBody().stream().filter(cp -> cp.chargePointId().equals("BORNE_E2E")).findFirst().get().online())
                    .isTrue();
        });

        ResponseEntity<CommandResponse> scenario = rest.postForEntity(
                "http://localhost:" + port + "/api/simulator/scenario",
                new HttpEntity<>(new ScenarioRequest("START_ALL", null)),
                CommandResponse.class);
        assertThat(scenario.getStatusCode().is2xxSuccessful()).isTrue();

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ResponseEntity<List<ChargePointDto>> resp = rest.exchange(
                    "http://localhost:" + port + "/api/chargepoints",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            ChargePointDto cp = resp.getBody().stream()
                    .filter(c -> c.chargePointId().equals("BORNE_E2E")).findFirst().orElseThrow();
            ConnectorDto connector = cp.connectors().stream()
                    .filter(c -> c.connectorId() != null && c.connectorId() == 1)
                    .findFirst().orElseThrow();
            assertThat(connector.status()).isIn(ConnectorStatus.Charging, ConnectorStatus.Preparing);
        });
    }
}
