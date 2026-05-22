package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.*;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.ConfigurationService;
import com.accenture.nexcharge.simulator.simulator.ChargePointSimulator;
import com.accenture.nexcharge.simulator.simulator.SimulatorManager;
import com.accenture.nexcharge.simulator.simulator.SimulatorState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chargepoints/{id}")
@RequiredArgsConstructor
@Tag(name = "Remote Commands", description = "CSMS-initiated commands to bornes")
public class RemoteCommandController {

    private final SimulatorManager manager;
    private final ConfigurationService configurationService;

    @Operation(summary = "Send a RemoteStartTransaction command to a charge point")
    @PostMapping("/remote-start")
    public CommandResponse remoteStart(@PathVariable String id, @Valid @RequestBody RemoteStartRequest req) {
        ChargePointSimulator s = require(id);
        s.startSession(req.connectorId(), req.idTag());
        return CommandResponse.accepted("RemoteStart sent to " + id);
    }

    @Operation(summary = "Send a RemoteStopTransaction command to a charge point")
    @PostMapping("/remote-stop")
    public CommandResponse remoteStop(@PathVariable String id, @Valid @RequestBody RemoteStopRequest req) {
        ChargePointSimulator s = require(id);
        if (s.getState() != SimulatorState.CHARGING) {
            return CommandResponse.rejected("Charge point not charging");
        }
        s.stopSession("Remote");
        return CommandResponse.accepted("RemoteStop sent to " + id);
    }

    @Operation(summary = "Send a Reset (Soft or Hard) command to a charge point")
    @PostMapping("/reset")
    public CommandResponse reset(@PathVariable String id, @Valid @RequestBody ResetRequest req) {
        ChargePointSimulator s = require(id);
        s.reset();
        return CommandResponse.accepted("Reset (" + req.type() + ") sent to " + id);
    }

    @Operation(summary = "Send an UnlockConnector command to a charge point")
    @PostMapping("/unlock")
    public CommandResponse unlock(@PathVariable String id, @Valid @RequestBody UnlockRequest req) {
        ChargePointSimulator s = require(id);
        if (s.getState() == SimulatorState.CHARGING) {
            s.stopSession("UnlockCommand");
        }
        return CommandResponse.accepted("Unlock connector " + req.connectorId() + " on " + id);
    }

    /**
     * POST /api/chargepoints/{id}/change-configuration
     *
     * <p>Sends an OCPP {@code ChangeConfiguration} to the charge point and returns the
     * CP's status (Accepted / Rejected / RebootRequired / NotSupported).
     */
    @Operation(summary = "Send a ChangeConfiguration command to update a charge point configuration key")
    @PostMapping("/change-configuration")
    public ChangeConfigurationResponseDto changeConfiguration(
            @PathVariable String id,
            @Valid @RequestBody ChangeConfigurationRequestDto req) {
        require(id); // 404 if unknown CP
        return configurationService.changeConfiguration(id, req.key(), req.value());
    }

    /**
     * POST /api/chargepoints/{id}/get-configuration
     *
     * <p>Sends an OCPP {@code GetConfiguration} to the charge point.
     * {@code keys} may be null or empty to request all known configuration keys.
     */
    @Operation(summary = "Send a GetConfiguration command to read charge point configuration keys")
    @PostMapping("/get-configuration")
    public GetConfigurationResponseDto getConfiguration(
            @PathVariable String id,
            @RequestBody(required = false) GetConfigurationRequestDto req) {
        require(id); // 404 if unknown CP
        java.util.List<String> keys = (req != null) ? req.keys() : null;
        return configurationService.getConfiguration(id, keys);
    }

    private ChargePointSimulator require(String id) {
        ChargePointSimulator s = manager.get(id);
        if (s == null) throw new ChargePointNotFoundException(id);
        return s;
    }
}
