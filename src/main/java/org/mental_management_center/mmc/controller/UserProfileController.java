package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.service.TherapyNoteService;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.web.form.PasswordChangeForm;
import org.mental_management_center.mmc.web.form.ProfileUpdateForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import java.security.Principal;

@Controller
public class UserProfileController {

    private final UserService userService;
    private final TherapyNoteService therapyNoteService;

    public UserProfileController(UserService userService, TherapyNoteService therapyNoteService) {
        this.userService = userService;
        this.therapyNoteService = therapyNoteService;
    }

    @GetMapping("/profile")
    public String showProfile(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            model.addAttribute("user", user);

            // 1. Отримуємо нотатки, де цей користувач є автором
            model.addAttribute("myNotes", therapyNoteService.getNotesByAuthor(user.getId()));

            // 2. Логіка запрошення (сповіщення)
            if (user.isClient()) {
                // Клієнт бачить кнопку переходу у свій кабінет
                model.addAttribute("therapyRoomUrl", "/therapy/room/" + user.getId());
                model.addAttribute("hasInvitation", true);
            }

            if (!model.containsAttribute("profileUpdateForm")) {
                ProfileUpdateForm profileUpdateForm = new ProfileUpdateForm();
                profileUpdateForm.setName(user.getName());
                model.addAttribute("profileUpdateForm", profileUpdateForm);
            }
        });

        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        }
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm passwordChangeForm,
                                 BindingResult result,
                                 Principal principal,
                                 Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        userService.findByEmail(principal.getName()).ifPresent(user -> model.addAttribute("user", user));

        if (result.hasErrors()) {
            return "profile";
        }

        try {
            userService.changePassword(
                    principal.getName(),
                    passwordChangeForm.getCurrentPassword(),
                    passwordChangeForm.getNewPassword(),
                    passwordChangeForm.getConfirmNewPassword()
            );
            model.addAttribute("passwordSuccess", "Пароль успішно змінено.");
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
            return "profile";
        } catch (RuntimeException e) {
            model.addAttribute("passwordError", e.getMessage());
            return "profile";
        }
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileUpdateForm") ProfileUpdateForm profileUpdateForm,
                                BindingResult result,
                                Principal principal,
                                Model model) {
        if (principal == null) return "redirect:/login";

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            model.addAttribute("user", user);
            model.addAttribute("myNotes", therapyNoteService.getNotesByAuthor(user.getId()));
            if (user.isClient()) {
                model.addAttribute("therapyRoomUrl", "/therapy/room/" + user.getId());
                model.addAttribute("hasInvitation", true);
            }
        });

        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        }

        if (result.hasErrors()) return "profile";

        try {
            userService.updateProfileName(principal.getName(), profileUpdateForm.getName());
            userService.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("user", user);
                ProfileUpdateForm refreshedForm = new ProfileUpdateForm();
                refreshedForm.setName(user.getName());
                model.addAttribute("profileUpdateForm", refreshedForm);
            });
            model.addAttribute("profileSuccess", "Профіль успішно оновлено.");
            return "profile";
        } catch (RuntimeException e) {
            model.addAttribute("profileError", e.getMessage());
            return "profile";
        }
    }
}
