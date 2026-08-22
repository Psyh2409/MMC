package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "therapist_portfolios")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TherapistPortfolio {

    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Min(0)
    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "credo", columnDefinition = "TEXT")
    private String credo; // Професійне кредо / девіз

    @ElementCollection
    @CollectionTable(name = "portfolio_approaches", joinColumns = @JoinColumn(name = "portfolio_id"))
    @Column(name = "approach")
    @Builder.Default
    private Set<String> approaches = new HashSet<>(); // Підходи (КПТ, Гештальт тощо)

    @ElementCollection
    @CollectionTable(name = "portfolio_target_issues", joinColumns = @JoinColumn(name = "portfolio_id"))
    @Column(name = "target_issue")
    @Builder.Default
    private Set<String> targetIssues = new HashSet<>(); // Запити (Тривожність, ПТСР тощо)

    @ElementCollection
    @CollectionTable(name = "portfolio_principles", joinColumns = @JoinColumn(name = "portfolio_id"))
    @Column(name = "principle")
    @Builder.Default
    private Set<String> principles = new HashSet<>(); // Принципи роботи

    @ElementCollection
    @CollectionTable(name = "portfolio_techniques", joinColumns = @JoinColumn(name = "portfolio_id"))
    @Column(name = "technique")
    @Builder.Default
    private Set<String> techniques = new HashSet<>(); // Техніки та інструменти

    @ElementCollection
    @CollectionTable(name = "portfolio_certificates", joinColumns = @JoinColumn(name = "portfolio_id"))
    @Column(name = "certificate")
    @Builder.Default
    private Set<String> certificates = new HashSet<>(); // Сертифікати та дипломи

    @Column(name = "practice_type", length = 50)
    private String practiceType; // MEDICAL або NON_MEDICAL

    @Column(name = "non_medical_competence_aware")
    private boolean nonMedicalCompetenceAware;

    @Column(name = "about_me", length = 1500)
    private String aboutMe;
}