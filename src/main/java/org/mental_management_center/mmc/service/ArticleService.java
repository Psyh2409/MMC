package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.CategoryTranslation;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.web.form.ArticleForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final CategoryTranslationRepository categoryTranslationRepository; // ІН'ЄКЦІЯ РЕПОЗИТОРІЮ

    public List<Article> findAll() {
        return articleRepository.findAll();
    }

    public Article findById(UUID id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Статтю не знайдено"));
    }

    @Transactional
    public void saveArticle(Article article, String title, String desc, String text) {
        article.setTitle(title);
        article.setDescription(desc);
        article.setContent(text);
        articleRepository.save(article);
    }

    @Transactional
    public void deleteArticle(UUID id) {
        // Перевіряємо, чи існує, щоб не "впасти" з помилкою
        if (articleRepository.existsById(id)) {
            articleRepository.deleteById(id);
        }
    }

    @Transactional
    public void saveFromForm(ArticleForm form, User author) {
        String slug = form.getCategory();
        String nameUa = form.getCategoryNameUa();

        // 1. ОНОВЛЕННЯ СЛОВНИКА (тепер ми і створюємо, і ОНОВЛЮЄМО назву)
        if (slug != null && !slug.isBlank() && nameUa != null && !nameUa.isBlank()) {
            CategoryTranslation translation = categoryTranslationRepository.findById(slug)
                    .orElse(new CategoryTranslation(slug, nameUa)); // Знайти або створити новий

            translation.setDisplayName(nameUa); // Оновити назву в будь-якому випадку
            categoryTranslationRepository.save(translation);
        }

        Article article;

        // 2. ЛОГІКА UPSERT (Update or Insert)
        if (form.getId() != null) {
            // РЕДАГУВАННЯ
            article = articleRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Статтю не знайдено"));

            article.setTitle(form.getTitle());
            article.setDescription(form.getDescription());
            article.setCategory(slug);
            // Дату та автора НЕ чіпаємо при редагуванні
        } else {
            // СТВОРЕННЯ
            article = Article.builder()
                    .title(form.getTitle())
                    .description(form.getDescription())
                    .category(slug)
                    .publishedAt(LocalDateTime.now())
                    .author(author)
                    .build();
        }

        article.setContent(form.getContent());

        // ТЕГИ (якщо порожньо - чистимо)
        if (form.getTags() != null && !form.getTags().isBlank()) {
            Set<String> tagSet = Arrays.stream(form.getTags().split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toSet());
            article.setTags(tagSet);
        } else {
            article.setTags(new HashSet<>());
        }

        articleRepository.save(article);
    }
}
