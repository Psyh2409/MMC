package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.JournalPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalPostRepository extends JpaRepository<JournalPost, UUID> {

    // Знаходимо всі пости конкретного користувача, сортуючи від найновіших до найстаріших
    List<JournalPost> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndMediaFileName(UUID userId, String mediaFileName);
}