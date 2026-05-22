package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.CommandResponse;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import com.accenture.nexcharge.simulator.simulator.SimulatorScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorScenarioService service;

    @PostMapping("/scenario")
    public CommandResponse runScenario(@Valid @RequestBody ScenarioRequest request) {
        service.run(request);
        return CommandResponse.accepted("Scenario " + request.scenario() + " executed");
    }
}
