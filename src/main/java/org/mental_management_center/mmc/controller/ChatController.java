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

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
public class ChatController {

    private static final UUID PUBLIC_CHAT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private UserRepository userRepository;

    // 1. ПУБЛІЧНИЙ ЧАТ
    @MessageMapping("/chat.public")
    @SendTo("/topic/public")
    public ChatMessage processPublicMessage(@Payload ChatMessage chatMessage) {
        User user = userRepository.findById(chatMessage.getSenderId()).orElse(null);

        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            return null;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setSenderName(user.getName());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setRecipientId(PUBLIC_CHAT_ID);

        // 🎯 ФІКС: Тепер і публічний чат знає про аватарки!
        chatMessage.setSenderAvatar(user.getAvatarFileName());

        return chatMessageRepository.save(chatMessage);
    }

    // 2. ПРИВАТНІ ПОВІДОМЛЕННЯ
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage, java.security.Principal principal) {
        User user = userRepository.findById(chatMessage.getSenderId()).orElse(null);

        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            return;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setSenderName(user.getName());

        // Тут аватар уже був, залишаємо
        chatMessage.setSenderAvatar(user.getAvatarFileName());

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        User recipientUser = userRepository.findById(chatMessage.getRecipientId()).orElse(null);

        if (recipientUser != null) {
            messagingTemplate.convertAndSendToUser(
                    recipientUser.getEmail(),
                    "/queue/messages",
                    savedMessage
            );
        }

        if (!chatMessage.getSenderId().equals(chatMessage.getRecipientId())) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/messages",
                    savedMessage
            );
        }
    }

    // 3. ІСТОРІЯ ПУБЛІЧНОГО ЧАТУ
    @GetMapping("/api/chat/history/public")
    public ResponseEntity<List<ChatMessage>> getPublicHistory() {
        List<ChatMessage> history = chatMessageRepository.findByRecipientIdOrderByTimestampAsc(PUBLIC_CHAT_ID);
        // 🎯 ФІКС: Насичуємо стару історію свіжими аватарками
        history.forEach(msg -> {
            userRepository.findById(msg.getSenderId())
                    .ifPresent(u -> msg.setSenderAvatar(u.getAvatarFileName()));
        });
        return ResponseEntity.ok(history);
    }

    // 4. ІСТОРІЯ ПРИВАТНОГО ЧАТУ
    @GetMapping("/api/chat/history/private")
    public ResponseEntity<List<ChatMessage>> getPrivateHistory(Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<ChatMessage> history = chatMessageRepository.findPrivateConversationHistory(user.getId(), PUBLIC_CHAT_ID);

        // 🎯 ФІКС: Насичуємо стару історію свіжими аватарками
        history.forEach(msg -> {
            userRepository.findById(msg.getSenderId())
                    .ifPresent(u -> msg.setSenderAvatar(u.getAvatarFileName()));
        });

        return ResponseEntity.ok(history);
    }

    @GetMapping("/chat")
    public String chatPage(java.security.Principal principal, org.springframework.ui.Model model) {
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            model.addAttribute("user", user);
        }
        return "chat";
    }
}