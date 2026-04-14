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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

        // Якщо прийшов slug і ми бачимо, що такого в словнику ще немає
        if (slug != null && !slug.isBlank()) {
            if (!categoryTranslationRepository.existsById(slug)) {
                // Якщо прийшла і назва — створюємо новий запис у словнику
                if (nameUa != null && !nameUa.isBlank()) {
                    CategoryTranslation translation = CategoryTranslation.builder()
                            .categorySlug(slug)
                            .displayName(nameUa)
                            .build();
                    categoryTranslationRepository.save(translation);
                }
            }
        }

        // Використовуємо ваш @Builder з класу Article
        Article article = Article.builder()
                .title(form.getTitle())
                .description(form.getDescription())
                .category(slug) // Зберігаємо англійський slug
                .publishedAt(LocalDateTime.now())
                .author(author) // Сюди прийде той User, якого ми передамо з контролера
                .build();

        // Викликаємо ваш метод setContent, який автоматично стисне текст у GZIP
        article.setContent(form.getContent());

        // Обробка тегів
        if (form.getTags() != null && !form.getTags().isBlank()) {
            java.util.Set<java.lang.String> tagSet = java.util.Arrays.stream(form.getTags().split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toSet());
            article.setTags(tagSet);
        }

        articleRepository.save(article);
    }
}
