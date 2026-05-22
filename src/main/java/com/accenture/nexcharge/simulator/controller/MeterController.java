package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.service.MeterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meter-values")
@RequiredArgsConstructor
@Validated
@Tag(name = "Meter Values", description = "Time-series meter readings")
public class MeterController {

    private final MeterService service;

    @Operation(summary = "Get meter readings for a charge point (optionally filtered by connector and time window)")
    @GetMapping("/{chargePointId}")
    public List<MeterValueDto> get(
            @PathVariable String chargePointId,
            @RequestParam(required = false) Integer connectorId,
            @RequestParam(required = false) Integer last,
            @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(1000) int limit,
            @RequestParam(required = false, defaultValue = "0") @Min(0) int offset) {
        return service.findRecent(chargePointId, connectorId, last, limit, offset);
    }
}
