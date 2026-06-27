package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapyAssignmentRepository extends JpaRepository<TherapyAssignment, UUID> {

    // Щоб клієнт бачив свої запити/сесії
    List<TherapyAssignment> findByClientId(UUID clientId);

    // Щоб терапевт міг вивести список запитів (наприклад, тільки PENDING або ACTIVE)
    // Використовуємо JOIN FETCH, щоб одразу завантажити клієнтів (уникнемо LazyInitializationException)
    @Query("SELECT t FROM TherapyAssignment t JOIN FETCH t.client WHERE t.therapist.id = :therapistId AND t.status = :status")
    List<TherapyAssignment> findByTherapistIdAndStatus(@Param("therapistId") UUID therapistId, @Param("status") String status);

    // Щоб уникнути дублювання запитів від одного клієнта до одного терапевта
    Optional<TherapyAssignment> findByClientIdAndTherapistId(UUID clientId, UUID therapistId);
}