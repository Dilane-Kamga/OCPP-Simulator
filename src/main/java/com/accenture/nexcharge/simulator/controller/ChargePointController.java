package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.service.ChargePointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chargepoints")
@RequiredArgsConstructor
public class ChargePointController {

    private final ChargePointService service;

    @GetMapping
    public List<ChargePointDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ChargePointDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/connectors")
    public List<ConnectorDto> getConnectors(@PathVariable String id) {
        return service.getConnectors(id);
    }
}
