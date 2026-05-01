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

    // Використовуємо стабільний UUID для публічної кімнати
    private static final UUID PUBLIC_CHAT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
        // Шукаємо за UUID безпосередньо
        User user = userRepository.findById(chatMessage.getSenderId()).orElse(null);
        // Перевірка бану
        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            log.warn("Блокування публічного повідомлення від: {}", chatMessage.getSenderId());
            return null;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setSenderName(user.getName());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setRecipientId(PUBLIC_CHAT_ID);

        return chatMessageRepository.save(chatMessage);
    }

    // 2. ПРИВАТНІ ПОВІДОМЛЕННЯ (Твій метод, який ти просив залишити)
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage, java.security.Principal principal) {
        User user = userRepository.findById(chatMessage.getSenderId()).orElse(null);

        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            return;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setSenderName(user.getName());

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // Знаходимо імейл отримувача, щоб WebSocket знав, куди слати
        User recipientUser = userRepository.findById(chatMessage.getRecipientId()).orElse(null);

        if (recipientUser != null) {
            // Відправка отримувачу за його Email
            messagingTemplate.convertAndSendToUser(
                    recipientUser.getEmail(),
                    "/queue/messages",
                    savedMessage
            );
        }
// Відправляємо копію собі, ТІЛЬКИ якщо ми не є отримувачем (щоб не було дублів)
        if (!chatMessage.getSenderId().equals(chatMessage.getRecipientId())) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/messages",
                    savedMessage
            );
        }
    }

    // 3. ІСТОРІЯ (Твій HTTP ендпоінт)
    @GetMapping("/api/chat/history/public")
    public ResponseEntity<List<ChatMessage>> getPublicHistory() {
        return ResponseEntity.ok(chatMessageRepository.findByRecipientIdOrderByTimestampAsc(PUBLIC_CHAT_ID));
    }

    // 4. СТОРІНКА (Твій мапінг на шаблон)
    @GetMapping("/chat")
    public String chatPage(java.security.Principal principal, org.springframework.ui.Model model) {
        if (principal != null) {
            // Знаходимо користувача за email (який лежить у Principal)
            User user = userRepository.findByEmail(principal.getName()).orElse(null);

            // Передаємо об'єкт користувача в модель сторінки
            model.addAttribute("user", user);
        }
        return "chat";
    }

    @GetMapping("/api/chat/history/private")
    public ResponseEntity<List<ChatMessage>> getPrivateHistory(Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        log.info("Запит приватної історії для: {}", principal.getName());

        // Знаходимо користувача за email, щоб отримати його UUID
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Знайдено повідомлень: {}", user.getId());

        // Використовуємо репозиторій з UUID параметрами
        List<ChatMessage> history = chatMessageRepository.findPrivateConversationHistory(user.getId(), PUBLIC_CHAT_ID);

        return ResponseEntity.ok(history);
    }
}