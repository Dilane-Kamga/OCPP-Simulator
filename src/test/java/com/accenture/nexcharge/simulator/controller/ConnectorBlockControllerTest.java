package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.service.ChargePointService;
import com.accenture.nexcharge.simulator.service.ConnectorNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChargePointController.class)
@Import(GlobalExceptionHandler.class)
class ConnectorBlockControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ChargePointService service;

    @Test
    void blockConnector_happyPath_returns200WithBlockedDto() throws Exception {
        Instant blockedAt = Instant.parse("2026-05-23T10:00:00Z");
        ConnectorDto dto = new ConnectorDto(
                1, ConnectorStatus.Charging, 7.2, 31.0, 230.0, 38.5, 14.5, "NoError",
                true, "Quarterly maintenance", blockedAt);
        when(service.blockConnector("BORNE_A", 1, "Quarterly maintenance")).thenReturn(dto);

        mvc.perform(put("/api/chargepoints/BORNE_A/connectors/1/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Quarterly maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockedReason").value("Quarterly maintenance"))
                .andExpect(jsonPath("$.connectorId").value(1));
    }

    @Test
    void blockConnector_missingConnector_returns404() throws Exception {
        when(service.blockConnector("BORNE_Z", 1, "reason"))
                .thenThrow(new ConnectorNotFoundException("BORNE_Z", 1));

        mvc.perform(put("/api/chargepoints/BORNE_Z/connectors/1/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"reason\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Connector not found: BORNE_Z/1"));
    }

    @Test
    void blockConnector_blankReason_returns400() throws Exception {
        mvc.perform(put("/api/chargepoints/BORNE_A/connectors/1/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blockConnector_nullReason_returns400() throws Exception {
        mvc.perform(put("/api/chargepoints/BORNE_A/connectors/1/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unblockConnector_happyPath_returns200WithUnblockedDto() throws Exception {
        ConnectorDto dto = new ConnectorDto(
                1, ConnectorStatus.Charging, 7.2, 31.0, 230.0, 38.5, 14.5, "NoError",
                false, null, null);
        when(service.unblockConnector("BORNE_A", 1)).thenReturn(dto);

        mvc.perform(delete("/api/chargepoints/BORNE_A/connectors/1/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.connectorId").value(1));
    }

    @Test
    void unblockConnector_missingConnector_returns404() throws Exception {
        when(service.unblockConnector("BORNE_Z", 1))
                .thenThrow(new ConnectorNotFoundException("BORNE_Z", 1));

        mvc.perform(delete("/api/chargepoints/BORNE_Z/connectors/1/block"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Connector not found: BORNE_Z/1"));
    }
}
