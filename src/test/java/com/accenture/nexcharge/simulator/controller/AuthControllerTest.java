package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.entity.AuthorizedTagEntity;
import com.accenture.nexcharge.simulator.repository.AuthorizedTagRepository;
import com.accenture.nexcharge.simulator.service.TagNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthorizedTagRepository repository;

    // ------------------------------------------------------------------
    // GET /api/auth/tags
    // ------------------------------------------------------------------

    @Test
    void getAll_returnsList() throws Exception {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-0001")
                .blocked(false)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(repository.findAll()).thenReturn(List.of(tag));

        mvc.perform(get("/api/auth/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idTag").value("RFID-0001"))
                .andExpect(jsonPath("$[0].blocked").value(false));
    }

    // ------------------------------------------------------------------
    // GET /api/auth/tags/{idTag} — found
    // ------------------------------------------------------------------

    @Test
    void getOne_found_returnsDto() throws Exception {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-0042")
                .blocked(false)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(repository.findByIdTag("RFID-0042")).thenReturn(Optional.of(tag));

        mvc.perform(get("/api/auth/tags/RFID-0042"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTag").value("RFID-0042"));
    }

    // ------------------------------------------------------------------
    // GET /api/auth/tags/{idTag} — not found → 404
    // ------------------------------------------------------------------

    @Test
    void getOne_notFound_returns404() throws Exception {
        when(repository.findByIdTag("RFID-MISSING")).thenReturn(Optional.empty());

        mvc.perform(get("/api/auth/tags/RFID-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Authorized tag not found: RFID-MISSING"));
    }

    // ------------------------------------------------------------------
    // POST /api/auth/tags → 201 Created
    // ------------------------------------------------------------------

    @Test
    void create_newTag_returns201() throws Exception {
        when(repository.existsById("RFID-NEW")).thenReturn(false);
        AuthorizedTagEntity saved = AuthorizedTagEntity.builder()
                .idTag("RFID-NEW")
                .blocked(false)
                .createdAt(Instant.parse("2026-05-23T00:00:00Z"))
                .build();
        when(repository.save(any())).thenReturn(saved);

        mvc.perform(post("/api/auth/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTag\":\"RFID-NEW\",\"blocked\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTag").value("RFID-NEW"));
    }

    // ------------------------------------------------------------------
    // POST /api/auth/tags — conflict → 409
    // ------------------------------------------------------------------

    @Test
    void create_existingTag_returns409() throws Exception {
        when(repository.existsById("RFID-0001")).thenReturn(true);

        mvc.perform(post("/api/auth/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTag\":\"RFID-0001\",\"blocked\":false}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // POST /api/auth/tags — missing idTag → 400 validation error
    // ------------------------------------------------------------------

    @Test
    void create_blankIdTag_returns400() throws Exception {
        mvc.perform(post("/api/auth/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTag\":\"\",\"blocked\":false}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // PUT /api/auth/tags/{idTag} → 200
    // ------------------------------------------------------------------

    @Test
    void update_existingTag_returns200() throws Exception {
        AuthorizedTagEntity existing = AuthorizedTagEntity.builder()
                .idTag("RFID-0001")
                .blocked(false)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(repository.findByIdTag("RFID-0001")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        mvc.perform(put("/api/auth/tags/RFID-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTag\":\"RFID-0001\",\"blocked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTag").value("RFID-0001"));
    }

    // ------------------------------------------------------------------
    // PUT /api/auth/tags/{idTag} — not found → 404
    // ------------------------------------------------------------------

    @Test
    void update_missingTag_returns404() throws Exception {
        when(repository.findByIdTag("RFID-MISSING")).thenReturn(Optional.empty());

        mvc.perform(put("/api/auth/tags/RFID-MISSING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTag\":\"RFID-MISSING\",\"blocked\":false}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // DELETE /api/auth/tags/{idTag} → 204
    // ------------------------------------------------------------------

    @Test
    void delete_existingTag_returns204() throws Exception {
        when(repository.existsById("RFID-0001")).thenReturn(true);
        doNothing().when(repository).deleteById("RFID-0001");

        mvc.perform(delete("/api/auth/tags/RFID-0001"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById("RFID-0001");
    }

    // ------------------------------------------------------------------
    // DELETE /api/auth/tags/{idTag} — not found → 404
    // ------------------------------------------------------------------

    @Test
    void delete_missingTag_returns404() throws Exception {
        when(repository.existsById("RFID-MISSING")).thenReturn(false);

        mvc.perform(delete("/api/auth/tags/RFID-MISSING"))
                .andExpect(status().isNotFound());
    }
}
