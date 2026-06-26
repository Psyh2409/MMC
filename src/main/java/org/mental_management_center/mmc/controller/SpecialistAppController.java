package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.SpecialistService;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.web.form.EdeboVerificationForm;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/profile")
public class SpecialistAppController {

    private final SpecialistService specialistService;
    private final UserService userService;

    public SpecialistAppController(SpecialistService specialistService, UserService userService) {
        this.specialistService = specialistService;
        this.userService = userService;
    }

    @PostMapping("/verify-diploma")
    public String submitDiploma(EdeboVerificationForm form,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {

        // Знаходимо користувача за email (із сесії)
        User user = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувач не знайдений"));

        try {
            specialistService.submitApplication(user, form);
            redirectAttributes.addFlashAttribute("success", "Заявка на верифікацію успішно відправлена!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }
}
