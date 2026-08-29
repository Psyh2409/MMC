package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.TherapistPortfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapistPortfolioRepository extends JpaRepository<TherapistPortfolio, UUID> {

    @EntityGraph(attributePaths = {"approaches", "targetIssues", "principles", "techniques", "certificates"})
    Optional<TherapistPortfolio> findByUserId(UUID userId);

    // Пошук фахівців за конкретним підходом
    Page<TherapistPortfolio> findByApproachesContaining(String approach, Pageable pageable);

    // Пошук фахівців за запитом клієнта
    Page<TherapistPortfolio> findByTargetIssuesContaining(String targetIssue, Pageable pageable);

    // Універсальний фільтр за кількома параметрами
    @Query("SELECT DISTINCT p FROM TherapistPortfolio p " +
            "LEFT JOIN p.approaches a " +
            "LEFT JOIN p.targetIssues t " +
            "WHERE (:approach IS NULL OR LOWER(a) LIKE LOWER(CONCAT('%', :approach, '%'))) " +
            "AND (:issue IS NULL OR LOWER(t) LIKE LOWER(CONCAT('%', :issue, '%')))")
    Page<TherapistPortfolio> filterTherapists(@Param("approach") String approach,
                                              @Param("issue") String issue,
                                              Pageable pageable);

    // Отримання TOP-3 верифікованих та активних фахівців (звертаємось до поля u.enabled)
    @Query("SELECT p FROM TherapistPortfolio p JOIN FETCH p.user u WHERE u.enabled = true ORDER BY p.experienceYears DESC")
    List<TherapistPortfolio> findTop3Specialists(Pageable pageable);
}