package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.service.ArticleService;
import org.mental_management_center.mmc.service.SpecialistService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ContentController {

    private final ArticleService articleService;
    private final SpecialistService specialistService;

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
    public String showSpecialistsDirectory(Model model) {
        model.addAttribute("specialists", specialistService.getApprovedSpecialists());
        return "specialists";
    }

}