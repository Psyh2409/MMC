package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.SiteStats;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.repository.SiteStatsRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.web.form.ForgotPasswordForm;
import org.mental_management_center.mmc.web.form.RegistrationForm;
import org.mental_management_center.mmc.web.form.ResetPasswordForm;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.core.env.Environment;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.UUID;

@SuppressWarnings("null")
@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final SiteStatsRepository siteStatsRepository;
    private final Environment environment;

    public AuthController(UserService userService, UserRepository userRepository,
            SiteStatsRepository siteStatsRepository, Environment environment) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.siteStatsRepository = siteStatsRepository;
        this.environment = environment;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registrationForm") RegistrationForm registrationForm,
            BindingResult result,
            Model model) {

        // 1. Перевірка на помилки валідації (пусті поля, некоректний email тощо)
        if (result.hasErrors()) {
            return "register";
        }

        // 2. Перевірка збігу паролів
        if (!registrationForm.getPassword().equals(registrationForm.getConfirmPassword())) {
            model.addAttribute("error", "Паролі не збігаються! Спробуйте ще раз.");
            return "register";
        }

        try {
            // 3. Створення нового користувача
            User user = new User();
            user.setName(registrationForm.getName());
            user.setEmail(registrationForm.getEmail());
            user.setPassword(registrationForm.getPassword());

            // 4. Реєстрація в сервісі (тут створюється токен та відправляється лист)
            userService.registerNewUser(user, registrationForm.getConfirmPassword());

            // 5. Перенаправлення на сторінку логіна зі спеціальним параметром
            return "redirect:/login?needsActivation";

        } catch (RuntimeException e) {
            // Якщо email вже зайнятий або сталася інша помилка в сервісі
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/registrationConfirm")
    public String confirmRegistration(@RequestParam("token") String token, org.springframework.ui.Model model) {

        boolean isConfirmed = userService.confirmUser(token);

        if (isConfirmed) {
            model.addAttribute("message", "Ваш акаунт успішно активовано! Тепер ви можете увійти.");
            return "redirect:/login?activated=true"; // Повертаємо на сторінку логіна з повідомленням
        } else {
            model.addAttribute("error", "Посилання недійсне або термін його дії вичерпано.");
            return "register"; // Повертаємо на реєстрацію, якщо щось не так
        }
    }

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("googleOAuthEnabled", isProviderEnabled("google"));
        return "login";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(
            @Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm forgotPasswordForm,
            BindingResult result,
            Model model) {
        if (result.hasErrors()) {
            return "forgot-password";
        }

        try {
            userService.initiatePasswordReset(forgotPasswordForm.getEmail());
            model.addAttribute("message",
                    "Якщо акаунт з таким email існує, ми надіслали інструкції для відновлення пароля.");
        } catch (RuntimeException e) {
            logger.error("Error processing forgot password request for email {}: {}", forgotPasswordForm.getEmail(),
                    e.getMessage(), e);
            model.addAttribute("error",
                    "Сталася помилка при обробці запиту. Спробуйте пізніше або зверніться до адміністратора.");
        }

        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        if (!userService.isPasswordResetTokenValid(token)) {
            model.addAttribute("error", "Посилання для відновлення недійсне або вже прострочене.");
            return "forgot-password";
        }
        ResetPasswordForm resetPasswordForm = new ResetPasswordForm();
        resetPasswordForm.setToken(token);
        model.addAttribute("resetPasswordForm", resetPasswordForm);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm resetPasswordForm,
            BindingResult result,
            Model model) {
        if (result.hasErrors()) {
            return "reset-password";
        }
        try {
            userService.resetPassword(
                    resetPasswordForm.getToken(),
                    resetPasswordForm.getNewPassword(),
                    resetPasswordForm.getConfirmNewPassword());
            return "redirect:/login?passwordReset";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "reset-password";
        }
    }

    @GetMapping("/")
    public String home(Model model, Principal principal) {

        if (principal == null) {
            UUID statsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            SiteStats siteStats = siteStatsRepository.findById(statsId).orElseGet(() -> {
                return new SiteStats(); // Створюємо новий об'єкт, якщо в базі порожньо
            });
            siteStats.setGuestVisits(siteStats.getGuestVisits() + 1);
            model.addAttribute("guestCount", siteStats.getGuestVisits());
            siteStatsRepository.save(siteStats);
        } else {
            userService.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("userName", user.getName());

                // Пріоритетна роль для відображення
                RoleBit priorityRole = RoleBit.READER;
                if (user.isAdmin()) {
                    priorityRole = RoleBit.ADMIN;
                } else if (user.isTherapist()) { // Виправлено з isColleague
                    priorityRole = RoleBit.THERAPIST;
                } else if (user.isClient()) {
                    priorityRole = RoleBit.CLIENT;
                }
                model.addAttribute("userRole", priorityRole.name()); // Передаємо рядок
            });
        }

        return "index";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isProviderEnabled(String provider) {
        return hasText(environment.getProperty("app.oauth2." + provider + ".client-id"))
                && hasText(environment.getProperty("app.oauth2." + provider + ".client-secret"));
    }
}
