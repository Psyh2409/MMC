package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.SpecialistApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpecialistAppRepository extends JpaRepository<SpecialistApplication, UUID> {

    // 1. Перевірка, чи подавав цей юзер заявку раніше
    Optional<SpecialistApplication> findByUserId(UUID userId);

    // 2. Для Адмінки: витягти всі заявки зі статусом "PENDING"
    // Використовуємо Page, щоб мати доступ до загальної кількості для пагінації в адмінці
    Page<SpecialistApplication> findByStatus(String status, Pageable pageable);

    // 3. Для публічного пошуку на сайті
    @Query("SELECT s FROM SpecialistApplication s WHERE s.status = 'APPROVED' AND (" +
            "LOWER(s.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.middleName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<SpecialistApplication> searchApprovedSpecialists(@Param("query") String query, Pageable pageable);

    // Витягнути всі заявки зі статусом APPROVED
    @Query("SELECT s FROM SpecialistApplication s JOIN FETCH s.user WHERE s.status = :status")
    List<SpecialistApplication> findAllByStatusWithUser(@Param("status") String status);
}