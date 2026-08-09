package org.mental_management_center.mmc.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityDto {

    private UUID id;            // ID оригінального коментаря / поста / чату
    private ActivityType type;  // Тип активності (COMMENT, PUBLIC_POST, PUBLIC_CHAT, PRIVATE_CHAT, THERAPIST_NOTE)
    private String typeLabel;   // Текст для фронтенду: "Коментар під статтею", "Допис у стрічці"
    private String title;       // Назва статті або опис чату
    private String content;     // Текст коментаря/повідомлення
    private LocalDateTime createdAt; // Дата та час створення
    private String targetUrl;   // Посилання для швидкого переходу/перегляду

    public enum ActivityType {
        COMMENT,
        PUBLIC_POST,
        PUBLIC_CHAT,
        PRIVATE_CHAT,
        THERAPIST_NOTE
    }
}
