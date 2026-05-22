package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.MeterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeterController.class)
@Import(GlobalExceptionHandler.class)
class MeterControllerTest {

    @Autowired MockMvc mvc;
    @MockBean MeterService service;

    @Test
    void returnsMeterValues() throws Exception {
        when(service.findRecent(eq("BORNE_A"), eq(1), eq(60))).thenReturn(List.of(
                new MeterValueDto(Instant.parse("2026-05-22T14:30:10Z"), 1, 1001,
                        "Power.Active.Import", 7200.0, "W")));

        mvc.perform(get("/api/meter-values/BORNE_A").param("connectorId", "1").param("last", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].measurand").value("Power.Active.Import"))
                .andExpect(jsonPath("$[0].value").value(7200.0));
    }

    @Test
    void returns404WhenChargePointMissing() throws Exception {
        when(service.findRecent(eq("UNKNOWN"), any(), any()))
                .thenThrow(new ChargePointNotFoundException("UNKNOWN"));
        mvc.perform(get("/api/meter-values/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Charge point not found: UNKNOWN"));
    }
}
