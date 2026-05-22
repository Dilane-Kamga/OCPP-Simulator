package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargePointServiceTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;

    @InjectMocks ChargePointService service;

    @Test
    void getAllReturnsDtoWithConnectors() {
        ChargePointEntity cp = ChargePointEntity.builder()
                .chargePointId("BORNE_A").vendor("Legrand")
                .model("Green'Up Premium").serialNumber("LGR-001")
                .firmwareVersion("1.4.2")
                .status(ChargePointStatus.Charging).online(true)
                .lastHeartbeat(Instant.now()).registeredAt(Instant.now())
                .errorCode("NoError").build();
        ConnectorEntity conn = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Charging)
                .currentPowerKw(7.2).voltage(230.0).currentAmps(31.0)
                .temperatureCelsius(38.0).totalEnergyKwh(14.5)
                .build();

        when(chargePointRepository.findAll()).thenReturn(List.of(cp));
        when(connectorRepository.findByChargePointIdOrderByConnectorIdAsc("BORNE_A"))
                .thenReturn(List.of(conn));

        List<ChargePointDto> result = service.getAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargePointId()).isEqualTo("BORNE_A");
        assertThat(result.get(0).connectors()).hasSize(1);
        assertThat(result.get(0).connectors().get(0).currentPowerKw()).isEqualTo(7.2);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(chargePointRepository.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("UNKNOWN"))
                .isInstanceOf(ChargePointNotFoundException.class);
    }

    @Test
    void getByIdReturnsDto() {
        ChargePointEntity cp = ChargePointEntity.builder()
                .chargePointId("BORNE_A").status(ChargePointStatus.Available).build();
        when(chargePointRepository.findById("BORNE_A")).thenReturn(Optional.of(cp));
        when(connectorRepository.findByChargePointIdOrderByConnectorIdAsc("BORNE_A"))
                .thenReturn(List.of());

        ChargePointDto dto = service.getById("BORNE_A");
        assertThat(dto.chargePointId()).isEqualTo("BORNE_A");
        assertThat(dto.connectors()).isEmpty();
    }
}
