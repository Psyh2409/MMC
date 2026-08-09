package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Отримувач сповіщення
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Заголовок сповіщення
    @Column(name = "title", nullable = false)
    private String title;

    // Текст повідомлення
    @Column(name = "message", length = 1000, nullable = false)
    private String message;

    // Посилання, куди переходить користувач при кліку (наприклад, на статтю чи в чат)
    @Column(name = "target_url")
    private String targetUrl;

    // Тип сповіщення
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    // Прапорець прочитання
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        STANDARD,      // Звичайне (коментар, відповідь, чат)
        THERAPY_CALL,  // Запрошення на терапевтичну сесію від фахівця
        ADMIN_ALERT    // Домінуюче "Червоне повідомлення" від адміністратора
    }
}