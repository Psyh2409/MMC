package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.TherapyAssignment;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapyAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TherapyAssignmentService {

    private final TherapyAssignmentRepository repository;

    public TherapyAssignmentService(TherapyAssignmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TherapyAssignment sendRequest(User client, User therapist) {
        // Перевіряємо, чи немає вже існуючого запиту (щоб не спамили кнопкою)
        Optional<TherapyAssignment> existing = repository.findByClientIdAndTherapistId(client.getId(), therapist.getId());

        if (existing.isPresent()) {
            throw new IllegalStateException("Ви вже відправляли запит цьому фахівцю або вже працюєте з ним.");
        }

        // Створюємо новий запит зі статусом PENDING
        TherapyAssignment assignment = TherapyAssignment.builder()
                .client(client)
                .therapist(therapist)
                .status("PENDING")
                .build();

        return repository.save(assignment);
    }

    // ДОДАЙ ЦІ МЕТОДИ ВНИЗУ КЛАСУ:

    // Отримати всі нові (PENDING) запити для конкретного терапевта
    public List<TherapyAssignment> getPendingRequestsForTherapist(UUID therapistId) {
        return repository.findByTherapistIdAndStatus(therapistId, "PENDING");
    }

    // Прийняти запит (змінити статус на ACTIVE)
    @Transactional
    public void acceptRequest(UUID assignmentId) {
        TherapyAssignment assignment = repository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Запит не знайдено"));

        assignment.setStatus("ACTIVE");
        repository.save(assignment);
    }
}