package org.mental_management_center.mmc.model;

import lombok.*;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Entity
@Table(name = "articles")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private String category;

    // Заголовок і опис — тепер просто ТЕКСТ. База буде їх бачити.
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Тільки основний контент лишаємо в байтах зі стисненням
    @Column(name = "content_bytes", nullable = false)
    private byte[] contentBytes;

    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    @Transient
    private String content;

    // Сеттер для автоматичного стиснення при встановленні тексту
    public void setContent(String content) {
        this.content = content;
        if (content != null) {
            this.contentBytes = compress(content);
        }
    }

    // Метод для розпакування після завантаження з бази
    @PostLoad
    protected void fillContent() {
        if (contentBytes != null) {
            this.content = decompress(contentBytes);
        }
    }

    @Transient // Це поле не йде в базу, воно лише для Thymeleaf
    public String getDisplayCategory() {
        if (this.category == null) return "Матеріали";

        return switch (this.category) {
            case "inner-calm" -> "Тривога та панічні стани";
            case "restore-resource" -> "Відновлення ресурсу";
            case "closeness-crisis" -> "Кризи близькості";
            case "new-meanings" -> "Депресивні стани та сенси";
            case "freedom-choice" -> "Залежні форми поведінки";
            case "dialogue" -> "Конфлікти та медіація";
            case "exit-nearby" -> "Ілюзія, що виходу нема";
            default -> this.category;
        };
    }

    private byte[] compress(String str) {
        if (str == null || str.isEmpty()) return null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            return str.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) return null;
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new String(compressed, StandardCharsets.UTF_8);
        }
    }
}
//    @Column(name = "title_bytes", nullable = false)
//    private byte[] titleBytes;
//
//    @Column(name = "description_bytes")
//    private byte[] descriptionBytes;
//
//    @Column(name = "content_bytes", nullable = false)
//    private byte[] contentBytes;
//
//    @Column(name = "published_at", nullable = false, updatable = false)
//    private LocalDateTime publishedAt;
//
//    @Transient
//    private String title;
//
//    @Transient
//    private String description;
//
//    @Transient
//    private String content;
//
//    // --- РУЧНІ СЕТТЕРИ ДЛЯ ГАРАНТОВАНОЇ КОНВЕРТАЦІЇ ---
//
//    public void setTitle(String title) {
//        this.title = title;
//        if (title != null) {
//            this.titleBytes = title.getBytes(StandardCharsets.UTF_8);
//        }
//    }
//
//    public void setDescription(String description) {
//        this.description = description;
//        if (description != null) {
//            this.descriptionBytes = description.getBytes(StandardCharsets.UTF_8);
//        }
//    }
//
//    public void setContent(String content) {
//        this.content = content;
//        if (content != null) {
//            this.contentBytes = compress(content);
//        }
//    }
//
//    @PrePersist
//    protected void onCreate() {
//        if (this.publishedAt == null) {
//            this.publishedAt = LocalDateTime.now();
//        }
//    }
//
//    @PostLoad
//    protected void fillTransientFields() {
//        if (titleBytes != null) {
//            this.title = new String(titleBytes, StandardCharsets.UTF_8);
//        }
//        if (descriptionBytes != null) {
//            this.description = new String(descriptionBytes, StandardCharsets.UTF_8);
//        }
//        if (contentBytes != null) {
//            this.content = decompress(contentBytes);
//        }
//    }
