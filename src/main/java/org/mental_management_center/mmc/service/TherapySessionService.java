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
        // Перевіряємо, хто зараз дивиться на календар відносно цієї конкретної сесії
        boolean isMyClientSession = session.getClient().getId().equals(currentUser.getId());

        String title;
        String eventColor;

        if (isMyClientSession) {
            // Я - клієнт у цій сесії. Бачу, з яким терапевтом працюю.
            title = "🛋 Терапевт: " + session.getTherapist().getName();
            eventColor = "var(--accent-color)";
        } else {
            // Я - фахівець у цій сесії. Бачу свого клієнта.
            title = "🎙 Клієнт: " + session.getClient().getName();
            eventColor = "var(--primary-color)";
        }

        // Перевизначаємо колір, якщо сесія скасована
        if (session.getStatus() == SessionStatus.CANCELLED) {
            eventColor = "var(--text-disabled)";
        }

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
        LocalDateTime endTime = startTime.plusHours(1); // Фіксована тривалість - 1 година

        // Захист від накладок
        long overlaps = sessionRepository.countOverlappingSessions(therapist.getId(), clientId, startTime, endTime);
        if (overlaps > 0) {
            throw new IllegalStateException("У вас або у клієнта вже є запланована сесія на цей час.");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнта не знайдено"));

        TherapySession session = new TherapySession();
        session.setTherapist(therapist);
        session.setClient(client);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setStatus(SessionStatus.SCHEDULED);

        return sessionRepository.save(session);
    }
}