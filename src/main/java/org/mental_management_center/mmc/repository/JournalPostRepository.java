package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.JournalPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalPostRepository extends JpaRepository<JournalPost, UUID> {

    // Знаходимо всі пости конкретного користувача, сортуючи від найновіших до найстаріших
    Page<JournalPost> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndMediaFileName(UUID userId, String mediaFileName);

    // SQL-запит: "Порахуй, скільки рядків у таблиці мають таке ім'я файлу"
    @Query("SELECT COUNT(jp) FROM JournalPost jp WHERE jp.mediaFileName = :fileName")
    long countUsage(@Param("fileName") String fileName);

    Optional<JournalPost> findFirstByMediaFileName(String mediaFileName);

    // Рахуємо записи в особистому журналі
    long countByUserId(java.util.UUID userId);

    // Повністю видаляємо журнал користувача
    void deleteByUserId(java.util.UUID userId);

}
