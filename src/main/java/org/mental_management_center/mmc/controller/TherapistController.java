package org.mental_management_center.mmc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
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
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {

        // Знаходимо того, хто клікнув (Клієнт)
        User client = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Знаходимо фахівця, до якого клікнули
        User therapist = Optional.ofNullable(userService.findById(therapistId))
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        // Захист від дурня: не можна подати запит самому собі
        if (client.getId().equals(therapist.getId())) {
            redirectAttributes.addFlashAttribute("error", "Ви не можете надіслати запит самому собі.");
            return "redirect:/therapist/public/" + therapistId;
        }

        try {
            assignmentService.sendRequest(client, therapist);
            redirectAttributes.addFlashAttribute("success", "Ваш запит успішно відправлено! Очікуйте на підтвердження фахівця.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        // Повертаємо користувача назад на сторінку візитки
        return "redirect:/therapist/public/" + therapistId;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public String therapistDashboard(Principal principal, Model model) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        model.addAttribute("user", therapist);

        // Дістаємо всі запити, які чекають на відповідь
        List<TherapyAssignment> pendingRequests = assignmentService.getPendingRequestsForTherapist(therapist.getId());

        // НОВЕ: Дістаємо активних клієнтів, щоб передати їх в HTML сторінку
        List<TherapyAssignment> activeAssignments = assignmentService.getAssignmentsByStatus(therapist.getId(), "ACTIVE");

        model.addAttribute("therapist", therapist);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("activeAssignments", activeAssignments); // Передаємо список на фронтенд
        // Витягуємо першу сторінку постів (наприклад, 20 останніх)
        model.addAttribute("publicPosts", publicPostService.getPostsByAuthor(therapist.getId(), 0, 20));

        return "therapist-dashboard";
    }

    // 2. Обробка натискання кнопки "Прийняти"
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/dashboard/accept/{id}")
    public String acceptRequest(@PathVariable UUID id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            // Знаходимо терапевта, який зараз натиснув кнопку
            User therapist = userService.findByEmail(principal.getName()).orElseThrow();

            // Передаємо id запиту ТА терапевта в сервіс
            assignmentService.acceptRequest(id, therapist);

            redirectAttributes.addFlashAttribute("success", "Клієнта успішно додано до вашої практики!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при прийнятті запиту: " + e.getMessage());
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

        User client = userService.findById(recipientId);
        if (client == null) {
            redirectAttributes.addFlashAttribute("error", "Клієнта не знайдено.");
            return "redirect:/therapist/dashboard";
        }

        // 1. Створюємо сповіщення у Дзвоник для клієнта
        notificationService.createNotification(
                client,
                "Повідомлення від терапевта",
                therapist.getName() + ": " + message.trim(),
                "/therapy/room/" + client.getId(), // Прямий перехід у терапевтичну кімнату
                Notification.NotificationType.STANDARD
        );

        // 2. Якщо клієнт надав згоду на Email-сповіщення (Opt-In model)
        if (client.isEmailNotificationsEnabled() && client.getEmail() != null) {
            emailService.sendNotificationEmail(
                    client.getEmail(),
                    "🌿 Нове повідомлення від фахівця | MMC",
                    "Ваш терапевт " + therapist.getName() + " залишив повідомлення:\n\n\"" + message.trim() + "\"\n\nПерейти до кабінету: /therapy/room/" + client.getId(),
                    "/therapy/room/" + client.getId()
            );
        }

        redirectAttributes.addFlashAttribute("success", "Повідомлення клієнту успішно надіслано!");
        return "redirect:/therapist/dashboard";
    }

}