package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;

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
}
