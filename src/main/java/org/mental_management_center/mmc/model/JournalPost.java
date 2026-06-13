package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "journal_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Зв'язок із користувачем, який веде щоденник
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Зашифровані та стиснуті байтові дані поста
    @Lob
    @Column(name = "encrypted_content", nullable = false)
    private byte[] encryptedContent;

    // Шлях до прикріпленого медіафайлу (фото/відео), null - якщо пост тільки текстовий
    @Column(name = "media_file_name", length = 512)
    private String mediaFileName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Поле для фронтенду, не зберігається в БД у чистому вигляді
    @Transient
    private String content;

    // Зашифровані 4 КБ початкових метаданих медіафайлу
    @Lob
    @Column(name = "encrypted_media_header")
    private byte[] encryptedMediaHeader;
}