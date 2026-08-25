package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.PublicPost;
import org.mental_management_center.mmc.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublicPostRepository extends JpaRepository<PublicPost, UUID> {
    // Для виведення постів конкретного фахівця на його візитці/в кабінеті
    Page<PublicPost> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    // На майбутнє: для головної сторінки (загальна стрічка всіх фахівців)
    Page<PublicPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PublicPost> findByAuthorOrderByCreatedAtDesc(User author);

    // Підрахунок використання файлу серед усіх публічних дописів
    long countByMediaFileName(String mediaFileName);
}