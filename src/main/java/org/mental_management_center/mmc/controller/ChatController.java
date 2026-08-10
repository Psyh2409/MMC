package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.model.ChatMessage;
import org.mental_management_center.mmc.model.MessageStatus;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private static final UUID PUBLIC_CHAT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 1. ПУБЛІЧНИЙ ЧАТ (з відображенням тексту відповіді у сповіщенні)
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
        chatMessage.setSenderAvatar(user.getAvatarFileName());
        chatMessage.setChatType(ChatMessage.ChatType.PUBLIC);

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // --- БЛОК СПОВІЩЕНЬ ПРО ВІДПОВІДЬ У ПУБЛІЧНОМУ ЧАТІ ---
        if (chatMessage.getParentId() != null) {
            chatMessageRepository.findById(chatMessage.getParentId()).ifPresent(parentMsg -> {
                userRepository.findById(parentMsg.getSenderId()).ifPresent(parentAuthor -> {
                    // Перевіряємо: сповіщаємо, тільки якщо це не відповідь самому собі
                    if (!parentAuthor.getId().equals(user.getId())) {
                        notificationService.createNotification(
                                parentAuthor,
                                "Відповідь у публічному чаті",
                                user.getName() + ": " + chatMessage.getContent(), // Додано текст повідомлення
                                "/chat?tab=public",
                                Notification.NotificationType.STANDARD
                        );
                    }
                });
            });
        }

        return savedMessage;
    }

    // 2. ПРИВАТНІ ПОВІДОМЛЕННЯ (з уточненням типу чату в заголовку)
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage, Principal principal) {
        User user = userRepository.findById(chatMessage.getSenderId()).orElse(null);

        if (user == null || !user.isEnabled() || !user.isChatEnabled()) {
            return;
        }

        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setStatus(MessageStatus.DELIVERED);
        chatMessage.setSenderName(user.getName());
        chatMessage.setSenderAvatar(user.getAvatarFileName());
        chatMessage.setChatType(ChatMessage.ChatType.PRIVATE);

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

        // --- БЛОК СПОВІЩЕНЬ ПРО ПРИВАТНЕ ПОВІДОМЛЕННЯ ---
        UUID recipientId = chatMessage.getRecipientId();
        if (recipientId != null
                && !recipientId.equals(PUBLIC_CHAT_ID)
                && !recipientId.equals(chatMessage.getSenderId())) {

            userRepository.findById(recipientId).ifPresent(recipient -> {
                notificationService.createNotification(
                        recipient,
                        "Відповідь в приватному чаті", // Уточнено заголовок
                        chatMessage.getSenderName() + ": " + chatMessage.getContent(),
                        "/chat?tab=private&recipient=" + chatMessage.getSenderId(),
                        Notification.NotificationType.STANDARD
                );
            });
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
    public String chatPage(
            @RequestParam(value = "tab", defaultValue = "public") String tab,
            @RequestParam(value = "recipient", required = false) String recipient,
            java.security.Principal principal,
            org.springframework.ui.Model model) {

        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            model.addAttribute("user", user);
        }

        // Передаємо вказівки для фронтенду
        model.addAttribute("activeTab", tab);
        model.addAttribute("activeRecipient", recipient != null ? recipient : PUBLIC_CHAT_ID.toString());

        return "chat";
    }

    @DeleteMapping("/api/chat/messages/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable UUID id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        ChatMessage message = chatMessageRepository.findById(id).orElse(null);

        if (message == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isOwner = message.getSenderId().equals(user.getId());
        boolean isAdminPublicDelete = user.isAdmin() && message.getChatType() == ChatMessage.ChatType.PUBLIC;

        // БЕЗПЕКА ТА КОНФІДЕНЦІЙНІСТЬ:
        // 1. Власник може видалити будь-яке своє повідомлення.
        // 2. Адміністратор може видалити чуже повідомлення ТІЛЬКИ в публічному чаті.
        if (isOwner || isAdminPublicDelete) {
            chatMessageRepository.delete(message);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/chat/{chatRoomId}/messages")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> getMoreMessages(@PathVariable UUID chatRoomId,
                                                             @RequestParam(defaultValue = "0") int page) {

        Pageable pageable = PageRequest.of(page, 20);
        Slice<ChatMessage> messageSlice = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable);

        List<ChatMessage> messages = messageSlice.getContent();

        messages.forEach(msg -> {
            userRepository.findById(msg.getSenderId())
                    .ifPresent(u -> msg.setSenderAvatar(u.getAvatarFileName()));
        });

        return ResponseEntity.ok(messages);
    }

    @GetMapping("/api/chat/messages/{messageId}/page")
    public ResponseEntity<Integer> getPageForMessage(
            @PathVariable UUID messageId,
            @RequestParam UUID chatRoomId) {

        return chatMessageRepository.findById(messageId)
                .map(message -> {
                    // Рахуємо, скільки повідомлень новіші за це
                    long newerCount = chatMessageRepository.countMessagesAfter(chatRoomId, message.getTimestamp());
                    // Обчислюємо номер сторінки (0-індексація, по 20 на сторінку)
                    int page = (int) (newerCount / 20);
                    return ResponseEntity.ok(page);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/chat/messages/{id}")
    public ResponseEntity<ChatMessage> updateMessage(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, String> payload,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ChatMessage message = chatMessageRepository.findById(id).orElse(null);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }

        // БЕЗПЕКА: Перевіряємо, чи є поточний користувач автором повідомлення
        if (!message.getSenderId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String newContent = payload.get("content");
        if (newContent == null || newContent.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        message.setContent(newContent);
        ChatMessage savedMessage = chatMessageRepository.save(message);

        return ResponseEntity.ok(savedMessage);
    }
}