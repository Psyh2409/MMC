package org.mental_management_center.mmc.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_management_center.mmc.model.Request;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID>{

    @EntityGraph(attributePaths = "user")
    List<Request> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    Optional<Request> findById(UUID id);

    void deleteByUserId(UUID userId);
}
