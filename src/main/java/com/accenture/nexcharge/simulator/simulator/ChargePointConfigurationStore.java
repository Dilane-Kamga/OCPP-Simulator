package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import eu.chargetime.ocpp.model.core.KeyValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory configuration store for a single simulated charge point.
 *
 * <p>Pre-populated with two standard OCPP keys:
 * <ul>
 *   <li>{@code HeartbeatInterval} — integer seconds between heartbeats</li>
 *   <li>{@code MeterValueSampleInterval} — integer seconds between MeterValues</li>
 * </ul>
 * Both are writable (readonly=false). Any other key submitted via
 * {@code ChangeConfiguration} is considered unknown and returns {@code Rejected}.
 *
 * <p>Setting a key mutates the local copy; the simulator's scheduling picks up the
 * value lazily on the next tick (best-effort: the live scheduler wires to
 * {@link SimulatorProperties}, not directly to this store, so the effective behaviour
 * is that the new value is stored and readable via GetConfiguration immediately).
 */
public class ChargePointConfigurationStore implements SimulatorClientHandler.ConfigurationStore {

    /** OCPP standard key names we support. */
    public static final String KEY_HEARTBEAT_INTERVAL = "HeartbeatInterval";
    public static final String KEY_METER_VALUE_SAMPLE_INTERVAL = "MeterValueSampleInterval";

    /** Ordered list of supported key names (determines GetConfiguration response order). */
    private static final List<String> SUPPORTED_KEYS = List.of(
            KEY_HEARTBEAT_INTERVAL,
            KEY_METER_VALUE_SAMPLE_INTERVAL
    );

    /** Mutable values map; keys are the OCPP configuration key names. */
    private final Map<String, String> values = new ConcurrentHashMap<>();

    /**
     * Create and pre-populate a store from the global simulator properties.
     *
     * @param properties the {@link SimulatorProperties} supplying default values
     */
    public ChargePointConfigurationStore(SimulatorProperties properties) {
        values.put(KEY_HEARTBEAT_INTERVAL,
                String.valueOf(properties.heartbeatIntervalSeconds()));
        values.put(KEY_METER_VALUE_SAMPLE_INTERVAL,
                String.valueOf(properties.meterIntervalSeconds()));
    }

    /**
     * Package-visible constructor for tests that supply explicit defaults.
     *
     * @param heartbeatInterval       initial HeartbeatInterval value (seconds)
     * @param meterValueSampleInterval initial MeterValueSampleInterval value (seconds)
     */
    ChargePointConfigurationStore(int heartbeatInterval, int meterValueSampleInterval) {
        values.put(KEY_HEARTBEAT_INTERVAL, String.valueOf(heartbeatInterval));
        values.put(KEY_METER_VALUE_SAMPLE_INTERVAL, String.valueOf(meterValueSampleInterval));
    }

    @Override
    public boolean set(String key, String value) {
        if (!SUPPORTED_KEYS.contains(key)) {
            return false;
        }
        values.put(key, value);
        return true;
    }

    @Override
    public List<KeyValueType> get(List<String> requestedKeys) {
        List<String> keysToReturn = (requestedKeys == null || requestedKeys.isEmpty())
                ? SUPPORTED_KEYS
                : requestedKeys.stream().filter(SUPPORTED_KEYS::contains).toList();

        List<KeyValueType> result = new ArrayList<>();
        for (String key : keysToReturn) {
            String val = values.get(key);
            if (val != null) {
                KeyValueType kv = new KeyValueType(key, false);
                kv.setValue(val);
                result.add(kv);
            }
        }
        return result;
    }

    /** Expose the current value of a key for test assertions or future scheduler integration. */
    public String getValue(String key) {
        return values.get(key);
    }
}
