package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorBlockServiceTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;

    @InjectMocks ChargePointService service;

    @Test
    void blockConnector_setsFieldsAndPersists() {
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available).blocked(false).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        ConnectorDto dto = service.blockConnector("BORNE_A", 1, "Quarterly maintenance");

        ArgumentCaptor<ConnectorEntity> captor = ArgumentCaptor.forClass(ConnectorEntity.class);
        verify(connectorRepository).save(captor.capture());
        ConnectorEntity saved = captor.getValue();
        assertThat(saved.getBlocked()).isTrue();
        assertThat(saved.getBlockedReason()).isEqualTo("Quarterly maintenance");
        assertThat(saved.getBlockedAt()).isNotNull();

        assertThat(dto.blocked()).isTrue();
        assertThat(dto.blockedReason()).isEqualTo("Quarterly maintenance");
        assertThat(dto.blockedAt()).isNotNull();
    }

    @Test
    void unblockConnector_clearsAllFields() {
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Charging)
                .blocked(true).blockedReason("Maintenance").blockedAt(Instant.now()).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        ConnectorDto dto = service.unblockConnector("BORNE_A", 1);

        ArgumentCaptor<ConnectorEntity> captor = ArgumentCaptor.forClass(ConnectorEntity.class);
        verify(connectorRepository).save(captor.capture());
        ConnectorEntity saved = captor.getValue();
        assertThat(saved.getBlocked()).isFalse();
        assertThat(saved.getBlockedReason()).isNull();
        assertThat(saved.getBlockedAt()).isNull();

        assertThat(dto.blocked()).isFalse();
        assertThat(dto.blockedReason()).isNull();
        assertThat(dto.blockedAt()).isNull();
    }

    @Test
    void blockConnector_doubleBlock_updatesReasonAndTimestamp() {
        Instant firstBlockedAt = Instant.now().minusSeconds(3600);
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available)
                .blocked(true).blockedReason("Old reason").blockedAt(firstBlockedAt).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        ConnectorDto dto = service.blockConnector("BORNE_A", 1, "New reason");

        assertThat(dto.blockedReason()).isEqualTo("New reason");
        assertThat(dto.blockedAt()).isAfterOrEqualTo(firstBlockedAt);
    }

    @Test
    void unblockWhenNotBlocked_isNoOp() {
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available).blocked(false).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        ConnectorDto dto = service.unblockConnector("BORNE_A", 1);

        assertThat(dto.blocked()).isFalse();
        assertThat(dto.blockedReason()).isNull();
        assertThat(dto.blockedAt()).isNull();
    }

    @Test
    void blockConnector_throws404WhenConnectorMissing() {
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_Z", 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.blockConnector("BORNE_Z", 1, "reason"))
                .isInstanceOf(ConnectorNotFoundException.class)
                .hasMessageContaining("BORNE_Z");
    }

    @Test
    void unblockConnector_throws404WhenConnectorMissing() {
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_Z", 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unblockConnector("BORNE_Z", 1))
                .isInstanceOf(ConnectorNotFoundException.class)
                .hasMessageContaining("BORNE_Z");
    }
}
