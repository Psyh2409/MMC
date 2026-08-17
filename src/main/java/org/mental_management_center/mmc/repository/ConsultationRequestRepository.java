package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.ConsultationRequest;
import org.mental_management_center.mmc.model.ConsultationRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, UUID> {

    List<ConsultationRequest> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<ConsultationRequest> findByTherapistIdOrderByCreatedAtDesc(UUID therapistId);

    @EntityGraph(attributePaths = {"client"})
    List<ConsultationRequest> findByTherapistIdAndStatusOrderByCreatedAtDesc(UUID therapistId, ConsultationRequestStatus status);

    boolean existsByClientIdAndTherapistIdAndStatus(UUID clientId, UUID therapistId, ConsultationRequestStatus status);
}