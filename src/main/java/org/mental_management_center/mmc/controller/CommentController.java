package org.mental_management_center.mmc.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.Comment;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.ArticleService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ArticleService articleService;

    // 1. Створення коментаря під статтею
    @Transactional
    @PostMapping("/articles/{id}/comments")
    public String addComment(
            @PathVariable("id") UUID id,
            @RequestParam String content,
            @RequestParam(required = false) UUID parentId,
            Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = (auth.getPrincipal() instanceof OAuth2User oauth2)
                ? oauth2.getAttribute("email")
                : auth.getName();

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Користувача не знайдено"));
        Article article = articleService.findById(id);

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setArticle(article);
        comment.setAuthor(author);
        comment.setCreatedAt(LocalDateTime.now());

        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(comment::setParentComment);
        }

        commentRepository.save(comment);

        return "redirect:/articles/" + id + "#comment-" + comment.getId();
    }

    // 2. Редагування коментаря (Тільки автор)
    @Transactional
    @PostMapping("/articles/comments/{commentId}/edit")
    public String editComment(
            @PathVariable("commentId") UUID commentId,
            @RequestParam String content,
            Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = (auth.getPrincipal() instanceof OAuth2User oauth2)
                ? oauth2.getAttribute("email")
                : auth.getName();

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Користувача не знайдено"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Коментар не знайдено"));

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Немає прав для редагування");
        }

        comment.setContent(content);
        commentRepository.save(comment);

        return "redirect:/articles/" + comment.getArticle().getId() + "#comment-" + comment.getId();
    }

    // 3. М'яке видалення коментаря (Автор коментаря, Автор статті або Адмін)
    @Transactional
    @PostMapping("/articles/comments/{id}/delete")
    public String deleteComment(
            @PathVariable("id") UUID commentId,
            @RequestParam(value = "reason", required = false) String reason,
            Authentication auth,
            HttpServletRequest request) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = (auth.getPrincipal() instanceof OAuth2User oauth2)
                ? oauth2.getAttribute("email")
                : auth.getName();

        User currentUser = userRepository.findByEmail(email).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }

        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            String referer = request.getHeader("Referer");
            return "redirect:" + (referer != null ? referer : "/articles");
        }

        UUID articleId = (comment.getArticle() != null) ? comment.getArticle().getId() : null;

        // 3 суб'єкти
        boolean isCommenter = comment.getAuthor() != null && comment.getAuthor().getId().equals(currentUser.getId());
        boolean isAuthor = comment.getArticle() != null && comment.getArticle().getAuthor() != null
                && comment.getArticle().getAuthor().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.isAdmin();

        if (!isCommenter && !isAuthor && !isAdmin) {
            throw new AccessDeniedException("Немає прав для видалення цього коментаря");
        }

        // Встановлення відповідного прапорця
        if (isCommenter) {
            comment.setDeletedByCommenter(true);
            comment.setDeletionReason("Видалено коментатором");
        } else if (isAuthor) {
            comment.setDeletedByAuthor(true);
            comment.setDeletionReason((reason != null && !reason.isBlank()) ? reason : "Видалено автором статті");
        } else if (isAdmin) {
            comment.setDeletedByAdmin(true);
            comment.setDeletionReason((reason != null && !reason.isBlank()) ? reason : "Видалено адміністратором");
        }

        commentRepository.save(comment);

        if (articleId != null) {
            return "redirect:/articles/" + articleId;
        }

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/articles");
    }
}