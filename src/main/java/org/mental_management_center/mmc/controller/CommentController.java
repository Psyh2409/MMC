package org.mental_management_center.mmc.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.PublicPostRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.ArticleService;
import org.mental_management_center.mmc.service.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ArticleService articleService;
    private final NotificationService notificationService;
    private final PublicPostRepository publicPostRepository;

    // 1. Створення коментаря під статтею (з відображенням автора та тексту)
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
        
        if (!author.isCommentsEnabled()) {
            throw new AccessDeniedException("Ваш доступ до коментарів заблоковано");
        }
        
        Article article = articleService.findById(id);

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setArticle(article);
        comment.setAuthor(author);
        comment.setCreatedAt(LocalDateTime.now());

        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(comment::setParentComment);
        }

        Comment savedComment = commentRepository.save(comment);

        // --- БЛОК СПОВІЩЕНЬ ---
        User articleAuthor = article.getAuthor();

        // 1. Сповіщення для автора статті (якщо коментує не він сам)
        if (articleAuthor != null && !articleAuthor.getId().equals(author.getId())) {
            notificationService.createNotification(
                    articleAuthor,
                    "Новий коментар до статті",
                    author.getName() + ": " + comment.getContent(), // Формат: "Ім'я: Текст коментаря"
                    "/articles/" + id + "#comment-" + savedComment.getId(),
                    Notification.NotificationType.STANDARD
            );
        }

        // 2. Сповіщення для автора батьківського коментаря (якщо це відповідь у гілці)
        if (comment.getParentComment() != null) {
            User parentCommentAuthor = comment.getParentComment().getAuthor();
            if (parentCommentAuthor != null
                    && !parentCommentAuthor.getId().equals(author.getId())
                    && (articleAuthor == null || !parentCommentAuthor.getId().equals(articleAuthor.getId()))) {

                notificationService.createNotification(
                        parentCommentAuthor,
                        "Відповідь на ваш коментар",
                        author.getName() + ": " + comment.getContent(), // Формат: "Ім'я: Текст відповіді"
                        "/articles/" + id + "#comment-" + savedComment.getId(),
                        Notification.NotificationType.STANDARD
                );
            }
        }

        return "redirect:/articles/" + id + "#comment-" + savedComment.getId();
    }

    // 2. УНІВЕРСАЛЬНЕ РЕДАГУВАННЯ (і для статей, і для стіни)
    @Transactional
    @PostMapping("/articles/comments/{commentId}/edit")
    public String editComment(
            @PathVariable("commentId") UUID commentId,
            @RequestParam String content,
            Authentication auth,
            HttpServletRequest request) { // ДОДАЛИ HttpServletRequest

        if (auth == null || !auth.isAuthenticated()) return "redirect:/login";

        String email = (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2)
                ? oauth2.getAttribute("email")
                : auth.getName();

        User currentUser = userRepository.findByEmail(email).orElseThrow();
        Comment comment = commentRepository.findById(commentId).orElseThrow();

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Немає прав");
        }

        comment.setContent(content);
        commentRepository.save(comment);

        // Безпечний редирект залежно від того, де залишено коментар
        if (comment.getArticle() != null) {
            return "redirect:/articles/" + comment.getArticle().getId() + "#comment-" + comment.getId();
        } else if (comment.getPublicPost() != null) {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                int hashIndex = referer.indexOf('#');
                String cleanReferer = (hashIndex != -1) ? referer.substring(0, hashIndex) : referer;
                return "redirect:" + cleanReferer + "#comment-" + comment.getId();
            }
        }
        return "redirect:/";
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

        boolean isCommenter = comment.getAuthor() != null && comment.getAuthor().getId().equals(currentUser.getId());
        // Змінюємо перевірку isAuthor:
        boolean isAuthor = (comment.getArticle() != null && comment.getArticle().getAuthor() != null && comment.getArticle().getAuthor().getId().equals(currentUser.getId()))
                || (comment.getPublicPost() != null && comment.getPublicPost().getAuthor() != null && comment.getPublicPost().getAuthor().getId().equals(currentUser.getId()));
        boolean isAdmin = currentUser.isAdmin();

        if (!isCommenter && !isAuthor && !isAdmin) {
            throw new AccessDeniedException("Немає прав для видалення цього коментаря");
        }

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

    // 1. СТВОРЕННЯ КОМЕНТАРЯ (Змінили URL, щоб Spring Security не блокував звичайних юзерів)
    @Transactional
    @PostMapping("/public-posts/{postId}/comments")
    public String addPublicPostComment(
            @PathVariable("postId") UUID postId,
            @RequestParam String content,
            @RequestParam(required = false) UUID parentId,
            Authentication auth,
            HttpServletRequest request) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2)
                ? oauth2.getAttribute("email")
                : auth.getName();

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Користувача не знайдено"));

        if (!author.isCommentsEnabled()) {
            throw new org.springframework.security.access.AccessDeniedException("Ваш доступ до коментарів заблоковано");
        }

        PublicPost post = publicPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Пост не знайдено"));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setPublicPost(post);
        comment.setAuthor(author);
        comment.setCreatedAt(java.time.LocalDateTime.now());

        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(comment::setParentComment);
        }

        Comment savedComment = commentRepository.save(comment);

        // Власник публічної стіни (терапевт)
        User postAuthor = post.getAuthor();

        // ЄДИНИЙ ПУБЛІЧНИЙ URL ДЛЯ ВСІХ РОЛЕЙ (Терапевт, Клієнт, Читач)
        String targetPublicUrl = (postAuthor != null)
                ? "/therapist/public/" + postAuthor.getId() + "#comment-" + savedComment.getId()
                : "/#comment-" + savedComment.getId();

        // 1. Сповіщення для терапевта (якщо коментує не він сам)
        if (postAuthor != null && !postAuthor.getId().equals(author.getId())) {
            notificationService.createNotification(
                    postAuthor,
                    "Новий коментар на вашій стіні",
                    author.getName() + ": " + comment.getContent(),
                    targetPublicUrl,
                    Notification.NotificationType.STANDARD
            );
        }

        // 2. Сповіщення для автора батьківського коментаря (відповідь у гілці)
        if (comment.getParentComment() != null) {
            User parentCommentAuthor = comment.getParentComment().getAuthor();
            if (parentCommentAuthor != null
                    && !parentCommentAuthor.getId().equals(author.getId())
                    && (postAuthor == null || !parentCommentAuthor.getId().equals(postAuthor.getId()))) {

                notificationService.createNotification(
                        parentCommentAuthor,
                        "Відповідь на ваш коментар",
                        author.getName() + ": " + comment.getContent(),
                        targetPublicUrl,
                        Notification.NotificationType.STANDARD
                );
            }
        }

        // Безпечний повернення на сторінку, з якої робився запит
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            int hashIndex = referer.indexOf('#');
            String cleanReferer = (hashIndex != -1) ? referer.substring(0, hashIndex) : referer;
            return "redirect:" + cleanReferer + "#comment-" + savedComment.getId();
        }

        return "redirect:" + targetPublicUrl;
    }
}