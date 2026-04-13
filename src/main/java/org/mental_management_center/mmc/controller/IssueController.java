package org.mental_management_center.mmc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;
import java.util.List;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.model.Article;

@Controller
@RequestMapping("/issues")
public class IssueController {

    private final ArticleRepository articleRepository;

    public IssueController(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    private static final Map<String, String> TRANSLATIONS = Map.of(
            "inner-calm", "Тривога та панічні стани",
            "restore-resource", "Відновлення ресурсу",
            "closeness-crisis", "Кризи близькості",
            "new-meanings", "Депресивні стани та сенси",
            "freedom-choice", "Залежні форми поведінки",
            "dialogue", "Конфлікти та медіація",
            "exit-nearby", "Ілюзія, що виходу нема");

    @GetMapping("/{topic}")
    public String getIssuePage(@PathVariable String topic, Model model) {
        // 1. Отримуємо українську назву або просто назву категорії з великої літери
        String ukrainianTitle = TRANSLATIONS.getOrDefault(topic, 
            topic.substring(0, 1).toUpperCase() + topic.substring(1).replace("-", " "));

        // 2. Шукаємо всі статті цієї категорії
        List<Article> articles = articleRepository.findByCategory(topic);

        model.addAttribute("topicTitle", ukrainianTitle);
        model.addAttribute("articles", articles);

        // Повертаємо шаблон згідно інструкції
        return "issues/topic-page";
    }
}