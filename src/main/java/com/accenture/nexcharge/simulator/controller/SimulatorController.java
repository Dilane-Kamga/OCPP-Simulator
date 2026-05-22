package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.CommandResponse;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import com.accenture.nexcharge.simulator.simulator.SimulatorScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
@Tag(name = "Simulator Scenarios", description = "Manual fault/load injection")
public class SimulatorController {

    private final SimulatorScenarioService service;

    @Operation(summary = "Trigger a named simulation scenario (START_ALL, STOP_ALL, FAULT_ONE, PEAK_LOAD, RESET_ALL, DISCONNECT_ONE)")
    @PostMapping("/scenario")
    public CommandResponse runScenario(@Valid @RequestBody ScenarioRequest request) {
        service.run(request);
        return CommandResponse.accepted("Scenario " + request.scenario() + " executed");
    }
}
