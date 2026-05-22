package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.*;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.simulator.ChargePointSimulator;
import com.accenture.nexcharge.simulator.simulator.SimulatorManager;
import com.accenture.nexcharge.simulator.simulator.SimulatorState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chargepoints/{id}")
@RequiredArgsConstructor
public class RemoteCommandController {

    private final SimulatorManager manager;

    @PostMapping("/remote-start")
    public CommandResponse remoteStart(@PathVariable String id, @Valid @RequestBody RemoteStartRequest req) {
        ChargePointSimulator s = require(id);
        s.startSession(req.connectorId(), req.idTag());
        return CommandResponse.accepted("RemoteStart sent to " + id);
    }

    @PostMapping("/remote-stop")
    public CommandResponse remoteStop(@PathVariable String id, @Valid @RequestBody RemoteStopRequest req) {
        ChargePointSimulator s = require(id);
        if (s.getState() != SimulatorState.CHARGING) {
            return CommandResponse.rejected("Charge point not charging");
        }
        s.stopSession("Remote");
        return CommandResponse.accepted("RemoteStop sent to " + id);
    }

    @PostMapping("/reset")
    public CommandResponse reset(@PathVariable String id, @Valid @RequestBody ResetRequest req) {
        ChargePointSimulator s = require(id);
        s.reset();
        return CommandResponse.accepted("Reset (" + req.type() + ") sent to " + id);
    }

    @PostMapping("/unlock")
    public CommandResponse unlock(@PathVariable String id, @Valid @RequestBody UnlockRequest req) {
        ChargePointSimulator s = require(id);
        if (s.getState() == SimulatorState.CHARGING) {
            s.stopSession("UnlockCommand");
        }
        return CommandResponse.accepted("Unlock connector " + req.connectorId() + " on " + id);
    }

    private ChargePointSimulator require(String id) {
        ChargePointSimulator s = manager.get(id);
        if (s == null) throw new ChargePointNotFoundException(id);
        return s;
    }
}
