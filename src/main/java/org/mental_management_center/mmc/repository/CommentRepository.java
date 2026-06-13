package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query(value = "SELECT c FROM Comment c LEFT JOIN FETCH c.author WHERE c.article = :article AND c.parentComment IS NULL ORDER BY c.createdAt ASC",
            countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.article = :article AND c.parentComment IS NULL")
    Page<Comment> findTopLevelCommentsByArticle(@Param("article") Article article, Pageable pageable);

    // FETCH JOIN каже Hibernate: "Витягни автора відразу, не чекай!"
    @Query("SELECT c FROM Comment c " +
            "LEFT JOIN FETCH c.author " +
            "LEFT JOIN FETCH c.replies r " +
            "LEFT JOIN FETCH r.author " +
            "WHERE c.article = :article AND c.parentComment IS NULL " +
            "ORDER BY c.createdAt ASC")
    List<Comment> findCommentsWithTreeByArticle(@Param("article") Article article);}