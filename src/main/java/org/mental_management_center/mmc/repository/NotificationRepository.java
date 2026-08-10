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

    // Кількість непрочитаних звичайних сповіщень
    long countByUserIdAndIsReadFalseAndTypeNot(UUID userId, Notification.NotificationType type);

    // Слайсова пагінація: вибираємо ТІЛЬКИ непрочитані (isRead = false) та виключаємо сесії
    Slice<Notification> findByUserIdAndIsReadFalseAndTypeNotOrderByCreatedAtDesc(
            UUID userId, Notification.NotificationType type, Pageable pageable);

    // Пошук активних критичних викликів (ADMIN_ALERT чи THERAPY_CALL)
    List<Notification> findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(
            UUID userId, Notification.NotificationType type);
}