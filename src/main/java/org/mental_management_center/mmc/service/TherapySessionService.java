package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.SessionEventDto;
import org.mental_management_center.mmc.model.TherapySession;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapySessionRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TherapySessionService {

    private final TherapySessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SessionEventDto> getUserSessionsAsEvents(User user, LocalDateTime start, LocalDateTime end) {
        List<TherapySession> sessions;

        // Визначаємо, чиї сесії шукати: фахівця чи клієнта
        if (user.isTherapist()) {
            sessions = sessionRepository.findByTherapistIdAndStartTimeBetween(user.getId(), start, end);
        } else {
            sessions = sessionRepository.findByClientIdAndStartTimeBetween(user.getId(), start, end);
        }

        return sessions.stream()
                .map(this::mapToEventDto)
                .collect(Collectors.toList());
    }

    // Пояснення «на пальцях»: Цей метод бере складний об'єкт бази даних
    // і перетворює його на просту «коробочку» DTO для фронтенду
    private SessionEventDto mapToEventDto(TherapySession session) {
        // Задаємо колір залежно від статусу (можна налаштувати під вашу палітру)
        String eventColor = switch (session.getStatus()) {
            case SCHEDULED -> "var(--primary-color)";
            case COMPLETED -> "var(--accent-color)";
            case CANCELLED -> "var(--text-disabled)";
        };

        // Формуємо заголовок (з ким сесія)
        String title = session.getClient().getName();

        return SessionEventDto.builder()
                .id(session.getId().toString())
                .title(title)
                .start(session.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .end(session.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .color(eventColor)
                .description(session.getDescription())
                .build();
    }

    @Transactional
    public TherapySession createSession(User therapist, java.util.UUID clientId, LocalDateTime startTime) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнта не знайдено"));

        TherapySession session = new TherapySession();
        session.setTherapist(therapist);
        session.setClient(client);
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusHours(1)); // За замовчуванням +1 година
        session.setStatus(org.mental_management_center.mmc.model.enums.SessionStatus.SCHEDULED);

        return sessionRepository.save(session);
    }
}