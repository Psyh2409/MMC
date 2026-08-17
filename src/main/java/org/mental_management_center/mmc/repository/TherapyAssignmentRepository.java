package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapyAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapyAssignmentRepository extends JpaRepository<TherapyAssignment, UUID> {

    @EntityGraph(attributePaths = {"client", "therapist"})
    Optional<TherapyAssignment> findById(UUID id);

    // Щоб клієнт бачив свої запити/сесії
    List<TherapyAssignment> findByClientId(UUID clientId);

    // Щоб терапевт міг вивести список запитів (наприклад, тільки PENDING або ACTIVE)
    // Використовуємо JOIN FETCH, щоб одразу завантажити клієнтів (уникнемо LazyInitializationException)
//    @Query("SELECT t FROM TherapyAssignment t JOIN FETCH t.client WHERE t.therapist.id = :therapistId AND t.status = :status")
    @Query("SELECT t FROM TherapyAssignment t " +
            "JOIN FETCH t.client " +
            "LEFT JOIN FETCH t.approvedByTherapist " +
            "WHERE t.therapist.id = :therapistId AND t.status = :status")
    List<TherapyAssignment> findByTherapistIdAndStatus(@Param("therapistId") UUID therapistId, @Param("status") String status);

    // Щоб уникнути дублювання запитів від одного клієнта до одного терапевта
    Optional<TherapyAssignment> findByClientIdAndTherapistId(UUID clientId, UUID therapistId);

    // Windsurf: Знаходимо активні призначення клієнта (для отримання його терапевтів)
    @Query("SELECT t FROM TherapyAssignment t " +
            "JOIN FETCH t.therapist " +
            "WHERE t.client.id = :clientId AND t.status = 'ACTIVE'")
    List<TherapyAssignment> findActiveByClientId(@Param("clientId") UUID clientId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TherapyAssignment t WHERE t.client.id = :userId OR t.therapist.id = :userId")
    void deleteAllAssignmentsRelatedToUser(@Param("userId") UUID userId);

    List<TherapyAssignment> findByStatus(String status);

    List<TherapyAssignment> findByClientIdAndStatus(UUID clientId, String status);
}