package org.mental_management_center.mmc.controller;

import jakarta.validation.Valid;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.web.form.ArticleForm;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.service.ArticleService;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    // Якщо в тебе Lombok @RequiredArgsConstructor, просто додай:
    private final CommentRepository commentRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;

    @GetMapping("/admin/articles")
    public String listArticles(Model model) {
        model.addAttribute("articles", articleService.findAll());
        return "admin-articles";
    }
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/admin/articles/delete/{id}")
    public String deleteArticle(@PathVariable UUID id) {
        articleService.deleteArticle(id);
        return "redirect:/admin/articles";
    }

    // 2. ОНОВЛЮЄМО МЕТОД СТВОРЕННЯ (GET)
    @GetMapping("/admin/articles/create")
    public String showCreateForm(Model model) {
        model.addAttribute("articleForm", new ArticleForm());

        // Передаємо всі існуючі категорії з бази даних у форму
        model.addAttribute("categories", categoryTranslationRepository.findAll());

        return "article-form";
    }

    // 3. ОНОВЛЮЄМО МЕТОД РЕДАГУВАННЯ (GET)
    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @GetMapping("/admin/articles/edit/{id}")
    public String editArticle(@PathVariable UUID id, Model model) {
        Article article = articleService.findById(id);

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

        // Передаємо всі існуючі категорії з бази даних і сюди теж!
        model.addAttribute("categories", categoryTranslationRepository.findAll());

        return "article-form";
    }

    @PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')") // ТІЛЬКИ реальний адмін
    @PostMapping("/admin/articles/create")
    public String createArticle(@Valid @ModelAttribute("articleForm") ArticleForm form,
                                BindingResult result,
                                @RequestParam(value = "mediaFile", required = false) MultipartFile mediaFile,
                                Model model) { // <--- 1. ДОДАНО ПАРАМЕТР Model

        // --- ДІАГНОСТИКА: ЩО ПРИЙШЛО З ФОРМИ ---
        System.out.println("=== СТАРТ ЗБЕРЕЖЕННЯ СТАТТІ ===");
        System.out.println("Title: " + form.getTitle());
        System.out.println("Category: " + form.getCategory());
        System.out.println("Has Errors? " + result.hasErrors());
        // ---------------------------------------

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email;
        // Універсальний спосіб отримання email
        if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
            email = oauth2User.getAttribute("email");
        } else {
            email = auth.getName(); // Для звичайного Form Login (Username)
        }

        System.out.println("DEBUG: Спроба публікації від: " + email);

        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser != null && currentUser.isAdmin()) {
            if (result.hasErrors()) {

                // ПРИМУСОВИЙ ВИВІД ПОМИЛОК В КОНСОЛЬ
                result.getFieldErrors().forEach(e ->
                        System.err.println("ПОМИЛКА В ПОЛІ " + e.getField() + ": " + e.getDefaultMessage()));

                // <--- 2. ДОДАНО: Повертаємо категорії на форму, щоб список не зникав!
                model.addAttribute("categories", categoryTranslationRepository.findAll());
                return "article-form";
            }

            String savedFileName = null;
            // Перевіряємо, чи користувач дійсно прикріпив файл
            if (mediaFile != null && !mediaFile.isEmpty()) {
                // Зберігаємо файл на диск і отримуємо його унікальне ім'я (тепер уже хеш)
                savedFileName = fileStorageService.storeFile(mediaFile);
            }

            // Передаємо ім'я файлу в сервіс
            articleService.saveFromForm(form, currentUser, savedFileName);
            return "redirect:/admin/articles";
        } else {
            System.out.println("КРИТИЧНО: Користувач не знайдений або не має прав ADMIN: " + email);
            return "redirect:/login?error=access_denied";
        }
    }
}