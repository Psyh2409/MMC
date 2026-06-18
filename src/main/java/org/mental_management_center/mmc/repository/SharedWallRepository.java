package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.JournalPost;
import org.mental_management_center.mmc.model.SharedWallEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharedWallRepository extends JpaRepository<SharedWallEntry, UUID> {

    // Отримуємо записи для кімнати, відсортовані від нових до старих, із пагінацією
    Page<SharedWallEntry> findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

    boolean existsByAuthorIdAndMediaFileName(UUID authorId, String mediaFileName);

    // SQL-запит: "Порахуй, скільки рядків у таблиці мають таке ім'я файлу"
    @Query("SELECT COUNT(sw) FROM SharedWallEntry sw WHERE sw.mediaFileName = :fileName")
    long countUsage(@Param("fileName") String fileName);

    // Замість findByMediaFileName використовуємо findFirstBy... і сортуємо за датою
    Optional<SharedWallEntry> findFirstByMediaFileNameOrderByCreatedAtDesc(String mediaFileName);
    // Рахуємо записи в особистому журналі
    long countByAuthorId(UUID authorId);

    // Повністю видаляємо журнал користувача
    void deleteByAuthorId(UUID authorId);


}
