package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    List<Article> findByCategory(String category);

    boolean existsByTitle(String title);

    Optional<Article> findByTitle(String title);

    List<Article> findByAuthorIdOrderByPublishedAtDesc(UUID authorId);

    List<Article> findByTags(String tag);

    List<Article> findAllByOrderByPublishedAtDesc();

    // Отримуємо всі унікальні категорії для рубрикатора в хедері
    @Query("SELECT DISTINCT a.category FROM Article a")
    List<String> findAllCategories();

    @Query("SELECT COUNT(a) FROM Article a WHERE a.imagePath = :fileName")
    long countArticleUsage(@Param("fileName") String fileName);
}