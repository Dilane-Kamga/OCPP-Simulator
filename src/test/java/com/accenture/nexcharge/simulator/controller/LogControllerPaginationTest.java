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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@code limit} / {@code offset} pagination params on {@link LogController}.
 */
@WebMvcTest(LogController.class)
@Import(GlobalExceptionHandler.class)
class LogControllerPaginationTest {

    @Autowired MockMvc mvc;
    @MockBean LogService service;

    private static OcppLogDto dto(long id) {
        return new OcppLogDto(id, "BORNE_A", LogDirection.IN, "Heartbeat", "{}", Instant.now());
    }

    @Test
    void limitTwoReturnsTwoEntries() throws Exception {
        when(service.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(dto(1), dto(2)));

        mvc.perform(get("/api/logs").param("limit", "2").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void limitZeroReturns400() throws Exception {
        mvc.perform(get("/api/logs").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitTenThousandReturns400() throws Exception {
        mvc.perform(get("/api/logs").param("limit", "10000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeOffsetReturns400() throws Exception {
        mvc.perform(get("/api/logs").param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultsWork() throws Exception {
        when(service.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mvc.perform(get("/api/logs"))
                .andExpect(status().isOk());
    }
}
