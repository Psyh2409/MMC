package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.Role;
import org.mental_management_center.mmc.model.SiteStats;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SiteStatsRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.core.env.Environment;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.security.Principal;

@Controller
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final SiteStatsRepository siteStatsRepository;
    private final Environment environment;

    public AuthController(UserService userService, UserRepository userRepository, SiteStatsRepository siteStatsRepository, Environment environment) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.siteStatsRepository = siteStatsRepository;
        this.environment = environment;
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
    public String showLoginForm(Model model) {
        model.addAttribute("googleOAuthEnabled", isProviderEnabled("google"));
        model.addAttribute("facebookOAuthEnabled", isProviderEnabled("facebook"));
        return "login";
    }

    @GetMapping("/")
    public String home(Model model, Principal principal) {

        if (principal == null) {
            SiteStats siteStats = siteStatsRepository.findById(1L).orElse(new SiteStats());
            siteStats.setGuestVisits(siteStats.getGuestVisits() + 1);
            model.addAttribute("guestCount", siteStats.getGuestVisits());
            siteStatsRepository.save(siteStats);
        } else {
            userService.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("userName", user.getName());
                Role priorityRole = Role.READER;
                if (user.isAdmin()) {
                    priorityRole = Role.ADMIN;
                } else if (user.isColleague()) {
                    priorityRole = Role.COLLEAGUE;
                } else if (user.isClient()) {
                    priorityRole = Role.CLIENT;
                }
                model.addAttribute("userRole", priorityRole);
            });
        }

        return "index";
    }

    @GetMapping("/admin/users")
    public String showAdminUsers(Model model) {

        model.addAttribute("allUsers", userRepository.findAll(Sort.by("id")));
        model.addAttribute("countUsers", userRepository.countByRolesContaining(Role.READER));
        model.addAttribute("countClients", userRepository.countByRolesContaining(Role.CLIENT));
        SiteStats siteStats = siteStatsRepository.findById(1L).orElse(new SiteStats());
        model.addAttribute("totalVisits", siteStats.getGuestVisits());

        return "admin-users";
    }

    @PostMapping("/admin/promote/{id}")
    public String promoteToClient(@PathVariable Long id) {
        userService.promoteToClient(id);
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/update-notes/{id}")
    public String updateNotes(@PathVariable Long id, @RequestParam("notes") String notes) {
        User user = userRepository.findById(id).orElseThrow();
        user.setAdminNotes(notes);
        userRepository.save(user);
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/delete/{id}")
    public String deleteUser(@PathVariable Long id, Principal principal) {
        try {
            userService.deleteUserById(id, principal.getName());
            return "redirect:/admin/users?success";
        } catch (RuntimeException e) {
            return "redirect:/admin/users?error=" + e.getMessage();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isProviderEnabled(String provider) {
        return hasText(environment.getProperty("app.oauth2." + provider + ".client-id"))
                && hasText(environment.getProperty("app.oauth2." + provider + ".client-secret"));
    }
}
