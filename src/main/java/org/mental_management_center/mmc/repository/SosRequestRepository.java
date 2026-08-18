package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.SosRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SosRequestRepository extends JpaRepository<SosRequest, UUID> {

    // Усі активні виклики SOS
    List<SosRequest> findByStatusOrderByCreatedAtDesc(String status);

    // Перевірка, чи є активний SOS саме в цьому конкретному договорі (для входу адміна)
    boolean existsByAssignmentIdAndStatus(UUID assignmentId, String status);

    // Перевірка, чи є активний SOS у клієнта взагалі (для червоної кнопка-сигналу "Терапевт")
    boolean existsByClientIdAndStatus(UUID clientId, String status);

    // НОВИЙ МЕТОД: Підрахунок кількості викликів за статусом (для лічильника в шапці Адмінки)
    long countByStatus(String status);
}