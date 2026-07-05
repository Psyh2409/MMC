package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "public_posts")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicPost {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // Зберігаємо оригінальний текст. media-links.js сам перетворить посилання на відео/картки на фронтенді
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // Якщо фахівець завантажить картинку напряму (не лінком)
    @Column(name = "media_file_name", length = 512)
    private String mediaFileName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}