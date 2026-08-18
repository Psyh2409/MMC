package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sos_requests")
@Getter
@Setter
@NoArgsConstructor
public class SosRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TherapyAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, RESOLVED

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}