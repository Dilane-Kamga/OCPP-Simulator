package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.service.MeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meter-values")
@RequiredArgsConstructor
public class MeterController {

    private final MeterService service;

    @GetMapping("/{chargePointId}")
    public List<MeterValueDto> get(
            @PathVariable String chargePointId,
            @RequestParam(required = false) Integer connectorId,
            @RequestParam(required = false) Integer last) {
        return service.findRecent(chargePointId, connectorId, last);
    }
}
