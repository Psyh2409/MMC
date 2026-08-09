package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    // @EntityGraph гарантує підвантаження даних користувача (reporter) в один SQL-запит
    @EntityGraph(attributePaths = {"reporter"})
    Page<Report> findByStatusOrderByCreatedAtDesc(Report.ReportStatus status, Pageable pageable);

    // Перевірка, чи користувач вже подавав скаргу на цей об'єкт
    boolean existsByReporterIdAndTargetIdAndStatus(UUID reporterId, UUID targetId, Report.ReportStatus status);

    // Пошук існуючої скарги/оскарження для конкретного об'єкта
    Optional<Report> findByTargetIdAndReportType(UUID targetId, Report.ReportType reportType);
}