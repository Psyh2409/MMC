package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.*;
import org.mental_management_center.mmc.service.EmailService;
import org.mental_management_center.mmc.service.NotificationService;
import org.mental_management_center.mmc.service.SpecialistService;
import org.mental_management_center.mmc.service.TherapyAssignmentService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin") // Цей префікс додається до всіх методів нижче
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final SiteStatsRepository siteStatsRepository;
    private final SpecialistAppRepository specialistAppRepository;
    private final SpecialistService specialistService;
    private final TherapyAssignmentService therapyAssignmentService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ReportRepository reportRepository;
    private final RequestRepository requestRepository;

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
        model.addAttribute("pendingReportsCount", reportRepository.countByStatus(org.mental_management_center.mmc.model.Report.ReportStatus.PENDING));
        model.addAttribute("newRequestsCount", requestRepository.countUnprocessedAdminRequests());

        UUID statsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SiteStats siteStats = siteStatsRepository.findById(statsId).orElse(new SiteStats());
        model.addAttribute("totalVisits", siteStats.getGuestVisits());
        // У методі showAdminUsers:
        Page<SpecialistApplication> pendingApps = specialistAppRepository.findByStatus("PENDING", PageRequest.of(0, 50));
        model.addAttribute("pendingApplications", pendingApps.getContent());

        java.util.Map<UUID, String> approvalDates = therapyAssignmentService.getActiveApprovalDatesMap();
        model.addAttribute("approvalDates", approvalDates);
        return "admin-users";
    }

    // 2. Тотальний бан (Сайт)
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable UUID id, @RequestParam("reason") String reason) {
        User user = userRepository.findById(id).orElseThrow();
        userService.toggleUserStatus(id);

        User updatedUser = userRepository.findById(id).orElseThrow();
        if (!updatedUser.isEnabled()) {
            emailService.sendNotificationEmail(
                    updatedUser.getEmail(),
                    "Обмеження доступу до сайту",
                    "Ваш доступ до платформи Mental Management Center заблоковано. Причина: " + reason,
                    null
            );
        }
        return "redirect:/admin/users?success";
    }

    // 3. Бан в чаті
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')")
    @PostMapping("/toggle-chat/{id}")
    public String toggleChatStatus(@PathVariable UUID id, @RequestParam("reason") String reason) {
        User user = userRepository.findById(id).orElseThrow();
        userService.toggleChatStatus(id); // Виконуємо перемикання

        // Отримуємо актуальний статус з бази після перемикання
        User updatedUser = userRepository.findById(id).orElseThrow();
        if (!updatedUser.isChatEnabled()) {
            notificationService.createNotification(
                    updatedUser,
                    "Обмеження доступу до чату",
                    "Ваш доступ до використання чату заблоковано. Причина: " + reason,
                    null,
                    Notification.NotificationType.ADMIN_ALERT
            );
        }
        return "redirect:/admin/users?success";
    }

    // 4. Бан в коментарях
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/toggle-comments/{id}")
    public String toggleCommentsStatus(@PathVariable UUID id, @RequestParam("reason") String reason) {
        User user = userRepository.findById(id).orElseThrow();
        userService.toggleCommentsStatus(id);

        User updatedUser = userRepository.findById(id).orElseThrow();
        if (!updatedUser.isCommentsEnabled()) {
            notificationService.createNotification(
                    updatedUser,
                    "Обмеження доступу до коментарів",
                    "Ваш доступ до написання коментарів заблоковано. Причина: " + reason,
                    null,
                    Notification.NotificationType.ADMIN_ALERT
            );
        }
        return "redirect:/admin/users?success";
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
        User user = userRepository.findById(id).orElseThrow();
        try {
            emailService.sendNotificationEmail(
                    user.getEmail(),
                    "Ваш акаунт видалено",
                    "Ваш акаунт на платформі Mental Management Center було видалено адміністратором.",
                    null
            );
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

    @PostMapping("/users/{id}/resolve-sos")
    public String resolveSos(@PathVariable UUID id) {
        userService.resolveSos(id);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/send-message")
    public String sendAdminMessage(@PathVariable UUID id,
                                   @RequestParam("message") String message,
                                   @RequestParam(value = "requireReply", required = false) boolean requireReply) {
        User targetUser = userRepository.findById(id).orElseThrow();

        // Якщо галочка стоїть - відправляємо в контакти. Якщо ні - targetUrl пустий
        String targetUrl = requireReply ? "/contact" : null;

        notificationService.createNotification(
                targetUser,
                "Повідомлення від Адміністратора",
                message,
                targetUrl,
                Notification.NotificationType.ADMIN_ALERT
        );

        return "redirect:/admin/users?success";
    }
}