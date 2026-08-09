package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.NotificationDto;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // Створення сповіщення для користувача
    @Transactional
    public void createNotification(User recipient, String title, String message, String targetUrl, Notification.NotificationType type) {
        Notification notification = Notification.builder()
                .user(recipient)
                .title(title)
                .message(message)
                .targetUrl(targetUrl)
                .type(type)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    // Отримання зведеної інформації для фонового опитування
    @Transactional(readOnly = true)
    public NotificationDto.Summary getSummaryForUser(User user) {
        long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(user.getId());

        // Пошук критичного повідомлення від адміна
        List<Notification> adminAlerts = notificationRepository
                .findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(user.getId(), Notification.NotificationType.ADMIN_ALERT);
        NotificationDto.Item adminItem = adminAlerts.isEmpty() ? null : NotificationDto.Item.fromEntity(adminAlerts.get(0));

        // Пошук критичного виклику на сесію
        List<Notification> therapyCalls = notificationRepository
                .findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(user.getId(), Notification.NotificationType.THERAPY_CALL);
        NotificationDto.Item therapyItem = therapyCalls.isEmpty() ? null : NotificationDto.Item.fromEntity(therapyCalls.get(0));

        return new NotificationDto.Summary(unreadCount, adminItem, therapyItem);
    }

    // Отримання порції сповіщень (Слайсова пагінація)
    @Transactional(readOnly = true)
    public Slice<NotificationDto.Item> getUserNotificationsSlice(User user, int page, int size) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page, size))
                .map(NotificationDto.Item::fromEntity);
    }

    // Відмітити сповіщення як прочитане
    @Transactional
    public void markAsRead(UUID notificationId, User user) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (notification.getUser().getId().equals(user.getId())) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        });
    }
}