package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;

@Controller
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String showProfile(Model model, Principal principal) {
        // Якщо юзер не залогінений - відправляємо на вхід
        if (principal == null) return "redirect:/login";

        // Знаходимо юзера по email (який Spring Security бере з сесії)
        userService.findByEmail(principal.getName()).ifPresent(user -> {
            model.addAttribute("user", user);
        });

        return "profile"; // Назва HTML-файлу
    }
}