package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.SessionEventDto;
import org.mental_management_center.mmc.model.TherapySession;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.enums.SessionStatus;
import org.mental_management_center.mmc.repository.TherapySessionRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TherapySessionService {

    private final TherapySessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SessionEventDto> getUserSessionsAsEvents(User user, LocalDateTime start, LocalDateTime end) {

        // Тепер ми просто беремо ВСІ сесії, де користувач є хоча б кимось (терапевтом чи клієнтом)
        List<TherapySession> sessions = sessionRepository.findSessionsForUser(user.getId(), start, end);

        // Передаємо самого користувача у метод мапінгу, щоб розуміти його поточну роль
        return sessions.stream()
                .map(session -> mapToEventDto(session, user))
                .collect(Collectors.toList());
    }

    private SessionEventDto mapToEventDto(TherapySession session, User currentUser) {
        boolean isMyClientSession = session.getClient().getId().equals(currentUser.getId());
        String title = isMyClientSession ? "🛋 Терапевт: " + session.getTherapist().getName()
                : "🎙 Клієнт: " + session.getClient().getName();

        // Зберігаємо оригінальні кольори
        String eventColor = isMyClientSession ? "var(--accent-color)" : "var(--primary-color)";
        String description = isMyClientSession ? null : session.getDescription();

        // 🟢 Передаємо статус і причину через extendedProps (додайте ці поля в DTO, якщо їх там немає, або використовуйте Map)
        return SessionEventDto.builder()
                .id(session.getId().toString())
                .title(title)
                .start(session.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .end(session.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .color(eventColor)
                .description(description)
                .status(session.getStatus().name())
                .cancellationReason(session.getCancellationReason())
                .build();
    }

    @Transactional
    public void createSession(User therapist, java.util.UUID clientId, LocalDateTime startTime, String description, int recurringWeeks) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнта не знайдено"));

        // Запобіжник, щоб не створили випадково 100 сесій
        int weeksToSchedule = Math.min(recurringWeeks, 16);

        for (int i = 0; i < weeksToSchedule; i++) {
            LocalDateTime currentStartTime = startTime.plusWeeks(i);
            LocalDateTime currentEndTime = currentStartTime.plusHours(1);

            long overlaps = sessionRepository.countOverlappingSessions(therapist.getId(), clientId, currentStartTime, currentEndTime);
            if (overlaps > 0) {
                // Якщо є накладка на конкретний тиждень - перериваємо транзакцію.
                // Можна змінити логіку, щоб ігнорувати лише зайнятий тиждень, але для початку краще fail-fast.
                throw new IllegalStateException("Виявлено накладку графіків на дату: " + currentStartTime.toLocalDate());
            }

            TherapySession session = new TherapySession();
            session.setTherapist(therapist);
            session.setClient(client);
            session.setStartTime(currentStartTime);
            session.setEndTime(currentEndTime);
            session.setStatus(SessionStatus.SCHEDULED);

            if (description != null && !description.isBlank()) {
                session.setDescription(description.trim());
            }

            sessionRepository.save(session);
        }
    }

    @Transactional
    public void cancelSession(UUID sessionId, UUID therapistId, String reason) {
        TherapySession session = sessionRepository.findById(sessionId).orElseThrow();
        if (!session.getTherapist().getId().equals(therapistId)) throw new SecurityException("Немає прав");

        session.setStatus(SessionStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            session.setCancellationReason(reason.trim());
        }
        sessionRepository.save(session);
    }

    @Transactional
    public void rescheduleSession(UUID sessionId, UUID therapistId, LocalDateTime newStart, LocalDateTime newEnd) {
        TherapySession session = sessionRepository.findById(sessionId).orElseThrow();
        if (!session.getTherapist().getId().equals(therapistId)) throw new SecurityException("Немає прав");

        // Перевірка накладок на новий час
        long overlaps = sessionRepository.countOverlappingSessions(therapistId, session.getClient().getId(), newStart, newEnd);
        if (overlaps > 0) throw new IllegalStateException("Цей час вже зайнятий.");

        session.setStartTime(newStart);
        session.setEndTime(newEnd);
        sessionRepository.save(session);
    }
}