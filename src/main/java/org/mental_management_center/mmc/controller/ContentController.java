package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.service.SpecialistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import org.mental_management_center.mmc.service.ArticleService;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.Comment;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.UserRepository;

@Controller
@RequiredArgsConstructor
public class ContentController {

    private final ArticleService articleService;
    private final ArticleRepository articleRepository;
    private final SpecialistService specialistService;
    // ДОДАНО: Репозиторії для коментарів та юзерів
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @GetMapping("/articles")
    public String articles(Model model) {
        List<Article> articles = articleService.findAll();
        model.addAttribute("articles", articles);
        return "articles";
    }

    @Transactional(readOnly = true)
    @GetMapping("/articles/{id}")
    public String getArticle(@PathVariable UUID id,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Model model) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Статтю не знайдено"));

        model.addAttribute("article", article);

        // 1. Створюємо об'єкт запиту сторінки
        Pageable pageable = PageRequest.of(page, size);

        // 2. Отримуємо сторінку ТІЛЬКИ кореневих коментарів (без фетчу відповідей, щоб не ламати БД)
        Page<Comment> commentsPage = commentRepository.findTopLevelCommentsByArticle(article, pageable);

        // 3. Передаємо сам список коментарів (щоб не ламати старий HTML)
        model.addAttribute("comments", commentsPage.getContent());

        // 4. Передаємо змінні для кнопок пагінації
        model.addAttribute("currentPage", commentsPage.getNumber());
        model.addAttribute("totalPages", commentsPage.getTotalPages());
        model.addAttribute("pageSize", commentsPage.getSize());

        return "article";
    }

    // ОНОВЛЕНИЙ МЕТОД ДОДАВАННЯ КОМЕНТАРЯ (тепер з parentId)
    @PostMapping("/articles/{id}/comments")
    public String addComment(@PathVariable UUID id,
                             @RequestParam("content") String content,
                             @RequestParam(value = "parentId", required = false) String parentIdStr, // ЗМІНЕНО НА String
                             Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Статтю не знайдено"));

        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            email = oauth2User.getAttribute("email");
        }

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено в базі"));

        // БЕЗПЕЧНА ПЕРЕВІРКА ТА КОНВЕРТАЦІЯ UUID
        Comment parent = null;
        if (parentIdStr != null && !parentIdStr.isBlank()) {
            try {
                UUID parentId = UUID.fromString(parentIdStr);
                parent = commentRepository.findById(parentId).orElse(null);
            } catch (IllegalArgumentException e) {
                System.out.println("Невірний формат parentId: " + parentIdStr);
            }
        }

        Comment comment = Comment.builder()
                .content(content)
                .article(article)
                .author(author)
                .parentComment(parent)
                .build();

        commentRepository.save(comment);

        return "redirect:/articles/" + id;
    }

    @GetMapping("/first-steps")
    public String firstSteps() {
        return "first-steps";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/specialists")
    public String showSpecialistsDirectory(Model model) {
        model.addAttribute("specialists", specialistService.getApprovedSpecialists());
        return "specialists";
    }

}