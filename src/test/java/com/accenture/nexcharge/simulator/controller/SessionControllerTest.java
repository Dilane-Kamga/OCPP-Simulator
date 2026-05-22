package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.service.SessionNotFoundException;
import com.accenture.nexcharge.simulator.service.SessionService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
@Import(GlobalExceptionHandler.class)
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean SessionService service;

    @Test
    void searchActiveSessions() throws Exception {
        when(service.search(eq(SessionStatus.Active), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                new SessionDto(1L, 1001, "BORNE_A", 1, "RFID-001",
                        Instant.parse("2026-05-22T12:30:00Z"), null,
                        500000.0, null, 14.8, null, SessionStatus.Active, 120L)));

        mvc.perform(get("/api/sessions").param("status", "Active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(1001))
                .andExpect(jsonPath("$[0].durationMinutes").value(120));
    }

    @Test
    void getActiveShortcut() throws Exception {
        when(service.findActive()).thenReturn(List.of());
        mvc.perform(get("/api/sessions/active")).andExpect(status().isOk());
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(service.getById(99L)).thenThrow(new SessionNotFoundException(99L));
        mvc.perform(get("/api/sessions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Session not found: 99"));
    }

    @Test
    void searchWithInvalidStatusReturns400() throws Exception {
        mvc.perform(get("/api/sessions").param("status", "GARBAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status: GARBAGE"));
    }
}
