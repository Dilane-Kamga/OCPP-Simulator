package com.accenture.nexcharge.simulator.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "meter_readings",
    indexes = {
        @Index(name = "idx_meter_cp_ts", columnList = "charge_point_id, timestamp"),
        @Index(name = "idx_meter_tx", columnList = "transaction_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_point_id", length = 50, nullable = false)
    private String chargePointId;

    @Column(name = "connector_id")
    private Integer connectorId;

    @Column(name = "transaction_id")
    private Integer transactionId;

    @Column(length = 100)
    private String measurand;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(length = 10)
    private String unit;

    @Column(nullable = false)
    private Instant timestamp;
}
