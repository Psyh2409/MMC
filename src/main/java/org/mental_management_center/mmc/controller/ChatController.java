package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.ChatMessage;
import org.mental_management_center.mmc.model.MessageStatus;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @GetMapping("/chat")
    public String chatPage() {
        return "chat";
    }

    // 1. Обробка повідомлень у реальному часі
    // Клієнт буде відправляти повідомлення на адресу /app/chat
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage) {
        // Встановлюємо поточний час та статус "доставлено"
        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus(MessageStatus.DELIVERED);

        // Зберігаємо повідомлення в PostgreSQL
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // Пересилаємо повідомлення адресату.
        // Метод convertAndSendToUser автоматично сформує адресу:
        // /user/{recipientId}/queue/messages
        messagingTemplate.convertAndSendToUser(
                chatMessage.getRecipientId(),
                "/queue/messages",
                savedMessage
        );
    }

    // 1. HTTP-ендпоінт для завантаження загальної історії
    @GetMapping("/api/chat/history/public")
    public ResponseEntity<List<ChatMessage>> getPublicHistory() {
        return ResponseEntity.ok(chatMessageRepository.findByRecipientIdOrderByTimestampAsc("PUBLIC"));
    }

    // 2. Обробка повідомлень у спільному просторі (Реальний час)
    @MessageMapping("/chat.public")
    @SendTo("/topic/public") // БРОДКАСТ: Розсилає всім підписникам!
    public ChatMessage processPublicMessage(@Payload ChatMessage chatMessage) {
        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setRecipientId("PUBLIC"); // Маркер спільного простору

        // Зберігаємо в базу і одразу повертаємо (Spring сам розішле це всім через @SendTo)
        return chatMessageRepository.save(chatMessage);
    }
}