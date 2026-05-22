package com.accenture.nexcharge.simulator.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "authorized_tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizedTagEntity {

    /** RFID tag identifier — primary key (e.g. "RFID-0042"). */
    @Id
    @Column(name = "id_tag", length = 50)
    private String idTag;

    /** Optional parent tag for group authorisation. */
    @Column(name = "parent_id_tag", length = 50)
    private String parentIdTag;

    /** Null means no expiry. */
    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When true the CSMS will always reject this tag. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean blocked = false;
}
