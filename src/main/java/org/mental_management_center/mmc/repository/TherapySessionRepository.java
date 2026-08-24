package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TherapySessionRepository extends JpaRepository<TherapySession, UUID> {

    // Витягуємо події фахівця за вказаний період (наприклад, з 1 по 31 число місяця)
    List<TherapySession> findByTherapistIdAndStartTimeBetween(UUID therapistId,
                                                              LocalDateTime start,
                                                              LocalDateTime end);

    // Те саме, але для календаря клієнта
    List<TherapySession> findByClientIdAndStartTimeBetween(UUID clientId,
                                                           LocalDateTime start,
                                                           LocalDateTime end);

    // Витягуємо всі сесії, де користувач є АБО терапевтом, АБО клієнтом
    @Query("SELECT s FROM TherapySession s WHERE " +
            "(s.therapist.id = :userId OR s.client.id = :userId) " +
            "AND s.startTime >= :start AND s.startTime <= :end")
    List<TherapySession> findSessionsForUser(@Param("userId") UUID userId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    // Перевіряємо, чи є накладки в розкладі (для терапевта або клієнта)
    @Query("SELECT COUNT(s) FROM TherapySession s " +
            "WHERE (s.therapist.id IN (:therapistId, :clientId) " +
            "OR s.client.id IN (:therapistId, :clientId)) " +
            "AND s.status = 'SCHEDULED' " +
            "AND s.startTime < :endTime AND s.endTime > :startTime")
    long countOverlappingSessions(@Param("therapistId") UUID therapistId,
                                  @Param("clientId") UUID clientId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

}

