package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "ocpp_logs",
    indexes = {
        @Index(name = "idx_log_cp_ts", columnList = "charge_point_id, timestamp"),
        @Index(name = "idx_log_action", columnList = "action")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcppLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_point_id", length = 50)
    private String chargePointId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LogDirection direction;

    @Column(length = 50)
    private String action;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant timestamp;
}
