package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // --- ДОДАНО ДЛЯ ВІДПОВІДЕЙ ---

    // Посилання на "батьківський" коментар (якщо це відповідь)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parentComment;

    // Список усіх відповідей на цей коментар (щоб зручно виводити в HTML)
    @OneToMany(mappedBy = "parentComment",
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("createdAt ASC") // Твоє сортування на місці
    @Builder.Default          // Твій білдер на місці
    private List<Comment> replies = new ArrayList<>();

    @Column(name = "is_deleted_by_admin", nullable = false)
    private boolean isDeletedByAdmin = false;

    @Column(name = "is_deleted_by_author", nullable = false)
    private boolean isDeletedByAuthor = false; // Автор статті

    @Column(name = "is_deleted_by_commenter", nullable = false)
    private boolean isDeletedByCommenter = false; // Коментатор

    @Column(name = "deletion_reason")
    private String deletionReason;
}