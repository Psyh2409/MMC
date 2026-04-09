package org.mental_management_center.mmc.model;

import lombok.*;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "articles")
@Getter 
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "slug", unique = true, nullable = false)
    private String slug; // Ті самі "чисті" URL (напр. inner-calm)

    @Column(name = "tags")
    private String tags; // Для відкритого пошуку (напр. "тривога, ресурс")

    // --- БАЙТОВІ МАСИВИ (ТЕ, ЩО БАЧИТЬ БАЗА) ---
    
    @Column(name = "title_bytes", nullable = false)
    private byte[] titleBytes;

    @Column(name = "description_bytes")
    private byte[] descriptionBytes;

    @Column(name = "content_bytes", nullable = false)
    private byte[] contentBytes; // Передбачено під стискання jzeep

    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    // --- ТРАНЗИТНІ ПОЛЯ (ТЕ, ЩО БАЧИТЬ JAVA) ---

    @Transient
    private String title;

    @Transient
    private String description;

    @Transient
    private String content;

    // --- АВТОМАТИКА ---

    @PrePersist
    protected void onCreate() {
        if (this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    @PostLoad
    protected void fillTransientFields() {
        // Конвертація з байтів у текст при завантаженні з БД
        if (titleBytes != null) {
            this.title = new String(titleBytes, StandardCharsets.UTF_8);
        }
        if (descriptionBytes != null) {
            this.description = new String(descriptionBytes, StandardCharsets.UTF_8);
        }
        if (contentBytes != null) {
            // У майбутньому тут додамо декомпресію
            this.content = new String(contentBytes, StandardCharsets.UTF_8);
        }
    }
}