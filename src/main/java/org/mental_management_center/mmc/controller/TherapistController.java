package org.mental_management_center.mmc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.TherapyAssignmentRepository;
import org.mental_management_center.mmc.service.*;
import org.mental_management_center.mmc.web.form.ArticleForm;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

@Controller
@RequestMapping("/therapist")
@RequiredArgsConstructor
public class TherapistController {

    private final UserService userService;
    private final TherapyAssignmentService assignmentService;
    private final ArticleService articleService; // Windsurf: Додано для роботи зі статтями
    private final CategoryTranslationRepository categoryTranslationRepository; // Windsurf: Для категорій
    private final PublicPostService publicPostService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CommentRepository commentRepository;
    private final ConsultationRequestService consultationRequestService;
    private final TherapyAssignmentService therapyAssignmentService;
    private final TherapyAssignmentRepository therapyAssignmentRepository;

    // Доступ ТІЛЬКИ для авторизованих (Читач, Клієнт, Адмін, інший Терапевт)
    // Гостя автоматично перекине на /login
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public/{id}")
    @Transactional(readOnly = true)
    public String showPublicProfile(@PathVariable UUID id, Model model, Principal principal) {
        User therapist = Optional.ofNullable(userService.findById(id))
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        // Захист: щоб ніхто не міг відкрити "візитку" звичайного читача
        if (!therapist.isTherapist()) {
            throw new RuntimeException("Цей користувач не є фахівцем");
        }

        // 1. Отримуємо email поточного авторизованого користувача
        String currentEmail = principal.getName();

        // 2. Дістаємо об'єкт User з бази по email
        // УВАГА: Якщо в твоєму UserService цей метод називається інакше
        // (наприклад, getUserByEmail), просто зміни назву тут.
        User currentUser = userService.findByEmail(currentEmail).orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // 3. Перевіряємо, чи можна показати кнопку
        boolean canRequest = assignmentService.canRequestTherapy(currentUser, therapist);
        model.addAttribute("canRequestTherapy", canRequest);

        model.addAttribute("therapist", therapist);

        // Windsurf: Отримуємо статті цього фахівця для публічного перегляду
        model.addAttribute("therapistArticles", articleService.findByAuthorId(therapist.getId()));
        // therapist - це той фахівець, чию візитку зараз відкрили
        model.addAttribute("publicPosts", publicPostService.getPostsByAuthor(therapist.getId(), 0, 20));

        // Завантаження коментарів для публічної стіни відвідувачів
        Page<PublicPost> postsPage = publicPostService.getPostsByAuthor(therapist.getId(), 0, 50);
        model.addAttribute("wallPosts", postsPage);

        Map<UUID, List<Comment>> postCommentsMap = new HashMap<>();
        for (PublicPost post : postsPage.getContent()) {
            List<Comment> comments = commentRepository.findCommentsWithTreeByPublicPost(post);
            postCommentsMap.put(post.getId(), comments);
        }
        model.addAttribute("postCommentsMap", postCommentsMap);

        return "therapist-public";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/request-therapy/{therapistId}")
    public String requestTherapy(@PathVariable UUID therapistId,
                                 @RequestParam(value = "message", required = false) String message,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {

        User client = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        try {
            consultationRequestService.createRequest(client, therapistId, message);
            redirectAttributes.addFlashAttribute("success", "Ваш запит успішно відправлено! Очікуйте на підтвердження фахівця.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Сталася помилка при надсиланні запиту.");
        }

        return "redirect:/therapist/public/" + therapistId;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public String therapistDashboard(Principal principal, Model model) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        model.addAttribute("user", therapist);

        // 1. Вхідні заявки на консультацію (новий механізм 1:N)
        List<ConsultationRequest> pendingConsultationRequests = consultationRequestService.getPendingRequestsForTherapist(therapist.getId());

        // 2. Існуючі запити (для зворотної сумісності)
        List<TherapyAssignment> pendingRequests = assignmentService.getPendingRequestsForTherapist(therapist.getId());

        // 3. Активні клієнти в терапії
        List<TherapyAssignment> activeAssignments = assignmentService.getAssignmentsByStatus(therapist.getId(), "ACTIVE");

        model.addAttribute("therapist", therapist);
        model.addAttribute("pendingConsultationRequests", pendingConsultationRequests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("activeAssignments", activeAssignments);
        model.addAttribute("publicPosts", publicPostService.getPostsByAuthor(therapist.getId(), 0, 20));

        return "therapist-dashboard";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/dashboard/requests/{id}/accept")
    public String acceptConsultationRequest(@PathVariable UUID id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            User therapist = userService.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            consultationRequestService.acceptRequest(id, therapist);
            redirectAttributes.addFlashAttribute("success", "Заявку успішно прийнято! Клієнта додано до вашої практики.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при прийнятті заявки: " + e.getMessage());
        }
        return "redirect:/therapist/dashboard";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/dashboard/requests/{id}/reject")
    public String rejectConsultationRequest(@PathVariable UUID id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            User therapist = userService.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            consultationRequestService.rejectRequest(id, therapist);
            redirectAttributes.addFlashAttribute("success", "Заявку відхилено.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при відхиленні заявки: " + e.getMessage());
        }
        return "redirect:/therapist/dashboard";
    }

    // Windsurf: Метод для отримання статей терапевта
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/articles")
    public String therapistArticles(Principal principal, Model model) {

        String email = principal.getName();

        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Windsurf: Отримуємо статті цього терапевта
        model.addAttribute("therapistArticles", articleService.findByAuthorId(therapist.getId()));

        model.addAttribute("currentUserEmail", email);

        return "therapist-articles";
    }

    // Windsurf: Метод для створення нової статті (GET)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/articles/create")
    public String showCreateArticleForm(Model model) {
        model.addAttribute("articleForm", new ArticleForm());
        model.addAttribute("categories", categoryTranslationRepository.findAll());
        model.addAttribute("actionUrl", "/therapist/articles/create");
        model.addAttribute("formTitle", "Створення нової статті");
        model.addAttribute("basePath", "/therapist/articles");
        return "article-form";
    }

    // Windsurf: Метод для редагування статті (GET)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/articles/edit/{id}")
    public String showEditArticleForm(@PathVariable UUID id, Principal principal, Model model) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        Article article = articleService.findById(id);

        // Windsurf: Перевіряємо, чи це стаття терапевта
        if (!article.getAuthor().getId().equals(therapist.getId())) {
            throw new RuntimeException("Ви можете редагувати тільки свої статті");
        }

        ArticleForm form = new ArticleForm();
        form.setId(article.getId());
        form.setTitle(article.getTitle());
        form.setDescription(article.getDescription());
        form.setCategory(article.getCategory());
        form.setContent(article.getContent());

        if (article.getTags() != null) {
            form.setTags(String.join(", ", article.getTags()));
        }

        if (article.getCategoryTranslation() != null) {
            form.setCategoryNameUa(article.getCategoryTranslation().getDisplayName());
        }

        model.addAttribute("articleForm", form);
        model.addAttribute("categories", categoryTranslationRepository.findAll());
        model.addAttribute("actionUrl", "/therapist/articles/edit/" + id);
        model.addAttribute("formTitle", "Редагування статті");
        model.addAttribute("basePath", "/therapist/articles");

        return "article-form";
    }

    // Windsurf: Метод для збереження статті (POST)
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/articles/create")
    public String createArticle(@Valid @ModelAttribute("article") ArticleForm form,
                                BindingResult result,
                                Principal principal,
                                Model model) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (result.hasErrors()) {
            model.addAttribute("categories", categoryTranslationRepository.findAll());
            model.addAttribute("actionUrl", "/therapist/articles/create");
            model.addAttribute("formTitle", "Створення нової статті");
            return "article-form";
        }

        articleService.saveFromForm(form, therapist, null);
        return "redirect:/therapist/articles";
    }

    // Windsurf: Метод для оновлення статті (POST)
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/articles/edit/{id}")
    public String updateArticle(@PathVariable UUID id,
                               @Valid @ModelAttribute("article") ArticleForm form,
                               BindingResult result,
                               Principal principal,
                               Model model) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        Article article = articleService.findById(id);

        // Windsurf: Перевіряємо, чи це стаття терапевта
        if (!article.getAuthor().getId().equals(therapist.getId())) {
            throw new RuntimeException("Ви можете редагувати тільки свої статті");
        }

        if (result.hasErrors()) {
            model.addAttribute("categories", categoryTranslationRepository.findAll());
            model.addAttribute("actionUrl", "/therapist/articles/edit/" + id);
            model.addAttribute("formTitle", "Редагування статті");
            return "article-form";
        }

        articleService.saveFromForm(form, therapist, null);
        return "redirect:/therapist/articles";
    }

    // Windsurf: Метод для видалення статті
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/articles/delete/{id}")
    public String deleteArticle(@PathVariable UUID id, Principal principal) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        Article article = articleService.findById(id);

        // Windsurf: Перевіряємо, чи це стаття терапевта
        if (!article.getAuthor().getId().equals(therapist.getId())) {
            throw new RuntimeException("Ви можете видаляти тільки свої статті");
        }

        articleService.deleteArticle(id);
        return "redirect:/therapist/articles";
    }

    // Обробка відправки швидкого сповіщення клієнту з дашборду фахівця
    @PostMapping("/notify-client")
    @PreAuthorize("hasAnyRole('THERAPIST', 'ADMIN')")
    public String notifyClientFromDashboard(
            @RequestParam UUID recipientId,
            @RequestParam String message,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        // 1. Захист від відправки сповіщення самому собі
        if (therapist.getId().equals(recipientId)) {
            redirectAttributes.addFlashAttribute("error", "Ви не можете надіслати сповіщення самому собі.");
            return "redirect:/therapist/dashboard";
        }

        User client = userService.findById(recipientId);
        if (client == null) {
            redirectAttributes.addFlashAttribute("error", "Клієнта не знайдено.");
            return "redirect:/therapist/dashboard";
        }

        // 2. Знаходимо конкретний терапевтичний зв'язок (договір) між цим фахівцем і клієнтом
        TherapyAssignment assignment = therapyAssignmentRepository
                .findByClientIdAndTherapistId(client.getId(), therapist.getId())
                .orElseThrow(() -> new RuntimeException("Активний терапевтичний зв'язок не знайдено."));

        // 3. Формуємо коректну адресу кімнати на основі assignment.getId()
        String roomUrl = "/therapy/room/" + assignment.getId();

        // 1. Створюємо сповіщення у Дзвоник для клієнта
        notificationService.createNotification(
                client,
                "Повідомлення від терапевта",
                therapist.getName() + ": " + message.trim(),
                roomUrl,
                Notification.NotificationType.STANDARD
        );

        // 2. Якщо клієнт надав згоду на Email-сповіщення (Opt-In model)
        if (client.isEmailNotificationsEnabled() && client.getEmail() != null) {
            emailService.sendNotificationEmail(
                    client.getEmail(),
                    "🌿 Нове повідомлення від фахівця | MMC",
                    "Ваш терапевт " + therapist.getName() + " залишив повідомлення:\n\n\"" + message.trim() + "\"\n\nПерейти до кабінету: /therapy/room/" + client.getId(),
                    roomUrl
            );
        }

        redirectAttributes.addFlashAttribute("success", "Повідомлення клієнту успішно надіслано!");
        return "redirect:/therapist/dashboard";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/dashboard/requests/{id}/block")
    public String blockConsultationRequest(@PathVariable UUID id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            User therapist = userService.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            consultationRequestService.blockRequest(id, therapist);
            redirectAttributes.addFlashAttribute("success", "Заявку заблоковано остаточно.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка: " + e.getMessage());
        }
        return "redirect:/therapist/dashboard";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/dashboard/clients/{clientId}/terminate")
    public String terminateTherapy(@PathVariable UUID clientId, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            User therapist = userService.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            therapyAssignmentService.terminateTherapy(clientId, therapist);
            redirectAttributes.addFlashAttribute("success", "Терапію успішно завершено.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при завершенні терапії: " + e.getMessage());
        }
        return "redirect:/therapist/dashboard";
    }
}