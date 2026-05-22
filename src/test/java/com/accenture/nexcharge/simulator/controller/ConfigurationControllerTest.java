package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ChangeConfigurationResponseDto;
import com.accenture.nexcharge.simulator.model.dto.ConfigurationKeyDto;
import com.accenture.nexcharge.simulator.model.dto.GetConfigurationResponseDto;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.ConfigurationService;
import com.accenture.nexcharge.simulator.simulator.ChargePointSimulator;
import com.accenture.nexcharge.simulator.simulator.SimulatorManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RemoteCommandController.class)
@Import(GlobalExceptionHandler.class)
class ConfigurationControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    SimulatorManager manager;

    @MockBean
    ChargePointSimulator simulator;

    @MockBean
    ConfigurationService configurationService;

    // ── change-configuration ─────────────────────────────────────────────────

    @Test
    void changeConfigurationHappyPath() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(configurationService.changeConfiguration("BORNE_A", "HeartbeatInterval", "60"))
                .thenReturn(new ChangeConfigurationResponseDto("Accepted"));

        mvc.perform(post("/api/chargepoints/BORNE_A/change-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("key", "HeartbeatInterval", "value", "60"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Accepted"));
    }

    @Test
    void changeConfigurationReturns404WhenChargePointMissing() throws Exception {
        when(manager.get("UNKNOWN")).thenReturn(null);

        mvc.perform(post("/api/chargepoints/UNKNOWN/change-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("key", "HeartbeatInterval", "value", "60"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeConfigurationReturns400WhenKeyBlank() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);

        mvc.perform(post("/api/chargepoints/BORNE_A/change-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("key", "", "value", "60"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeConfigurationReturns400WhenValueBlank() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);

        mvc.perform(post("/api/chargepoints/BORNE_A/change-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("key", "HeartbeatInterval", "value", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeConfigurationForwardsRejectStatus() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(configurationService.changeConfiguration("BORNE_A", "UnknownKey", "x"))
                .thenReturn(new ChangeConfigurationResponseDto("Rejected"));

        mvc.perform(post("/api/chargepoints/BORNE_A/change-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("key", "UnknownKey", "value", "x"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Rejected"));
    }

    // ── get-configuration ────────────────────────────────────────────────────

    @Test
    void getConfigurationHappyPath() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(configurationService.getConfiguration("BORNE_A",
                List.of("HeartbeatInterval", "MeterValueSampleInterval")))
                .thenReturn(new GetConfigurationResponseDto(
                        List.of(
                                new ConfigurationKeyDto("HeartbeatInterval", false, "30"),
                                new ConfigurationKeyDto("MeterValueSampleInterval", false, "10")
                        ),
                        List.of()
                ));

        mvc.perform(post("/api/chargepoints/BORNE_A/get-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("keys", List.of("HeartbeatInterval", "MeterValueSampleInterval")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurationKey[0].key").value("HeartbeatInterval"))
                .andExpect(jsonPath("$.configurationKey[0].value").value("30"))
                .andExpect(jsonPath("$.configurationKey[1].key").value("MeterValueSampleInterval"));
    }

    @Test
    void getConfigurationWithEmptyBodyFetchesAll() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(configurationService.getConfiguration("BORNE_A", null))
                .thenReturn(new GetConfigurationResponseDto(
                        List.of(new ConfigurationKeyDto("HeartbeatInterval", false, "30")),
                        List.of()
                ));

        // body omitted → req is null, keys is null → fetch all
        mvc.perform(post("/api/chargepoints/BORNE_A/get-configuration")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurationKey[0].key").value("HeartbeatInterval"));
    }

    @Test
    void getConfigurationReturns404WhenChargePointMissing() throws Exception {
        when(manager.get("UNKNOWN")).thenReturn(null);

        mvc.perform(post("/api/chargepoints/UNKNOWN/get-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("keys", List.of("HeartbeatInterval")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConfigurationReturnsUnknownKeyList() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(configurationService.getConfiguration("BORNE_A", List.of("NonExistentKey")))
                .thenReturn(new GetConfigurationResponseDto(List.of(), List.of("NonExistentKey")));

        mvc.perform(post("/api/chargepoints/BORNE_A/get-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("keys", List.of("NonExistentKey")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unknownKey[0]").value("NonExistentKey"))
                .andExpect(jsonPath("$.configurationKey").isEmpty());
    }
}
