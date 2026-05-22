package com.accenture.nexcharge.simulator.ocpp;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.ServerEvents;
import eu.chargetime.ocpp.feature.profile.ServerCoreProfile;
import eu.chargetime.ocpp.model.SessionInformation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsmsServer {

    private final OcppProperties ocppProperties;
    private final CsmsEventHandler eventHandler;
    private final OcppSessionRegistry sessionRegistry;
    private final LiveEventService liveEventService;
    private final ChargePointRepository chargePointRepository;

    private JSONServer server;

    @PostConstruct
    public void start() {
        ServerCoreProfile core = new ServerCoreProfile(eventHandler);
        server = new JSONServer(core);

        server.open(ocppProperties.server().host(), ocppProperties.server().port(), new ServerEvents() {
            @Override
            public void authenticateSession(SessionInformation info, String username, byte[] password) {
                // Simulator: accept all sessions (no credentials required)
            }

            @Override
            public void newSession(UUID sessionIndex, SessionInformation info) {
                String chargePointId = info.getIdentifier();
                if (chargePointId != null && chargePointId.startsWith("/")) {
                    chargePointId = chargePointId.substring(1);
                }
                if (chargePointId != null && chargePointId.startsWith("ocpp/")) {
                    chargePointId = chargePointId.substring("ocpp/".length());
                }

                log.info("[CSMS] New session: {} connected (sessionId={})", chargePointId, sessionIndex);
                sessionRegistry.register(chargePointId, sessionIndex);
            }

            @Override
            public void lostSession(UUID sessionIndex) {
                sessionRegistry.findChargePointId(sessionIndex).ifPresent(cpId -> {
                    log.info("[CSMS] Session lost: {}", cpId);
                    chargePointRepository.findById(cpId).ifPresent(cp -> {
                        cp.setOnline(false);
                        chargePointRepository.save(cp);
                    });
                    liveEventService.publish(LiveEventDto.of(
                            LiveEventType.CHARGE_POINT_DISCONNECTED, cpId, Map.of()));
                });
                sessionRegistry.unregisterBySessionId(sessionIndex);
            }
        });

        log.info("[CSMS] Server started on {}:{} (OCPP 1.6J)",
                ocppProperties.server().host(), ocppProperties.server().port());
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            try {
                server.close();
                log.info("[CSMS] Server stopped");
            } catch (Exception e) {
                log.warn("Error stopping CSMS server: {}", e.getMessage());
            }
        }
    }

    public boolean send(String chargePointId, eu.chargetime.ocpp.model.Request request) {
        return sessionRegistry.findSessionId(chargePointId)
                .map(sessionId -> {
                    try {
                        server.send(sessionId, request);
                        return true;
                    } catch (Exception e) {
                        log.warn("Failed to send {} to {}: {}",
                                request.getClass().getSimpleName(), chargePointId, e.getMessage());
                        return false;
                    }
                })
                .orElse(false);
    }
}
