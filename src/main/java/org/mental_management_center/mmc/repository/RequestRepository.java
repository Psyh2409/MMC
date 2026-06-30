package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_management_center.mmc.model.Request;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID>{

    @EntityGraph(attributePaths = "user")
    List<Request> findByRecipientIsNullOrderByCreatedAtDesc();

    // Сортування за пріоритетом (Нові зверху, потім за датою)
    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM Request r WHERE r.recipient IS NULL ORDER BY CASE WHEN r.status = 'NEW' OR r.status IS NULL THEN 0 ELSE 1 END ASC, r.createdAt DESC")
    List<Request> findByRecipientIsNullSortedByUrgency();

    // Сортування за ім'ям (в алфавітному порядку), а при збігу - за датою
    @EntityGraph(attributePaths = "user")
    List<Request> findByRecipientIsNullOrderByNameAscCreatedAtDesc();

    // Сортування за контактом (твоє головне поле)
    @EntityGraph(attributePaths = "user")
    List<Request> findByRecipientIsNullOrderByContactAscCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    Optional<Request> findById(UUID id);

    void deleteByUserId(UUID userId);

    // Отримати всі звернення для конкретного фахівця
    List<Request> findByRecipientOrderByCreatedAtDesc(User recipient);

    // Отримати тільки листи для адміністрації (де немає конкретного фахівця)
    List<Request> findByRecipientIsNull(org.springframework.data.domain.Sort sort);

}
