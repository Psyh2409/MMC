package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.model.SiteStats;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SiteStatsRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequestMapping("/admin") // Цей префікс додається до всіх методів нижче
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SiteStatsRepository siteStatsRepository;

    // 1. Сторінка списку користувачів (Переїхала з AuthController)
    @GetMapping("/users")
    public String showAdminUsers(Model model) {
        model.addAttribute("allUsers", userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("countUsers", userRepository.countByRoleMask(RoleBit.READER.getMask()));
        model.addAttribute("countClients", userRepository.countByRoleMask(RoleBit.CLIENT.getMask()));

        UUID statsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SiteStats siteStats = siteStatsRepository.findById(statsId).orElse(new SiteStats());
        model.addAttribute("totalVisits", siteStats.getGuestVisits());

        return "admin-users";
    }

    // 2. Тотальний бан (Сайт)
    @PostMapping("/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable UUID id) {
        userService.toggleUserStatus(id);
        return "redirect:/admin/users?success";
    }

    // 3. Бан в чаті
    @PostMapping("/toggle-chat/{id}")
    public String toggleChatStatus(@PathVariable UUID id) {
        userService.toggleChatStatus(id);
        return "redirect:/admin/users?success";
    }

    // 4. Бан в коментарях
    @PostMapping("/toggle-comments/{id}")
    public String toggleCommentsStatus(@PathVariable UUID id) {
        userService.toggleCommentsStatus(id);
        return "redirect:/admin/users?success";
    }

    // 5. Призначення клієнтом (Переїхало з AuthController)
    @PostMapping("/promote/{id}")
    public String promoteToClient(@PathVariable UUID id) {
        userService.promoteToClient(id);
        return "redirect:/admin/users";
    }

    // 6. Оновлення нотаток (Переїхало з AuthController)
    @PostMapping("/update-notes/{id}")
    public String updateNotes(@PathVariable UUID id, @RequestParam("notes") String notes) {
        User user = userRepository.findById(id).orElseThrow();
        user.setAdminNotes(notes);
        userRepository.save(user);
        return "redirect:/admin/users";
    }

    // 7. Видалення (Переїхало з AuthController)
    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable UUID id, Principal principal) {
        try {
            userService.deleteUserById(id, principal.getName());
            return "redirect:/admin/users?success";
        } catch (RuntimeException e) {
            return "redirect:/admin/users?error=" + e.getMessage();
        }
    }
}