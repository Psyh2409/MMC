package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Автор скарги або оскарження
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // Тип цілі (CHAT_MESSAGE, COMMENT, PUBLIC_POST)
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    // ID об'єкта, на який скаржаться або який оскаржують
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    // Тип звернення: COMPLAINT (скарга) чи APPEAL (оскарження)
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    // Текст причини / пояснення користувача
    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    // Стан розгляду
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // Коментар адміністратора при закритті тикету
    @Column(name = "admin_comment", length = 500)
    private String adminComment;

    public enum ReportType {
        COMPLAINT, // Скарга на чужий контент
        APPEAL     // Оскарження видалення власного контенту
    }

    public enum TargetType {
        CHAT_MESSAGE,
        COMMENT,
        PUBLIC_POST
    }

    public enum ReportStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}