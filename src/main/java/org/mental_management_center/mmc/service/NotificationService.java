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
    private final EmailService emailService;

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

        // Якщо користувач дав згоду — дублюємо на Email через ендпоінт автогасіння
        if (recipient.isEmailNotificationsEnabled()) {
            String redirectUrl = "/api/notifications/open/" + notification.getId();
            emailService.sendNotificationEmail(recipient.getEmail(), title, message, redirectUrl);
        }
    }

    @Transactional(readOnly = true)
    public NotificationDto.Summary getSummaryForUser(User user) {
        // Рахуємо непрочитані, ігноруючи THERAPY_CALL у бейджу дзвіночка
        long unreadCount = notificationRepository
                .countByUserIdAndIsReadFalseAndTypeNot(user.getId(), Notification.NotificationType.THERAPY_CALL);

        List<Notification> adminAlerts = notificationRepository
                .findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(user.getId(), Notification.NotificationType.ADMIN_ALERT);
        NotificationDto.Item adminItem = adminAlerts.isEmpty() ? null : NotificationDto.Item.fromEntity(adminAlerts.get(0));

        List<Notification> therapyCalls = notificationRepository
                .findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(user.getId(), Notification.NotificationType.THERAPY_CALL);
        NotificationDto.Item therapyItem = therapyCalls.isEmpty() ? null : NotificationDto.Item.fromEntity(therapyCalls.get(0));

        return new NotificationDto.Summary(unreadCount, adminItem, therapyItem);
    }

    // Отримання порції ТІЛЬКИ непрочитаних сповіщень для випадаючого списку
    @Transactional(readOnly = true)
    public Slice<NotificationDto.Item> getUserNotificationsSlice(User user, int page, int size) {
        return notificationRepository
                .findByUserIdAndIsReadFalseAndTypeNotOrderByCreatedAtDesc(
                        user.getId(),
                        Notification.NotificationType.THERAPY_CALL,
                        PageRequest.of(page, size))
                .map(NotificationDto.Item::fromEntity);
    }

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