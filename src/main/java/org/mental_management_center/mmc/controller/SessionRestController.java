package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.SessionEventDto;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.TherapySessionService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionRestController {

    private final TherapySessionService sessionService;
    private final UserService userService;

    // FullCalendar автоматично надсилає параметри start і end при перемиканні місяців
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SessionEventDto>> getEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Principal principal) {

        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        List<SessionEventDto> events = sessionService.getUserSessionsAsEvents(currentUser, start, end);

        return ResponseEntity.ok(events);
    }

    @PostMapping
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<?> createSession(
            @RequestParam UUID clientId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "1") int recurringWeeks,
            Principal principal) {

        try {
            User therapist = userService.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

            sessionService.createSession(therapist, clientId, startTime, description, recurringWeeks);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            // Повертаємо 400 Bad Request із текстом помилки
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{sessionId}/cancel")
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<?> cancelSession(@PathVariable UUID sessionId,
                                           @RequestParam(required = false) String reason,
                                           Principal principal) {
        try {
            User therapist = userService.findByEmail(principal.getName()).orElseThrow();
            sessionService.cancelSession(sessionId, therapist.getId(), reason);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{sessionId}/reschedule")
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<?> rescheduleSession(
            @PathVariable UUID sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newEnd,
            Principal principal) {
        try {
            User therapist = userService.findByEmail(principal.getName()).orElseThrow();
            sessionService.rescheduleSession(sessionId, therapist.getId(), newStart, newEnd);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}