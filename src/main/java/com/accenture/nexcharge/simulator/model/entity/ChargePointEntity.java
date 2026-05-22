package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "charge_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargePointEntity {

    @Id
    @Column(name = "charge_point_id", length = 50)
    private String chargePointId;

    private String vendor;
    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChargePointStatus status;

    private boolean online;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;
}
