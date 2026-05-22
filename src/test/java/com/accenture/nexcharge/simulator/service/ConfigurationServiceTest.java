package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ChangeConfigurationResponseDto;
import com.accenture.nexcharge.simulator.model.dto.GetConfigurationResponseDto;
import com.accenture.nexcharge.simulator.ocpp.CsmsServer;
import eu.chargetime.ocpp.model.core.ChangeConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.ChangeConfigurationRequest;
import eu.chargetime.ocpp.model.core.ConfigurationStatus;
import eu.chargetime.ocpp.model.core.GetConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.GetConfigurationRequest;
import eu.chargetime.ocpp.model.core.KeyValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    CsmsServer csmsServer;
    @Mock
    LogService logService;

    ConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigurationService(csmsServer, logService);
    }

    // ── ChangeConfiguration ───────────────────────────────────────────────────

    @Test
    void changeConfigurationBuildsCorrectRequest() {
        ChangeConfigurationConfirmation conf =
                new ChangeConfigurationConfirmation(ConfigurationStatus.Accepted);
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(ChangeConfigurationRequest.class), anyLong()))
                .thenReturn(Optional.of(conf));

        ChangeConfigurationResponseDto response =
                service.changeConfiguration("BORNE_A", "HeartbeatInterval", "60");

        ArgumentCaptor<ChangeConfigurationRequest> captor =
                ArgumentCaptor.forClass(ChangeConfigurationRequest.class);
        verify(csmsServer).sendAndAwait(eq("BORNE_A"), captor.capture(), anyLong());

        ChangeConfigurationRequest sent = captor.getValue();
        assertThat(sent.getKey()).isEqualTo("HeartbeatInterval");
        assertThat(sent.getValue()).isEqualTo("60");
        assertThat(response.status()).isEqualTo("Accepted");
    }

    @Test
    void changeConfigurationReturnsRejectedWhenCpOffline() {
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(), anyLong()))
                .thenReturn(Optional.empty());

        ChangeConfigurationResponseDto response =
                service.changeConfiguration("BORNE_A", "HeartbeatInterval", "60");

        assertThat(response.status()).isEqualTo("Rejected");
    }

    @Test
    void changeConfigurationForwardsRebootRequired() {
        ChangeConfigurationConfirmation conf =
                new ChangeConfigurationConfirmation(ConfigurationStatus.RebootRequired);
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(), anyLong()))
                .thenReturn(Optional.of(conf));

        ChangeConfigurationResponseDto response =
                service.changeConfiguration("BORNE_A", "SomeKey", "value");

        assertThat(response.status()).isEqualTo("RebootRequired");
    }

    // ── GetConfiguration ─────────────────────────────────────────────────────

    @Test
    void getConfigurationBuildsRequestWithKeys() {
        GetConfigurationConfirmation conf = new GetConfigurationConfirmation();
        KeyValueType kv = new KeyValueType("HeartbeatInterval", false);
        kv.setValue("30");
        conf.setConfigurationKey(new KeyValueType[]{kv});
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(GetConfigurationRequest.class), anyLong()))
                .thenReturn(Optional.of(conf));

        GetConfigurationResponseDto response =
                service.getConfiguration("BORNE_A", List.of("HeartbeatInterval"));

        ArgumentCaptor<GetConfigurationRequest> captor =
                ArgumentCaptor.forClass(GetConfigurationRequest.class);
        verify(csmsServer).sendAndAwait(eq("BORNE_A"), captor.capture(), anyLong());

        assertThat(captor.getValue().getKey()).containsExactly("HeartbeatInterval");
        assertThat(response.configurationKey()).hasSize(1);
        assertThat(response.configurationKey().get(0).key()).isEqualTo("HeartbeatInterval");
        assertThat(response.configurationKey().get(0).value()).isEqualTo("30");
        assertThat(response.configurationKey().get(0).readonly()).isFalse();
    }

    @Test
    void getConfigurationBuildsRequestWithNullKeysForAll() {
        GetConfigurationConfirmation conf = new GetConfigurationConfirmation();
        conf.setConfigurationKey(new KeyValueType[0]);
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(GetConfigurationRequest.class), anyLong()))
                .thenReturn(Optional.of(conf));

        service.getConfiguration("BORNE_A", null);

        ArgumentCaptor<GetConfigurationRequest> captor =
                ArgumentCaptor.forClass(GetConfigurationRequest.class);
        verify(csmsServer).sendAndAwait(eq("BORNE_A"), captor.capture(), anyLong());

        // null keys = fetch all, so the request should carry no key array
        assertThat(captor.getValue().getKey()).isNull();
    }

    @Test
    void getConfigurationReturnsEmptyWhenCpOffline() {
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(), anyLong()))
                .thenReturn(Optional.empty());

        GetConfigurationResponseDto response = service.getConfiguration("BORNE_A", null);

        assertThat(response.configurationKey()).isEmpty();
        assertThat(response.unknownKey()).isEmpty();
    }

    @Test
    void getConfigurationMapsUnknownKeys() {
        GetConfigurationConfirmation conf = new GetConfigurationConfirmation();
        conf.setConfigurationKey(new KeyValueType[0]);
        conf.setUnknownKey(new String[]{"SomeUnknownKey"});
        when(csmsServer.sendAndAwait(eq("BORNE_A"), any(), anyLong()))
                .thenReturn(Optional.of(conf));

        GetConfigurationResponseDto response =
                service.getConfiguration("BORNE_A", List.of("SomeUnknownKey"));

        assertThat(response.unknownKey()).containsExactly("SomeUnknownKey");
    }
}
