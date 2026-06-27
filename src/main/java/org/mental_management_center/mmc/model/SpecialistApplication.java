package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "specialist_applications")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SpecialistApplication {

    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @NotBlank
    @Size(max = 50)
    @Column(name = "education_level", nullable = false, length = 50)
    private String educationLevel;

    @NotBlank
    @Size(max = 50)
    @Column(name = "specialty", length = 100)
    private String specialty;

    @NotBlank
    @Size(max = 10)
    @Column(name = "diploma_series", nullable = false, length = 10)
    private String diplomaSeries;

    @NotBlank
    @Size(max = 20)
    @Column(name = "diploma_number", nullable = false, length = 20)
    private String diplomaNumber;

    @NotBlank
    @Size(max = 50)
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @NotBlank
    @Size(max = 50)
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "middle_name", length = 50)
    private String middleName;

    @Column(name = "no_middle_name", nullable = false)
    private boolean noMiddleName;

    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, APPROVED, REJECTED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}