package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/therapist")
public class TherapistController {

    private final UserService userService;

    public TherapistController(UserService userService) {
        this.userService = userService;
    }

    // Доступ ТІЛЬКИ для авторизованих (Читач, Клієнт, Адмін, інший Терапевт)
    // Гостя автоматично перекине на /login
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public/{id}")
    public String showPublicProfile(@PathVariable UUID id, Model model) {
        User therapist = Optional.ofNullable(userService.findById(id))
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        // Захист: щоб ніхто не міг відкрити "візитку" звичайного читача
        if (!therapist.isTherapist()) {
            throw new RuntimeException("Цей користувач не є фахівцем");
        }

        model.addAttribute("therapist", therapist);
        // Заділ на майбутнє: тут ми будемо діставати публічні статті цього фахівця

        return "therapist-public";
    }
}