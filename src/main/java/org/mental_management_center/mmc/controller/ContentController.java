package org.mental_management_center.mmc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import java.util.List;
import java.util.UUID;
import org.mental_management_center.mmc.service.ArticleService;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.repository.ArticleRepository;

@Controller
@RequiredArgsConstructor
public class ContentController {

    private final ArticleService articleService;
    private final ArticleRepository articleRepository; // Якщо потрібно напряму звертатися до репозиторію (наприклад,
                                                       // для пошуку за slug)

    @GetMapping("/articles")
    public String articles(Model model) {
        List<Article> articles = articleService.findAll();

        // ДОДАЙ ЦЕЙ РЯДОК:
        System.out.println("DEBUG: Знайдено статей у базі: " + articles.size());

        model.addAttribute("articles", articles);
        return "articles";
    }

    // @GetMapping("/articles/{slug}")
    // public String getArticleBySlug(@PathVariable String slug, Model model) {
    //     Article article = articleRepository.findBySlug(slug)
    //             .orElseThrow(() -> new ResourceNotFoundException("Сторінку не знайдено"));

    //     model.addAttribute("article", article);
    //     return "article";
    // }

//    @GetMapping("/articles")
//    public String articles(Model model) {
//        // Тут ми маємо отримати список статей з бази
//        List<Article> articles = articleService.findAll();
//        model.addAttribute("articles", articles);
//        return "articles";
//    }

    @GetMapping("/articles/{id}")
    public String getArticle(@PathVariable UUID id, Model model) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID не може бути пустим");
        }
        
        Article article = articleRepository.findById(id)
                .orElse(null); // Не кидаємо помилку тут, а повертаємо null

        if (article == null) {
            // Замість створення нового класу, ми просто кажемо Spring віддати 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Статтю не знайдено");
        }

        model.addAttribute("article", article);
        return "article";
    }

    // Твої існуючі методи
    @GetMapping("/first-steps")
    public String firstSteps() {
        return "first-steps";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
