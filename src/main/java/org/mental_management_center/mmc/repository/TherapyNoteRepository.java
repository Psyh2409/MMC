package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapyNoteRepository extends JpaRepository<TherapyNote, UUID> {

    // Для кімнати: знайти останню нотатку автора щодо конкретного клієнта і терапевта
    Optional<TherapyNote> findTopByClientIdAndTherapistIdAndAuthorIdOrderByCreatedAtDesc(
            UUID clientId, UUID therapistId, UUID authorId);

    // Для профайлу: дістати всі нотатки, які написав КОНКРЕТНИЙ АВТОР
    @EntityGraph(attributePaths = {"client", "therapist"})
    List<TherapyNote> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    // Наш новий метод репозиторію, який очікує пагінацію
    @EntityGraph(attributePaths = {"therapist", "client"})
    Page<TherapyNote> findByAuthorId(UUID authorId, Pageable pageable);
}