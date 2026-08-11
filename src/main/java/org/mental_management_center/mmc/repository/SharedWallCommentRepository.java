package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.SharedWallComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SharedWallCommentRepository extends JpaRepository<SharedWallComment, UUID> {

    // Порційне отримання коментарів для конкретного поста (від новіших до старіших)
    Slice<SharedWallComment> findByWallEntryIdOrderByCreatedAtDesc(UUID wallEntryId, Pageable pageable);

    // Швидкий лічильник для кнопки на стіні
    long countByWallEntryId(UUID wallEntryId);

    // 🟢 Отримання ВСІХ коментарів поста в правильному хронологічному порядку для дерева
    List<SharedWallComment> findByWallEntryIdOrderByCreatedAtAsc(UUID wallEntryId);
}