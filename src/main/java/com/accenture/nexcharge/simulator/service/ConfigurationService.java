package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ChangeConfigurationResponseDto;
import com.accenture.nexcharge.simulator.model.dto.ConfigurationKeyDto;
import com.accenture.nexcharge.simulator.model.dto.GetConfigurationResponseDto;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.ocpp.CsmsServer;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.core.ChangeConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.ChangeConfigurationRequest;
import eu.chargetime.ocpp.model.core.GetConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.GetConfigurationRequest;
import eu.chargetime.ocpp.model.core.KeyValueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Handles outbound CSMS→CP {@code ChangeConfiguration} and {@code GetConfiguration} commands
 * (OCPP 1.6J §6.3 / §6.6).
 *
 * <p>Both commands are sent synchronously via {@link CsmsServer#sendAndAwait} with a 5-second
 * timeout. If the CP is offline or the timeout elapses the method returns a synthetic
 * "Rejected / empty" response so the REST caller gets a deterministic answer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {

    /** Maximum seconds to wait for the CP's confirmation before returning a synthetic response. */
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final CsmsServer csmsServer;
    private final LogService logService;

    /**
     * Send a {@code ChangeConfiguration} to the named charge point and return the CP's status.
     *
     * @param chargePointId target CP identifier
     * @param key           configuration key (max 50 chars)
     * @param value         new configuration value (max 500 chars)
     * @return response DTO whose {@code status} is one of Accepted / Rejected /
     *         RebootRequired / NotSupported (or "Rejected" when the CP is unreachable)
     * @throws ChargePointNotFoundException if no simulator is registered for {@code chargePointId}
     */
    public ChangeConfigurationResponseDto changeConfiguration(String chargePointId,
                                                              String key,
                                                              String value) {
        log.info("[CSMS→{}] ChangeConfiguration key={} value={}", chargePointId, key, value);
        ChangeConfigurationRequest request = new ChangeConfigurationRequest(key, value);

        logService.log(chargePointId, LogDirection.OUT, "ChangeConfiguration",
                String.format("{\"key\":\"%s\",\"value\":\"%s\"}", key, value));

        Optional<Confirmation> confirmation = csmsServer.sendAndAwait(
                chargePointId, request, SEND_TIMEOUT_SECONDS);

        if (confirmation.isEmpty()) {
            log.warn("[CSMS→{}] ChangeConfiguration: no confirmation (CP offline or timeout)", chargePointId);
            return new ChangeConfigurationResponseDto("Rejected");
        }

        ChangeConfigurationConfirmation conf = (ChangeConfigurationConfirmation) confirmation.get();
        String status = conf.getStatus() != null ? conf.getStatus().name() : "Rejected";
        log.info("[CSMS→{}] ChangeConfiguration confirmed status={}", chargePointId, status);
        return new ChangeConfigurationResponseDto(status);
    }

    /**
     * Send a {@code GetConfiguration} to the named charge point and return its known keys.
     *
     * @param chargePointId target CP identifier
     * @param keys          list of keys to fetch, or null/empty to fetch all
     * @return response DTO mirroring OCPP's {@code GetConfigurationConfirmation}
     * @throws ChargePointNotFoundException if no simulator is registered for {@code chargePointId}
     */
    public GetConfigurationResponseDto getConfiguration(String chargePointId, List<String> keys) {
        log.info("[CSMS→{}] GetConfiguration keys={}", chargePointId, keys);
        GetConfigurationRequest request = new GetConfigurationRequest();
        if (keys != null && !keys.isEmpty()) {
            request.setKey(keys.toArray(String[]::new));
        }

        String keysJson = (keys == null || keys.isEmpty()) ? "[]"
                : "[\"" + String.join("\",\"", keys) + "\"]";
        logService.log(chargePointId, LogDirection.OUT, "GetConfiguration",
                String.format("{\"key\":%s}", keysJson));

        Optional<Confirmation> confirmation = csmsServer.sendAndAwait(
                chargePointId, request, SEND_TIMEOUT_SECONDS);

        if (confirmation.isEmpty()) {
            log.warn("[CSMS→{}] GetConfiguration: no confirmation (CP offline or timeout)", chargePointId);
            return new GetConfigurationResponseDto(Collections.emptyList(), Collections.emptyList());
        }

        GetConfigurationConfirmation conf = (GetConfigurationConfirmation) confirmation.get();
        List<ConfigurationKeyDto> knownKeys = toKnownKeyDtos(conf.getConfigurationKey());
        List<String> unknownKeys = toUnknownKeyList(conf.getUnknownKey());
        log.info("[CSMS→{}] GetConfiguration: {} known, {} unknown",
                chargePointId, knownKeys.size(), unknownKeys.size());
        return new GetConfigurationResponseDto(knownKeys, unknownKeys);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private List<ConfigurationKeyDto> toKnownKeyDtos(KeyValueType[] keyValueTypes) {
        if (keyValueTypes == null) return Collections.emptyList();
        return Arrays.stream(keyValueTypes)
                .map(kv -> new ConfigurationKeyDto(
                        kv.getKey(),
                        Boolean.TRUE.equals(kv.getReadonly()),
                        kv.getValue()))
                .toList();
    }

    private List<String> toUnknownKeyList(String[] unknownKeys) {
        if (unknownKeys == null) return Collections.emptyList();
        return Arrays.asList(unknownKeys);
    }
}
