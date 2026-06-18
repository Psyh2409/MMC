package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "shared_wall_entries")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedWallEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Ключ, що об'єднує вас обох в одному просторі
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    // Хто конкретно написав (терапевт або клієнт)
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    // Вміст
    @Lob
    @Column(name = "media_file_head")
    private byte[] mediaFileHead;

    @Lob
    @Column(name = "encrypted_content", nullable = false)
    private byte[] encryptedContent;

    @Column(name = "media_file_name", length = 512)
    private String mediaFileName;

    @Transient // Для фронтенду
    private String content;

    // Статус для твоєї сигналізації (блимання)
    @Column(name = "is_read")
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
