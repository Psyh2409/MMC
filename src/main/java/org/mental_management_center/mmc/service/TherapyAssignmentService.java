package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.TherapyAssignment;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapyAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@RequiredArgsConstructor
@Service
public class TherapyAssignmentService {

    private final TherapyAssignmentRepository repository;
    private final UserService userService;
    private final NotificationService notificationService;

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

    // Отримати всі нові (PENDING) запити для конкретного терапевта
    public List<TherapyAssignment> getPendingRequestsForTherapist(UUID therapistId) {
        return repository.findByTherapistIdAndStatus(therapistId, "PENDING");
    }

    // Прийняти запит (змінити статус на ACTIVE)
    @Transactional
    public void acceptRequest(UUID assignmentId, User therapist) {
        TherapyAssignment assignment = repository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Запит не знайдено"));

        assignment.setStatus("ACTIVE");
        assignment.setApprovedByTherapist(therapist); // Записуємо, хто прийняв

        // Передаємо об'єкт терапевта в оновлений єдиний метод
        userService.promoteToClient(assignment.getClient().getId(), therapist);

        repository.save(assignment);
    }

    // Додай цей метод у TherapyAssignmentService.java
    public List<TherapyAssignment> getAssignmentsByStatus(UUID therapistId, String status) {
        return repository.findByTherapistIdAndStatus(therapistId, status);
    }

    public boolean canRequestTherapy(User client, User therapist) {
        Optional<TherapyAssignment> existing = repository.findByClientIdAndTherapistId(client.getId(), therapist.getId());

        // Якщо історія взаємодії є, перевіряємо поточний статус
        if (existing.isPresent()) {
            String currentStatus = existing.get().getStatus();
            // Заборонити нову заявку можна тільки якщо вони ВЖЕ активно працюють
            if ("ACTIVE".equals(currentStatus)) {
                return false;
            }
        }
        // Якщо зв'язку немає (empty) або він COMPLETED — дозволяємо подати нову заявку
        return true;
    }

    public java.util.Map<UUID, String> getActiveApprovalDatesMap() {
        List<TherapyAssignment> activeAssignments = repository.findByStatus("ACTIVE");
        java.util.Map<UUID, String> datesMap = new java.util.HashMap<>();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        for (TherapyAssignment assignment : activeAssignments) {
            if (assignment.getUpdatedAt() != null) {
                datesMap.put(assignment.getClient().getId(), assignment.getUpdatedAt().format(formatter));
            }
        }
        return datesMap;
    }

    @Transactional
    public void createActiveAssignment(User client, User therapist) {
        Optional<TherapyAssignment> existing = repository.findByClientIdAndTherapistId(client.getId(), therapist.getId());

        TherapyAssignment assignment;
        if (existing.isPresent()) {
            assignment = existing.get();
            if ("ACTIVE".equals(assignment.getStatus())) {
                return; // Вони вже працюють разом, нічого не робимо
            }
        } else {
            // Використовуємо ваш існуючий патерн Builder
            assignment = TherapyAssignment.builder()
                    .client(client)
                    .therapist(therapist)
                    .build();
        }

        assignment.setStatus("ACTIVE");
        assignment.setApprovedByTherapist(therapist);
        repository.save(assignment);

        // Викликаємо ваш існуючий метод.
        // Він має подбати про підвищення ролі (якщо це Читач)
        // або просто проігнорувати зміну ролі (якщо це вже Клієнт).
        userService.promoteToClient(client.getId(), therapist);
    }

    @Transactional
    public void terminateTherapy(UUID clientId, User therapist) {
        TherapyAssignment assignment = repository.findByClientIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new RuntimeException("Активну терапію не знайдено"));

        assignment.setStatus("COMPLETED");
        repository.save(assignment);

        notificationService.createNotification(
                assignment.getClient(),
                "Терапію завершено",
                "Фахівець " + therapist.getName() + " завершив курс терапії.",
                "/profile",
                Notification.NotificationType.STANDARD
        );
    }
}