package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.service.ChargePointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chargepoints")
@RequiredArgsConstructor
@Tag(name = "Charge Points", description = "Borne registry and connector state")
public class ChargePointController {

    private final ChargePointService service;

    @Operation(summary = "List all charge points with their connectors")
    @GetMapping
    public List<ChargePointDto> getAll() {
        return service.getAll();
    }

    @Operation(summary = "Get a single charge point by ID")
    @GetMapping("/{id}")
    public ChargePointDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @Operation(summary = "Get connectors for a given charge point")
    @GetMapping("/{id}/connectors")
    public List<ConnectorDto> getConnectors(@PathVariable String id) {
        return service.getConnectors(id);
    }
}
