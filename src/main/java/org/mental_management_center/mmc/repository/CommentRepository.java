package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.Comment;
import org.mental_management_center.mmc.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query(value = "SELECT c FROM Comment c LEFT JOIN FETCH c.author WHERE c.article = :article AND c.parentComment IS NULL ORDER BY c.createdAt DESC",
            countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.article = :article AND c.parentComment IS NULL")
    Page<Comment> findTopLevelCommentsByArticle(@Param("article") Article article, Pageable pageable);

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.author WHERE c.article = :article AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findCommentsWithTreeByArticle(@Param("article") Article article);

    @EntityGraph(attributePaths = {"article"})
    List<Comment> findByAuthorOrderByCreatedAtDesc(User author);

    // ВИПРАВЛЕНО: Використовуємо parentComment замість parent та > замість < для DESC сортування
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.article.id = :articleId AND c.parentComment IS NULL AND c.createdAt > :createdAt")
    long countRootCommentsNewerThan(@Param("articleId") UUID articleId, @Param("createdAt") LocalDateTime createdAt);

    @Query("SELECT c FROM Comment c " +
            "LEFT JOIN FETCH c.author " +
            "LEFT JOIN FETCH c.publicPost pp " +
            "LEFT JOIN FETCH pp.author " +
            "WHERE c.publicPost = :post AND c.parentComment IS NULL " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findCommentsWithTreeByPublicPost(@Param("post") org.mental_management_center.mmc.model.PublicPost post);
}

