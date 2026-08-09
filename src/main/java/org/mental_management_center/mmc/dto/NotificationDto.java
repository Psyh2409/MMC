package org.mental_management_center.mmc.dto;

import org.mental_management_center.mmc.model.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationDto {

    // Запис для окремого сповіщення
    public record Item(
            UUID id,
            String title,
            String message,
            String targetUrl,
            Notification.NotificationType type,
            boolean isRead,
            LocalDateTime createdAt
    ) {
        public static Item fromEntity(Notification entity) {
            return new Item(
                    entity.getId(),
                    entity.getTitle(),
                    entity.getMessage(),
                    entity.getTargetUrl(),
                    entity.getType(),
                    entity.isRead(),
                    entity.getCreatedAt()
            );
        }
    }

    // Зведений об'єкт для фонового опитування (AJAX Polling)
    public record Summary(
            long unreadCount,
            Item criticalAdminAlert,
            Item criticalTherapyCall
    ) {}
}