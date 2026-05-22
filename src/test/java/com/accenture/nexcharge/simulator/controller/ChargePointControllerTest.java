package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.ChargePointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChargePointController.class)
@Import(GlobalExceptionHandler.class)
class ChargePointControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ChargePointService service;

    @Test
    void getAllReturnsList() throws Exception {
        ChargePointDto cp = new ChargePointDto("BORNE_A", "Legrand", "Green'Up Premium",
                "LGR-001", "1.4.2", ChargePointStatus.Available, true,
                Instant.now(), Instant.now(), "NoError",
                List.of(new ConnectorDto(1, ConnectorStatus.Available, 0.0, 0.0, 230.0, 22.0, 0.0, "NoError", false, null, null)));
        when(service.getAll()).thenReturn(List.of(cp));

        mvc.perform(get("/api/chargepoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chargePointId").value("BORNE_A"))
                .andExpect(jsonPath("$[0].connectors[0].connectorId").value(1));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(service.getById("UNKNOWN")).thenThrow(new ChargePointNotFoundException("UNKNOWN"));
        mvc.perform(get("/api/chargepoints/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Charge point not found: UNKNOWN"));
    }

    @Test
    void getConnectorsReturnsList() throws Exception {
        when(service.getConnectors("BORNE_A")).thenReturn(List.of(
                new ConnectorDto(1, ConnectorStatus.Charging, 7.2, 31.0, 230.0, 38.5, 14.5, "NoError", false, null, null)));
        mvc.perform(get("/api/chargepoints/BORNE_A/connectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPowerKw").value(7.2));
    }

    @Test
    void getConnectorsReturns404WhenChargePointMissing() throws Exception {
        when(service.getConnectors("UNKNOWN")).thenThrow(new ChargePointNotFoundException("UNKNOWN"));
        mvc.perform(get("/api/chargepoints/UNKNOWN/connectors"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Charge point not found: UNKNOWN"));
    }
}
