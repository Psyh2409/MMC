package org.mental_management_center.mmc.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_management_center.mmc.model.Request;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID>{

    @EntityGraph(attributePaths = "user")
    List<Request> findAllByOrderByCreatedAtDesc();

    // Сортування за пріоритетом (Нові зверху, потім за датою)
    @EntityGraph(attributePaths = "user")
    @Query("SELECT r FROM Request r ORDER BY CASE WHEN r.status = 'NEW' OR r.status IS NULL THEN 0 ELSE 1 END ASC, r.createdAt DESC")
    List<Request> findAllSortedByUrgency();

    // Сортування за ім'ям (в алфавітному порядку), а при збігу - за датою
    @EntityGraph(attributePaths = "user")
    List<Request> findAllByOrderByNameAscCreatedAtDesc();

    // Сортування за контактом (твоє головне поле)
    @EntityGraph(attributePaths = "user")
    List<Request> findAllByOrderByContactAscCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    Optional<Request> findById(UUID id);

    void deleteByUserId(UUID userId);
}
