package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "connectors",
    uniqueConstraints = @UniqueConstraint(columnNames = {"charge_point_id", "connector_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_point_id", length = 50, nullable = false)
    private String chargePointId;

    @Column(name = "connector_id", nullable = false)
    private Integer connectorId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConnectorStatus status;

    @Column(name = "current_power_kw")
    private Double currentPowerKw;

    @Column(name = "current_amps")
    private Double currentAmps;

    private Double voltage;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "total_energy_kwh")
    private Double totalEnergyKwh;

    @Column(name = "error_code", length = 50)
    private String errorCode;
}
