package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.PublicPost;
import org.mental_management_center.mmc.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PublicPostRepository extends JpaRepository<PublicPost, UUID> {
    // Для виведення постів конкретного фахівця на його візитці/в кабінеті
    Page<PublicPost> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    // На майбутнє: для головної сторінки (загальна стрічка всіх фахівців)
    Page<PublicPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PublicPost> findByAuthorOrderByCreatedAtDesc(User author);

    // Реальні дописи: 128-й біт НЕ встановлено (BITAND повертає 0)
    @Query("SELECT p FROM PublicPost p JOIN FETCH p.author a WHERE FUNCTION(" +
            "'BITAND', a.rolesMask, 128) = 0 ORDER BY p.createdAt DESC")
    Slice<PublicPost> findLatestRealPosts(Pageable pageable);

    // Тестові дописи: 128-й біт встановлено (BITAND повертає 128)
    @Query("SELECT p FROM PublicPost p JOIN FETCH p.author a WHERE FUNCTION(" +
            "'BITAND', a.rolesMask, 128) = 128 ORDER BY p.createdAt DESC")
    Slice<PublicPost> findLatestTestPosts(Pageable pageable);

    // Підрахунок використання файлу серед усіх публічних дописів
    long countByMediaFileName(String mediaFileName);
}