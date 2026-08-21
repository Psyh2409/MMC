package org.mental_management_center.mmc.listener;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.event.ReactionCreatedEvent;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.*;
import org.mental_management_center.mmc.service.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReactionNotificationListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private final ArticleRepository articleRepository;
    private final PublicPostRepository publicPostRepository;
    private final CommentRepository commentRepository;
    private final SharedWallRepository roomWallPostRepository;
    private final SharedWallCommentRepository roomWallCommentRepository;
    private final ChatMessageRepository chatRepository;

    @EventListener
    public void handleReactionCreated(ReactionCreatedEvent event) {
        String emoji = getEmojiForReaction(event.reactionType());
        String title = "Нова реакція " + emoji;
        String message = event.actorName() + " ";

        User recipient = null;
        String targetUrl = "/";

        switch (event.targetType()) {
            case ARTICLE -> {
                Optional<Article> opt = articleRepository.findById(event.targetId());
                if (opt.isPresent()) {
                    recipient = opt.get().getAuthor();
                    message += "відреагував(ла) на вашу статтю.";
                    targetUrl = "/articles/" + opt.get().getId();
                }
            }
            case ARTICLE_COMMENT -> {
                Optional<Comment> opt = commentRepository.findById(event.targetId());
                // Додано захист: виконуємо лише якщо це коментар саме до статті
                if (opt.isPresent() && opt.get().getArticle() != null) {
                    recipient = opt.get().getAuthor();
                    message += "відреагував(ла) на ваш коментар під статтею.";
                    targetUrl = "/articles/" + opt.get().getArticle().getId() + "#comment-" + opt.get().getId();
                }
            }
            case PUBLIC_WALL_POST -> {
                Optional<PublicPost> opt = publicPostRepository.findById(event.targetId());
                if (opt.isPresent()) {
                    recipient = opt.get().getAuthor();
                    message += "відреагував(ла) на ваш публічний допис.";
                    targetUrl = "/therapist/public-wall#post-read-" + opt.get().getId();
                }
            }
            case PUBLIC_WALL_COMMENT -> {
                Optional<Comment> opt = commentRepository.findById(event.targetId());
                // Додано захист: виконуємо лише якщо це коментар публічного поста
                if (opt.isPresent() && opt.get().getPublicPost() != null) {
                    recipient = opt.get().getAuthor();
                    message += "відреагував(ла) на ваш коментар на публічній стіні.";
                    UUID therapistId = opt.get().getPublicPost().getAuthor().getId();
                    boolean isOwner = recipient.getId().equals(therapistId);
                    targetUrl = isOwner
                            ? "/therapist/public-wall#comment-" + opt.get().getId()
                            : "/therapist/public/" + therapistId + "#comment-" + opt.get().getId();
                }
            }
            case ROOM_WALL_POST -> {
                Optional<SharedWallEntry> opt = roomWallPostRepository.findById(event.targetId());
                if (opt.isPresent()) {
                    recipient = userRepository.findById(opt.get().getAuthorId()).orElse(null);
                    message += "відреагував(ла) на ваш запис у терапевтичній кімнаті.";
                    targetUrl = "/therapy/room/" + opt.get().getRoomId() + "#post-read-" + opt.get().getId();
                }
            }
            case ROOM_WALL_COMMENT -> {
                Optional<SharedWallComment> opt = roomWallCommentRepository.findById(event.targetId());
                if (opt.isPresent()) {
                    recipient = userRepository.findById(opt.get().getAuthorId()).orElse(null);
                    message += "відреагував(ла) на ваш коментар у терапевтичній кімнаті.";
                    targetUrl = "/therapy/room/" + opt.get().getWallEntry().getRoomId()
                            + "?openPostId=" + opt.get().getWallEntry().getId()
                            + "#comment-" + opt.get().getId();
                }
            }
            case CHAT_MESSAGE -> {
                Optional<ChatMessage> opt = chatRepository.findById(event.targetId());
                if (opt.isPresent()) {
                    recipient = userRepository.findById(opt.get().getSenderId()).orElse(null);
                    message += "відреагував(ла) у чаті на ваше повідомлення.";
                    String tab = opt.get().getChatType() == ChatMessage.ChatType.PRIVATE ? "private" : "public";
                    targetUrl = "/chat?tab=" + tab + "&recipient=" + event.actorId() + "#msg-item-" + opt.get().getId();
                }
            }
        }

        // Відправка сповіщення
        if (recipient != null && !recipient.getId().equals(event.actorId())) {
            notificationService.createNotification(
                    recipient,
                    title,
                    message,
                    targetUrl,
                    Notification.NotificationType.STANDARD
            );
        }
    }

    private String getEmojiForReaction(Reaction.ReactionType type) {
        return switch (type) {
            case SUPPORT -> "🫂";
            case EMPATHY -> "💛";
            case INSIGHT -> "💡";
            case GRATITUDE -> "🙏";
            case AGREE -> "👍";
        };
    }
}

//package org.mental_management_center.mmc.listener;
//
//import lombok.RequiredArgsConstructor;
//import org.mental_management_center.mmc.event.ReactionCreatedEvent;
//import org.mental_management_center.mmc.model.*;
//import org.mental_management_center.mmc.repository.*;
//import org.mental_management_center.mmc.service.NotificationService;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//
//import java.util.Optional;
//
//@Component
//@RequiredArgsConstructor
//public class ReactionNotificationListener {
//
//    private final NotificationService notificationService;
//    private final UserRepository userRepository;
//
//    private final ArticleRepository articleRepository;
//    private final PublicPostRepository publicPostRepository;
//    private final CommentRepository commentRepository;
//    private final SharedWallRepository roomWallPostRepository;
//    private final SharedWallCommentRepository roomWallCommentRepository;
//    private final ChatMessageRepository chatRepository;
//
//    @EventListener
//    public void handleReactionCreated(ReactionCreatedEvent event) {
//        String emoji = getEmojiForReaction(event.reactionType());
//        String title = "Нова реакція " + emoji;
//
//        User recipient = null;
//        String targetUrl = "/";
//        String actionText = "відреагував(ла) на ваш матеріал.";
//
//        // Розділено логіку для різних типів контенту
//        switch (event.targetType()) {
//            case ARTICLE -> {
//                Optional<Article> opt = articleRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    recipient = opt.get().getAuthor();
//                    actionText = "відреагував(ла) на вашу статтю.";
//                    targetUrl = "/articles/" + opt.get().getId();
//                }
//            }
//            case ARTICLE_COMMENT -> {
//                Optional<Comment> opt = commentRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    Comment comment = opt.get();
//                    recipient = comment.getAuthor();
//                    actionText = "відреагував(ла) на ваш коментар під статтею.";
//                    targetUrl = "/articles/" + comment.getArticle().getId() + "#comment-" + comment.getId();
//                }
//            }
//            case PUBLIC_WALL_POST -> {
//                Optional<PublicPost> opt = publicPostRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    PublicPost post = opt.get();
//                    recipient = post.getAuthor();
//                    actionText = "відреагував(ла) на ваш публічний допис.";
//                    // Терапевт переходить у власний кабінет управління стіною
//                    targetUrl = "/therapist/public-wall#post-read-" + post.getId();
//                }
//            }
//            case PUBLIC_WALL_COMMENT -> {
//                Optional<Comment> opt = commentRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    Comment comment = opt.get();
//                    recipient = comment.getAuthor();
//                    actionText = "відреагував(ла) на ваш коментар на публічній стіні.";
//                    // Перехід на читацький вигляд стіни (доступно всім ролям)
//                    targetUrl = "/therapist/public/" + comment.getPublicPost().getAuthor().getId() + "#comment-" + comment.getId();
//                }
//            }
//            case ROOM_WALL_POST -> {
//                Optional<SharedWallEntry> opt = roomWallPostRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    SharedWallEntry entry = opt.get();
//                    recipient = userRepository.findById(entry.getAuthorId()).orElse(null);
//                    actionText = "відреагував(ла) на ваш запис у терапевтичній кімнаті.";
//                    targetUrl = "/therapy/room/" + entry.getRoomId() + "#post-read-" + entry.getId();
//                }
//            }
//            case ROOM_WALL_COMMENT -> {
//                Optional<SharedWallComment> opt = roomWallCommentRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    SharedWallComment comment = opt.get();
//                    recipient = userRepository.findById(comment.getAuthorId()).orElse(null);
//                    actionText = "відреагував(ла) на ваш коментар у терапевтичній кімнаті.";
//                    targetUrl = "/therapy/room/" + comment.getWallEntry().getRoomId() + "#comment-" + comment.getId();
//                }
//            }
//            case CHAT_MESSAGE -> {
//                Optional<ChatMessage> opt = chatRepository.findById(event.targetId());
//                if (opt.isPresent()) {
//                    ChatMessage chatMsg = opt.get();
//                    recipient = userRepository.findById(chatMsg.getSenderId()).orElse(null);
//                    actionText = "відреагував(ла) на ваше повідомлення в чаті.";
//
//                    if (chatMsg.getChatType() == ChatMessage.ChatType.PRIVATE) {
//                        // Відкриваємо приватну вкладку з тим, хто поставив лайк (actorId)
//                        targetUrl = "/chat?tab=private&recipient=" + event.actorId() + "#msg-item-" + chatMsg.getId();
//                    } else {
//                        // Відкриваємо публічну вкладку
//                        targetUrl = "/chat?tab=public#msg-item-" + chatMsg.getId();
//                    }
//                }
//            }
//        }
//
//        if (recipient != null && !recipient.getId().equals(event.actorId())) {
//            // Формуємо детальний текст повідомлення (Ім'я + дія)
//            String finalMessage = event.actorName() + " " + actionText;
//
//            notificationService.createNotification(
//                    recipient,
//                    title,
//                    finalMessage,
//                    targetUrl,
//                    Notification.NotificationType.STANDARD
//            );
//        }
//    }
//
//    private String getEmojiForReaction(Reaction.ReactionType type) {
//        return switch (type) {
//            case SUPPORT -> "🫂";
//            case EMPATHY -> "💛";
//            case INSIGHT -> "💡";
//            case GRATITUDE -> "🙏";
//            case AGREE -> "👍";
//        };
//    }
//}