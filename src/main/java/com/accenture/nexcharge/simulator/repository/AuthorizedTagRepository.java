package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.AuthorizedTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizedTagRepository extends JpaRepository<AuthorizedTagEntity, String> {

    /** Readable alias for {@link #findById(Object)}. */
    Optional<AuthorizedTagEntity> findByIdTag(String idTag);
}
