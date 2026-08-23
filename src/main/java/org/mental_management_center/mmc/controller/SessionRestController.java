package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.SessionEventDto;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.TherapySessionService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

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

    @org.springframework.web.bind.annotation.PostMapping
    @PreAuthorize("hasRole('THERAPIST')")
    public ResponseEntity<?> createSession(
            @RequestParam java.util.UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            Principal principal) {

        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        sessionService.createSession(therapist, clientId, startTime);

        return ResponseEntity.ok().build();
    }
}