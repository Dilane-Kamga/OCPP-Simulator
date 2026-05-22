package com.accenture.nexcharge.simulator.ocpp;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OcppSessionRegistry {

    private final ConcurrentMap<String, UUID> chargePointToSession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> sessionToChargePoint = new ConcurrentHashMap<>();

    public synchronized void register(String chargePointId, UUID sessionId) {
        UUID existing = chargePointToSession.get(chargePointId);
        if (existing != null) {
            sessionToChargePoint.remove(existing);
        }
        chargePointToSession.put(chargePointId, sessionId);
        sessionToChargePoint.put(sessionId, chargePointId);
    }

    public synchronized void unregisterBySessionId(UUID sessionId) {
        String chargePointId = sessionToChargePoint.remove(sessionId);
        if (chargePointId != null) {
            chargePointToSession.remove(chargePointId, sessionId);
        }
    }

    public Optional<UUID> findSessionId(String chargePointId) {
        return Optional.ofNullable(chargePointToSession.get(chargePointId));
    }

    public Optional<String> findChargePointId(UUID sessionId) {
        return Optional.ofNullable(sessionToChargePoint.get(sessionId));
    }
}
