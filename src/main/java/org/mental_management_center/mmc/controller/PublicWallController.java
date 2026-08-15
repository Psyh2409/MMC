package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Comment;
import org.mental_management_center.mmc.model.PublicPost;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.service.PublicPostService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/therapist/public-wall")
@RequiredArgsConstructor
public class PublicWallController {

    private final PublicPostService publicPostService;
    private final UserService userService;
    private final CommentRepository commentRepository;


    @GetMapping
    @PreAuthorize("hasRole('THERAPIST')")
    @Transactional(readOnly = true)
    public String showPublicWall(Model model, Principal principal) {

        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Витягуємо пости фахівця (наприклад, першу сторінку на 50 постів)
        // Твій шаблон очікує об'єкт з назвою "wallPosts" (ти використовуєш wallPosts.content)
        Page<PublicPost> wallPosts = publicPostService.getPostsByAuthor(therapist.getId(), 0, 50);
        model.addAttribute("wallPosts", wallPosts);

        Map<UUID, List<Comment>> postCommentsMap = new HashMap<>();
        for (PublicPost post : wallPosts.getContent()) {
            List<Comment> comments = commentRepository.findCommentsWithTreeByPublicPost(post);
            postCommentsMap.put(post.getId(), comments);
        }
        model.addAttribute("postCommentsMap", postCommentsMap);
        // Повертаємо назву твого HTML-файлу
        return "therapist-public-wall";
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('THERAPIST')")
    public String createPost(@RequestParam("content") String content,
                             @RequestParam(value = "mediaFile", required = false) MultipartFile mediaFile,
                             Principal principal) {
        User therapist = userService.findByEmail(principal.getName()).orElseThrow();
        publicPostService.createPost(therapist, content, mediaFile);
        return "redirect:/therapist/public-wall"; // ВИПРАВЛЕНО
    }

    @PostMapping("/delete/{id}")
    @PreAuthorize("hasRole('THERAPIST')")
    public String deletePost(@PathVariable UUID id, Principal principal) {
        User therapist = userService.findByEmail(principal.getName()).orElseThrow();
        publicPostService.deletePost(id, therapist);
        return "redirect:/therapist/public-wall"; // ВИПРАВЛЕНО
    }

    // НОВИЙ МЕТОД ДЛЯ РЕДАГУВАННЯ
    @PostMapping("/edit/{id}")
    @PreAuthorize("hasRole('THERAPIST')")
    public String editPost(@PathVariable UUID id, @RequestParam("content") String content, Principal principal) {
        User therapist = userService.findByEmail(principal.getName()).orElseThrow();
        publicPostService.updatePost(id, content, therapist);
        return "redirect:/therapist/public-wall";
    }
    // Повертаємо фахівця назад у його кабінет return "redirect:/therapist/dashboard#wall-tab";

}