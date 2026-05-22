package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.simulator.SimulatorScenarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SimulatorController.class)
@Import(GlobalExceptionHandler.class)
class SimulatorControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SimulatorScenarioService service;

    @Test
    void runsValidScenario() throws Exception {
        mvc.perform(post("/api/simulator/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scenario", "PEAK_LOAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Accepted"));

        ArgumentCaptor<com.accenture.nexcharge.simulator.model.dto.ScenarioRequest> captor =
                ArgumentCaptor.forClass(com.accenture.nexcharge.simulator.model.dto.ScenarioRequest.class);
        verify(service).run(captor.capture());
        assertThat(captor.getValue().scenario()).isEqualTo("PEAK_LOAD");
    }

    @Test
    void unknownScenarioReturns400() throws Exception {
        doThrow(new IllegalArgumentException("Unknown scenario: BOGUS")).when(service).run(org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/simulator/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scenario", "BOGUS"))))
                .andExpect(status().isBadRequest());
    }
}
