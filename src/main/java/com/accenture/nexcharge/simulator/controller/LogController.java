package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Validated
@Tag(name = "OCPP Logs", description = "Raw OCPP frame audit trail")
public class LogController {

    private final LogService service;

    @Operation(summary = "Search OCPP message logs (filterable by charge point, action, direction, and time window)")
    @GetMapping
    public List<OcppLogDto> search(
            @RequestParam(required = false) String chargePointId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LogDirection direction,
            @RequestParam(required = false) Integer last,
            @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(1000) int limit,
            @RequestParam(required = false, defaultValue = "0") @Min(0) int offset) {
        return service.search(chargePointId, action, direction, last, limit, offset);
    }
}
