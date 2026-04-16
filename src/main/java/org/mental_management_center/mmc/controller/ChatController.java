package org.mental_management_center.mmc.controller;

import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.model.ChatMessage;
import org.mental_management_center.mmc.model.MessageStatus;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private UserRepository userRepository;

    // 1. ПУБЛІЧНИЙ ЧАТ (Той, що для всіх)
    @MessageMapping("/chat.public")
    @SendTo("/topic/public")
    public ChatMessage processPublicMessage(@Payload ChatMessage chatMessage) {
        User user = findUserSafely(chatMessage.getSenderId());

        // Перевірка бану
        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            log.warn("Блокування публічного повідомлення від: {}", chatMessage.getSenderId());
            return null;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setSenderName(user.getName());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setRecipientId("PUBLIC");

        return chatMessageRepository.save(chatMessage);
    }

    // 2. ПРИВАТНІ ПОВІДОМЛЕННЯ (Твій метод, який ти просив залишити)
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage) {
        User user = findUserSafely(chatMessage.getSenderId());

        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            return;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setSenderName(user.getName());

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // ВІДПРАВКА ОТРИМУВАЧУ
        // У методі processMessage
        messagingTemplate.convertAndSendToUser(
                chatMessage.getRecipientId(),
                "/queue/messages",
                savedMessage
        );

// Відправляємо копію собі, ТІЛЬКИ якщо ми не є отримувачем (щоб не було дублів)
        if (!chatMessage.getSenderId().equals(chatMessage.getRecipientId())) {
            messagingTemplate.convertAndSendToUser(
                    chatMessage.getSenderId(),
                    "/queue/messages",
                    savedMessage
            );
        }
    }

    // 3. ІСТОРІЯ (Твій HTTP ендпоінт)
    @GetMapping("/api/chat/history/public")
    public ResponseEntity<List<ChatMessage>> getPublicHistory() {
        return ResponseEntity.ok(chatMessageRepository.findByRecipientIdOrderByTimestampAsc("PUBLIC"));
    }

    // 4. СТОРІНКА (Твій мапінг на шаблон)
    @GetMapping("/chat")
    public String chatPage() {
        return "chat";
    }

    // 5. БЕЗПЕЧНИЙ ПОШУК (Допоміжний метод, щоб не копіювати try-catch)
    private User findUserSafely(String id) {
        try {
            return userRepository.findById(UUID.fromString(id)).orElse(null);
        } catch (Exception e) {
            return userRepository.findByEmail(id).orElse(null);
        }
    }

    @GetMapping("/api/chat/history/private")
    public ResponseEntity<List<ChatMessage>> getPrivateHistory(Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.info("Запит приватної історії для: {}", principal.getName());

        // Передаємо email (principal.getName()) прямо в запит
        List<ChatMessage> history = chatMessageRepository.findPrivateConversationHistory(principal.getName());

        log.info("Знайдено повідомлень: {}", history.size());

        return ResponseEntity.ok(history);
    }
}