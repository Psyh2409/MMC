package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "therapy_assignments")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TherapyAssignment {

    @Id
    @UuidGenerator
    private UUID id;

    // Хто подав запит (Клієнт)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    // До кого подали запит (Терапевт)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "therapist_id", nullable = false)
    private User therapist;

    // Статус: "PENDING" (новий запит), "ACTIVE" (в роботі), "DECLINED" (відхилено), "FINISHED" (завершено)
    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}