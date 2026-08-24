package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.TherapyAssignmentService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CalendarController {

    private final UserService userService;
    private final TherapyAssignmentService assignmentService;

    @GetMapping("/calendar")
    @PreAuthorize("isAuthenticated()") // Доступ для всіх авторизованих
    public String showCalendar(Principal principal, Model model) {

        User user = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        boolean isTherapist = user.isTherapist();
        model.addAttribute("isTherapist", isTherapist);

        // Завантажуємо список клієнтів ТІЛЬКИ якщо користувач має роль фахівця
        if (isTherapist) {
            model.addAttribute("activeAssignments", assignmentService.getAssignmentsByStatus(user.getId(), "ACTIVE"));
        }

        return "calendar"; // Вказує на новий шаблон calendar.html
    }
}