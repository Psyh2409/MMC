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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category", referencedColumnName = "category_slug", insertable = false, updatable = false)
    private CategoryTranslation categoryTranslation;

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

    // Усередині класу Article.java додаємо поле для відносного шляху медіа
    @Column(name = "image_path", length = 512)
    private String imagePath;

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

