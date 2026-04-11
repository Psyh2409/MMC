package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    
    List<Article> findByCategory(String category);
    
    // Для "Бардачка": всі статті одного автора, свіжі зверху
    List<Article> findByAuthorIdOrderByPublishedAtDesc(UUID authorId);
    
    // Для пошуку: знаходимо статті за тегом (ігноруючи регістр)
    List<Article> findByTagsContainingIgnoreCase(String tag);
    
    // Для головної сторінки: просто список усіх нових статей
    List<Article> findAllByOrderByPublishedAtDesc();
}