package org.mental_masochistic_club.mmc.controller;

import org.mental_masochistic_club.mmc.model.User;
import org.mental_masochistic_club.mmc.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.validation.Valid;

import java.security.Principal;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user,
                               BindingResult result, Model model) {
        if (result.hasErrors()) return "register";
        try {
            userService.registerNewUser(user);
            return "redirect:/login?success";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model, Principal principal) {

        if (principal != null) {
            String email = principal.getName();
            userService.findByEmail(email).ifPresent(user -> {
                model.addAttribute("userName", user.getName());
                model.addAttribute("userRole", user.getRole());
            });
        }

        return "index";
    }
}
