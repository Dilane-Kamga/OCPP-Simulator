package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService service;

    @GetMapping
    public List<OcppLogDto> search(
            @RequestParam(required = false) String chargePointId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LogDirection direction,
            @RequestParam(required = false) Integer last,
            @RequestParam(required = false) Integer limit) {
        return service.search(chargePointId, action, direction, last, limit);
    }
}
