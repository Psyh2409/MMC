package org.mental_management_center.mmc.model;

import lombok.*;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.mental_management_center.mmc.service.ArticleService;

@Entity
@Table(name = "articles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @Setter(AccessLevel.NONE) // ID генерується автоматично, тому сеттер не потрібен
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE) // Автор встановлюється при створенні статті і не змінюється
    private User author;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private String category; // Наприклад: "inner-calm", "restore-resource"

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

    public Article(User author) {
        this.id = UUID.randomUUID();
        this.author = author;
    }



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
            this.content = ArticleService.decompress(contentBytes);
        }
    }
}