package org.mental_management_center.mmc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.Comment;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.ArticleService;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.web.form.ArticleForm;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final CommentRepository commentRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;

    @GetMapping("/admin/articles")
    public String listArticles(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (auth != null && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
                email = oauth2User.getAttribute("email");
            } else {
                email = auth.getName();
            }
        }

        // Передаємо email поточного користувача у модель
        model.addAttribute("currentUserEmail", email);
        model.addAttribute("allArticles", articleService.findAll());
        return "admin-articles";
    }

    @PostMapping("/admin/articles/delete/{id}")
    public String deleteArticle(@PathVariable UUID id) {
        // Windsurf: Адмін може видаляти будь-які статті (включно з чужими)
        articleService.deleteArticle(id);
        return "redirect:/admin/articles";
    }

    // 2. ОНОВЛЮЄМО МЕТОД СТВОРЕННЯ (GET)
    @GetMapping("/admin/articles/create")
    public String showCreateForm(Model model) {
        // ЗМІНА ТУТ: змінюємо назву ключа з "articleForm" на "article", щоб Thymeleaf його побачив.
        // Сам об'єкт new ArticleForm() залишається без змін!
        model.addAttribute("articleForm", new ArticleForm());

        // Передаємо всі існуючі категорії з бази даних у форму
        model.addAttribute("categories", categoryTranslationRepository.findAll());

        // Передаємо URL, куди форма має відправляти дані після натискання "Зберегти"
        model.addAttribute("actionUrl", "/admin/articles/save");
        model.addAttribute("formTitle", "Створення нової статті");

        return "article-form";
    }

    // 3. ОНОВЛЮЄМО МЕТОД РЕДАГУВАННЯ (GET)
    @GetMapping("/admin/articles/edit/{id}")
    public String editArticle(@PathVariable UUID id, Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("User: " + auth.getName());
        System.out.println("Authorities: " + auth.getAuthorities());

        Article article = articleService.findById(id);

        // Windsurf: Адмін може редагувати тільки СВОЇ статті
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email;
        if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
            email = oauth2User.getAttribute("email");
        } else {
            email = auth.getName();
        }
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser != null && !article.getAuthor().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Адмін може редагувати тільки свої статті");
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

        model.addAttribute("actionUrl", "/admin/articles/save");

        model.addAttribute("articleForm", form);

        // Передаємо всі існуючі категорії з бази даних і сюди теж!
        model.addAttribute("categories", categoryTranslationRepository.findAll());

        return "article-form";
    }

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

    // 1. Публічний перегляд статті та розрахунок сторінки за commentId
    @Transactional(readOnly = true)
    @GetMapping("/articles/{id}")
    public String getArticle(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID commentId,
            Model model) {

        Article article = articleService.findById(id);

        // Якщо прийшов запит на конкретний коментар — розраховуємо його сторінку
        if (commentId != null) {
            Comment targetComment = commentRepository.findById(commentId).orElse(null);
            if (targetComment != null) {
                // Якщо це відповідь (дочірній коментар), піднімаємося до кореневого
                Comment rootComment = targetComment;
                while (rootComment.getParentComment() != null) {
                    rootComment = rootComment.getParentComment();
                }

                long newerCount = commentRepository.countRootCommentsNewerThan(id, rootComment.getCreatedAt());
                page = (int) (newerCount / size);
            }
        }

        var commentsPage = articleService.getCommentsForArticle(article, page, size);

        model.addAttribute("article", article);
        model.addAttribute("comments", commentsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", commentsPage.getTotalPages());
        model.addAttribute("pageSize", size);

        return "article";
    }

    // 2. Обробка відправки форми коментаря
    @PostMapping("/articles/{id}/comments")
    public String addComment(
            @PathVariable UUID id,
            @RequestParam String content,
            @RequestParam(required = false) UUID parentId,
            org.springframework.security.core.Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2)
                ? oauth2.getAttribute("email")
                : auth.getName();

        User author = userRepository.findByEmail(email).orElseThrow();
        Article article = articleService.findById(id);

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setArticle(article);
        comment.setAuthor(author);
        comment.setCreatedAt(java.time.LocalDateTime.now());

        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(comment::setParentComment);
        }

        commentRepository.save(comment);

        return "redirect:/articles/" + id + "#comment-" + comment.getId();
    }
}