package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.ConsultationRequest;
import org.mental_management_center.mmc.model.enums.ConsultationRequestStatus;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ConsultationRequestRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ConsultationRequestService {

    private final ConsultationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TherapyAssignmentService therapyAssignmentService;

    @Transactional
    public ConsultationRequest createRequest(User client, UUID therapistId, String message) {
        if (client.getId().equals(therapistId)) {
            throw new IllegalArgumentException("Ви не можете надіслати заявку самому собі.");
        }

        User therapist = userRepository.findById(therapistId)
                .orElseThrow(() -> new IllegalArgumentException("Терапевта не знайдено."));

        if (!therapist.isTherapist()) {
            throw new IllegalArgumentException("Обраний користувач не є верифікованим фахівцем.");
        }

        // У методі createRequest перевіряємо чи є блокування
        boolean existsBlocked = requestRepository.existsByClientIdAndTherapistIdAndStatus(
                client.getId(), therapistId, ConsultationRequestStatus.BLOCKED
        );

        if (existsBlocked) {
            throw new IllegalStateException("Ви не можете надіслати заявку цьому фахівцю.");
        }

        boolean existsPending = requestRepository.existsByClientIdAndTherapistIdAndStatus(
                client.getId(), therapistId, ConsultationRequestStatus.PENDING
        );

        if (existsPending) {
            throw new IllegalStateException("Ви вже надіслали заявку цьому терапевту. Заявка знаходиться на розгляді.");
        }

        ConsultationRequest request = new ConsultationRequest();
        request.setClient(client);
        request.setTherapist(therapist);
        request.setMessage(message != null ? message.trim() : "");
        request.setStatus(ConsultationRequestStatus.PENDING);

        ConsultationRequest saved = requestRepository.save(request);

        // Надсилаємо системне сповіщення терапевту про нову заявку
        notificationService.createNotification(
                therapist,
                "Нова заявка на консультацію",
                "Клієнт " + client.getName() + " надіслав вам заявку на консультацію.",
                "/therapist/dashboard",
                Notification.NotificationType.THERAPY_REQUEST
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConsultationRequest> getPendingRequestsForTherapist(UUID therapistId) {
        return requestRepository.findByTherapistIdAndStatusOrderByCreatedAtDesc(
                therapistId, ConsultationRequestStatus.PENDING
        );
    }

    @Transactional(readOnly = true)
    public List<ConsultationRequest> getRequestsForClient(UUID clientId) {
        return requestRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    @Transactional
    public void acceptRequest(UUID requestId, User therapist) {
        ConsultationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Заявку не знайдено."));

        if (!request.getTherapist().getId().equals(therapist.getId())) {
            throw new SecurityException("Ви не маєте прав для обробки цієї заявки.");
        }

        if (request.getStatus() != ConsultationRequestStatus.PENDING) {
            throw new IllegalStateException("Цю заявку вже опрацьовано.");
        }

        // 1. Оновлюємо статус заявки
        request.setStatus(ConsultationRequestStatus.ACCEPTED);
        requestRepository.save(request);

        // 2. Делегуємо створення терапевтичного зв'язку профільному сервісу
        therapyAssignmentService.createActiveAssignment(request.getClient(), therapist);

        // 3. Надсилаємо сповіщення клієнту
        notificationService.createNotification(
                request.getClient(),
                "Заявку прийнято!",
                "Фахівець " + therapist.getName() + " прийняв вашу заявку на консультацію.",
                "/profile",
                Notification.NotificationType.STANDARD
        );
    }

    @Transactional
    public void rejectRequest(UUID requestId, User therapist) {
        ConsultationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Заявку не знайдено."));

        if (!request.getTherapist().getId().equals(therapist.getId())) {
            throw new SecurityException("Ви не маєте прав для обробки цієї заявки.");
        }

        if (request.getStatus() != ConsultationRequestStatus.PENDING) {
            throw new IllegalStateException("Цю заявку вже опрацьовано.");
        }

        // 1. Змінюємо статус на відхилений
        request.setStatus(ConsultationRequestStatus.REJECTED);
        requestRepository.save(request);

        // 2. Сповіщаємо клієнта
        notificationService.createNotification(
                request.getClient(),
                "Заявку відхилено",
                "Фахівець " + therapist.getName() + " наразі не може прийняти вашу заявку.",
                "/profile",
                Notification.NotificationType.STANDARD
        );
    }

    @Transactional
    public void blockRequest(UUID requestId, User therapist) {
        ConsultationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Заявку не знайдено."));

        if (!request.getTherapist().getId().equals(therapist.getId())) {
            throw new SecurityException("Ви не маєте прав для обробки цієї заявки.");
        }

        request.setStatus(ConsultationRequestStatus.BLOCKED);
        requestRepository.save(request);

        // Сповіщаємо клієнта нейтрально або залишаємо без сповіщення (на ваш розсуд)
        notificationService.createNotification(
                request.getClient(),
                "Статус заявки",
                "Фахівець відхилив вашу заявку.",
                "/profile",
                Notification.NotificationType.STANDARD
        );
    }
}