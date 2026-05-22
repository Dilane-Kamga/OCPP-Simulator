package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.service.LogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogController.class)
@Import(GlobalExceptionHandler.class)
class LogControllerTest {

    @Autowired MockMvc mvc;
    @MockBean LogService service;

    @Test
    void searchByFilters() throws Exception {
        when(service.search(eq("BORNE_A"), eq("Heartbeat"), eq(LogDirection.IN), eq(60), eq(20), eq(0)))
                .thenReturn(List.of(new OcppLogDto(42L, "BORNE_A", LogDirection.IN, "Heartbeat",
                        "{}", Instant.parse("2026-05-22T14:30:10Z"))));
        mvc.perform(get("/api/logs")
                        .param("chargePointId", "BORNE_A")
                        .param("action", "Heartbeat")
                        .param("direction", "IN")
                        .param("last", "60")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("Heartbeat"));
    }
}
