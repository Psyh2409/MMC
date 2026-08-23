package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TherapySessionRepository extends JpaRepository<TherapySession, UUID> {

    // Витягуємо події фахівця за вказаний період (наприклад, з 1 по 31 число місяця)
    List<TherapySession> findByTherapistIdAndStartTimeBetween(UUID therapistId, LocalDateTime start, LocalDateTime end);

    // Те саме, але для календаря клієнта
    List<TherapySession> findByClientIdAndStartTimeBetween(UUID clientId, LocalDateTime start, LocalDateTime end);
}