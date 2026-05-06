package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapyNoteRepository extends JpaRepository<TherapyNote, UUID> {
    // Знайти всі нотатки по конкретному клієнту (для історії)
    List<TherapyNote> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    // Знайти останню нотатку, створену сьогодні (щоб не плодити записи кожну секунду)
    Optional<TherapyNote> findTopByClientIdAndTherapistIdOrderByCreatedAtDesc(UUID clientId, UUID therapistId);
}