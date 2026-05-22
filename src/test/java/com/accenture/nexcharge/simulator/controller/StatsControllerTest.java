package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
@Import(GlobalExceptionHandler.class)
class StatsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean StatsService service;

    @Test
    void returnsStats() throws Exception {
        when(service.compute()).thenReturn(new StatsDto(
                5, 4, 2, 2, 1, 0, 2, 29.2, 63.6, 8, 6, 95L, 18.5));
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChargePoints").value(5))
                .andExpect(jsonPath("$.totalPowerKw").value(29.2));
    }
}
