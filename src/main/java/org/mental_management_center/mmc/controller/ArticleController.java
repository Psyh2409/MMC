package org.mental_management_center.mmc.controller;

import jakarta.validation.Valid;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.web.form.ArticleForm;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.service.ArticleService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final UserRepository userRepository;

    @GetMapping("/admin/articles")
    public String listArticles(Model model) {
        model.addAttribute("articles", articleService.findAll());
        return "admin-articles";
    }

    @PostMapping("/admin/articles/delete/{id}")
    public String deleteArticle(@PathVariable UUID id) {
        articleService.deleteArticle(id);
        return "redirect:/admin/articles";
    }

    @GetMapping("/admin/articles/create")
    public String showCreateForm(Model model) {
        model.addAttribute("articleForm", new ArticleForm());
        return "article-form";
    }

    @PostMapping("/admin/articles/create")
    public String createArticle(@Valid @ModelAttribute("articleForm") ArticleForm form,
                                BindingResult result) {
        
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
                return "article-form";
            }
            articleService.saveFromForm(form, currentUser);
            return "redirect:/admin/articles";
        } else {
            System.out.println("КРИТИЧНО: Користувач не знайдений або не має прав ADMIN: " + email);
            return "redirect:/login?error=access_denied";
        }
    }
}