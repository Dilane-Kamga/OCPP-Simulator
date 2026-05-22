package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.service.ConfigurationService;
import com.accenture.nexcharge.simulator.simulator.ChargePointSimulator;
import com.accenture.nexcharge.simulator.simulator.SimulatorManager;
import com.accenture.nexcharge.simulator.simulator.SimulatorState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RemoteCommandController.class)
@Import(GlobalExceptionHandler.class)
class RemoteCommandControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SimulatorManager manager;
    @MockBean ChargePointSimulator simulator;
    @MockBean ConfigurationService configurationService;

    @Test
    void remoteStartReturnsAccepted() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(simulator.getState()).thenReturn(SimulatorState.AVAILABLE);

        mvc.perform(post("/api/chargepoints/BORNE_A/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idTag", "RFID-0042", "connectorId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Accepted"));

        verify(simulator).startSession(1, "RFID-0042");
    }

    @Test
    void remoteStartReturns404WhenChargePointMissing() throws Exception {
        when(manager.get("UNKNOWN")).thenReturn(null);
        mvc.perform(post("/api/chargepoints/UNKNOWN/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idTag", "RFID-0042", "connectorId", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void remoteStartReturns400OnValidationError() throws Exception {
        mvc.perform(post("/api/chargepoints/BORNE_A/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idTag", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetTriggersSimulatorReset() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        mvc.perform(post("/api/chargepoints/BORNE_A/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "Soft"))))
                .andExpect(status().isOk());
        verify(simulator).reset();
    }
}
