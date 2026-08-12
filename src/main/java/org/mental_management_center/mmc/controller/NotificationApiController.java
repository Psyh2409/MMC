package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.NotificationDto;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.NotificationRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.NotificationService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserService userService;

    // Ендпоінт фонового опитування (раз на 10-15 сек)
    @GetMapping("/summary")
    public ResponseEntity<NotificationDto.Summary> getSummary(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        User currentUser = userRepository.findByEmail(principal.getName()).orElseThrow();
        return ResponseEntity.ok(notificationService.getSummaryForUser(currentUser));
    }

    // Ендпоінт завантаження списку з підвантаженням "Завантажити ще" (Slice)
    @GetMapping("/feed")
    public ResponseEntity<Slice<NotificationDto.Item>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        User currentUser = userRepository.findByEmail(principal.getName()).orElseThrow();
        return ResponseEntity.ok(notificationService.getUserNotificationsSlice(currentUser, page, size));
    }

    // Позначити сповіщення прочитаним
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        User currentUser = userRepository.findByEmail(principal.getName()).orElseThrow();
        notificationService.markAsRead(id, currentUser);
        return ResponseEntity.ok().build();
    }

    // Ендпоінт для відкриття сповіщення за посиланням з Email
    @GetMapping("/open/{id}")
    public ResponseEntity<Void> openNotificationAndRedirect(@PathVariable UUID id, Principal principal) {
        if (principal != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(currentUser -> {
                notificationService.markAsRead(id, currentUser);
            });
        }

        Notification notification = notificationRepository.findById(id).orElse(null);
        String targetUrl = (notification != null && notification.getTargetUrl() != null && !notification.getTargetUrl().isBlank())
                ? notification.getTargetUrl()
                : "/";

        // Виконуємо HTTP 302 Redirect на цільову сторінку
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(targetUrl))
                .build();
    }
}