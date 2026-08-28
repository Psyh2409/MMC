package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.SpecialistApplication;
import org.mental_management_center.mmc.service.ArticleService;
import org.mental_management_center.mmc.service.SpecialistService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ContentController {

    private final ArticleService articleService;
    private final SpecialistService specialistService;
    private final UserService userService;

    @GetMapping("/articles")
    public String articles(Model model) {
        List<Article> articles = articleService.findAll();
        model.addAttribute("articles", articles);
        return "articles";
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
    public String showSpecialistsDirectory(Model model, Principal principal) {
        // 1. Визначаємо статус глядача (якщо principal == null, вважаємо його реальним гостем)
        boolean isTestViewer = false;
        if (principal != null) {
            isTestViewer = userService.findByEmail(principal.getName())
                    .map(u -> u.isTest())
                    .orElse(false);
        }

        // 2. Отримуємо весь список підтверджених фахівців
        List<SpecialistApplication> specialists = specialistService.getApprovedSpecialists();

        // 3. Фільтруємо за аналогією з RequestController
        final boolean testMode = isTestViewer;
        List<SpecialistApplication> filteredSpecialists = specialists.stream()
                .filter(app -> app.getUser() != null && (testMode
                        ? app.getUser().isTest()
                        : !app.getUser().isTest()))
                .toList();

        model.addAttribute("specialists", filteredSpecialists);
        return "specialists";
    }

}