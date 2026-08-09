package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Кількість непрочитаних для бейджа
    long countByUserIdAndIsReadFalse(UUID userId);

    // Слайсова пагінація ("Завантажити ще") без виконання COUNT(*)
    Slice<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Пошук активних критичних викликів (ADMIN_ALERT чи THERAPY_CALL)
    List<Notification> findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(
            UUID userId, Notification.NotificationType type);
}