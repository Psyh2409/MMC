package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.model.SiteStats;
import org.mental_management_center.mmc.model.SpecialistApplication;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SiteStatsRepository;
import org.mental_management_center.mmc.repository.SpecialistAppRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.SpecialistService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
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
    @Autowired
    private SpecialistAppRepository specialistAppRepository;
    @Autowired
    private SpecialistService specialistService;

    // 1. Сторінка списку користувачів (Переїхала з AuthController)
    @GetMapping("/users")
    public String showAdminUsers(Model model, Principal principal) {
        // 2. Знаходимо поточного юзера (щоб знати, чи він TEST)
        User currentUser = userRepository.findByEmail(principal.getName()).orElseThrow();
        // 3. Отримуємо відфільтрований список (тільки реальні АБО тільки тестові)
        List<User> visibleUsers = userService.getVisibleUsers(currentUser);
        // 4. Сортуємо цей список за спаданням дати створення (те, що раніше робив Sort.by)
        visibleUsers.sort(Comparator.comparing(User::getCreatedAt).reversed());
        model.addAttribute("allUsers", visibleUsers);
        model.addAttribute("countUsers", userRepository.countByRoleMask(RoleBit.READER.getMask()));
        model.addAttribute("countClients", userRepository.countByRoleMask(RoleBit.CLIENT.getMask()));

        UUID statsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SiteStats siteStats = siteStatsRepository.findById(statsId).orElse(new SiteStats());
        model.addAttribute("totalVisits", siteStats.getGuestVisits());
        // У методі showAdminUsers:
        Page<SpecialistApplication> pendingApps = specialistAppRepository.findByStatus("PENDING", PageRequest.of(0, 50));
        model.addAttribute("pendingApplications", pendingApps.getContent());

        return "admin-users";
    }

    // 2. Тотальний бан (Сайт)
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable UUID id) {
        userService.toggleUserStatus(id);
        return "redirect:/admin/users?success";
    }

    // 3. Бан в чаті
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/toggle-chat/{id}")
    public String toggleChatStatus(@PathVariable UUID id) {
        userService.toggleChatStatus(id);
        return "redirect:/admin/users?success";
    }

    // 4. Бан в коментарях
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/toggle-comments/{id}")
    public String toggleCommentsStatus(@PathVariable UUID id) {
        userService.toggleCommentsStatus(id);
        return "redirect:/admin/users?success";
    }

    // 5. Призначення клієнтом (Переїхало з AuthController)
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
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
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін може видаляти, тестовий - ні
    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable UUID id, Principal principal) {
        try {
            userService.deleteUserById(id, principal.getName());
            return "redirect:/admin/users?success";
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage().replaceAll("[\r\n]", " ");
            String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            return "redirect:/admin/users?error=" + encodedError;
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/verify-specialist/{id}")
    public String verifySpecialist(@PathVariable UUID id) {
        // Викликаємо єдину точку входу в бізнес-процес
        specialistService.approveVerification(id);
        return "redirect:/admin/users?success";
    }
}