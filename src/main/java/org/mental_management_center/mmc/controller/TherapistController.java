package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.TherapyAssignment;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.TherapyAssignmentService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/therapist")
public class TherapistController {

    private final UserService userService;
    private final TherapyAssignmentService assignmentService;

    public TherapistController(UserService userService, TherapyAssignmentService assignmentService) {
        this.userService = userService;
        this.assignmentService = assignmentService;
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

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/request-therapy/{therapistId}")
    public String requestTherapy(@PathVariable UUID therapistId,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {

        // Знаходимо того, хто клікнув (Клієнт)
        User client = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Знаходимо фахівця, до якого клікнули
        User therapist = Optional.ofNullable(userService.findById(therapistId))
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        // Захист від дурня: не можна подати запит самому собі
        if (client.getId().equals(therapist.getId())) {
            redirectAttributes.addFlashAttribute("error", "Ви не можете надіслати запит самому собі.");
            return "redirect:/therapist/public/" + therapistId;
        }

        try {
            assignmentService.sendRequest(client, therapist);
            redirectAttributes.addFlashAttribute("success", "Ваш запит успішно відправлено! Очікуйте на підтвердження фахівця.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        // Повертаємо користувача назад на сторінку візитки
        return "redirect:/therapist/public/" + therapistId;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public String therapistDashboard(Principal principal, Model model) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        model.addAttribute("user", therapist);

        // Дістаємо всі запити, які чекають на відповідь
        List<TherapyAssignment> pendingRequests = assignmentService.getPendingRequestsForTherapist(therapist.getId());

        // НОВЕ: Дістаємо активних клієнтів, щоб передати їх в HTML сторінку
        List<TherapyAssignment> activeAssignments = assignmentService.getAssignmentsByStatus(therapist.getId(), "ACTIVE");

        model.addAttribute("therapist", therapist);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("activeAssignments", activeAssignments); // Передаємо список на фронтенд

        return "therapist-dashboard";
    }

    // 2. Обробка натискання кнопки "Прийняти"
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/dashboard/accept/{id}")
    public String acceptRequest(@PathVariable UUID id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            // Знаходимо терапевта, який зараз натиснув кнопку
            User therapist = userService.findByEmail(principal.getName()).orElseThrow();

            // Передаємо id запиту ТА терапевта в сервіс
            assignmentService.acceptRequest(id, therapist);

            redirectAttributes.addFlashAttribute("success", "Клієнта успішно додано до вашої практики!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при прийнятті запиту: " + e.getMessage());
        }
        return "redirect:/therapist/dashboard";
    }
}