package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "charging_sessions",
    indexes = {
        @Index(name = "idx_session_status", columnList = "status"),
        @Index(name = "idx_session_cp", columnList = "charge_point_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private Integer transactionId;

    @Column(name = "charge_point_id", length = 50, nullable = false)
    private String chargePointId;

    @Column(name = "connector_id", nullable = false)
    private Integer connectorId;

    @Column(name = "id_tag", length = 50)
    private String idTag;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "stop_time")
    private Instant stopTime;

    @Column(name = "meter_start_wh")
    private Double meterStartWh;

    @Column(name = "meter_stop_wh")
    private Double meterStopWh;

    @Column(name = "energy_delivered_kwh")
    private Double energyDeliveredKwh;

    @Column(name = "stop_reason", length = 50)
    private String stopReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SessionStatus status;
}
