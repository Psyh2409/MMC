package org.mental_management_center.mmc.service;

//import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.PublicPostRepository;
import org.mental_management_center.mmc.repository.TherapyNoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    // Lombok @RequiredArgsConstructor автоматично створить конструктор для цих фінальних полів
    private final CommentRepository commentRepository;
    private final PublicPostRepository publicPostRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TherapyNoteRepository therapyNoteRepository;

    // readOnly = true підказує Hibernate, що ми нічого не змінюємо, це прискорює транзакцію
    @Transactional(readOnly = true)
    public List<UserActivity> getUserActivities(User user) {
        List<UserActivity> activities = new ArrayList<>();

        // 1. Збираємо КОМЕНТАРІ
        List<Comment> comments = commentRepository.findByAuthorOrderByCreatedAtDesc(user);
        for (Comment comment : comments) {
            String articleTitle = (comment.getArticle() != null) ? comment.getArticle().getTitle() : "Видалена стаття";

            String commentUrl = (comment.getArticle() != null)
                    ? "/articles/" + comment.getArticle().getId() + "?commentId=" + comment.getId() + "#comment-" + comment.getId()
                    : "";

            activities.add(UserActivity.builder()
                    .id(comment.getId())
                    .type(UserActivity.ActivityType.COMMENT)
                    .typeLabel("Коментар до статті")
                    .title(articleTitle)
                    .content(comment.getContent()) // Якщо в майбутньому коментарі будуть шифруватись, тут викликатимемо decrypt
                    .createdAt(comment.getCreatedAt())
                    .targetUrl(commentUrl)
                    .build());
        }

        // 2. Збираємо ПУБЛІЧНІ ПОСТИ
        List<PublicPost> posts = publicPostRepository.findByAuthorOrderByCreatedAtDesc(user);
        for (PublicPost post : posts) {
            activities.add(UserActivity.builder()
                    .id(post.getId())
                    .type(UserActivity.ActivityType.PUBLIC_POST)
                    .typeLabel("Допис у стрічці")
                    .title("Публічна стіна")
                    .content(post.getContent())
                    .createdAt(post.getCreatedAt())
                    .targetUrl("/therapist/public-wall") // Замініть на ваш реальний роут, якщо він інший
                    .build());
        }

        // 3. Збираємо ЧАТИ (Публічні та Приватні)
        List<ChatMessage> chatMessages = chatMessageRepository.findBySenderIdOrderByTimestampDesc(user.getId());
        for (ChatMessage msg : chatMessages) {
            boolean isPrivate = (msg.getChatType() == ChatMessage.ChatType.PRIVATE);

            String anchor = "#msg-item-" + msg.getId();

            // Визначаємо з ким відкривати чат, якщо це приватне повідомлення
            // (Якщо повідомлення відправлене мною, адресат - recipientId. Якщо мені - senderId.
            // Оскільки ми тягнемо findBySenderId, адресат завжди recipientId).
            String targetUrl = isPrivate
                    ? "/chat?tab=private&recipient=" + msg.getRecipientId() + anchor
                    : "/chat" + anchor;

            activities.add(UserActivity.builder()
                    .id(msg.getId())
                    .type(isPrivate ? UserActivity.ActivityType.PRIVATE_CHAT : UserActivity.ActivityType.PUBLIC_CHAT)
                    .typeLabel(isPrivate ? "Приватне повідомлення" : "Повідомлення в загальному чаті")
                    .title(isPrivate ? "Приватний чат" : "Загальна кімната")
                    .content(msg.getContent())
                    .createdAt(msg.getTimestamp())
                    .targetUrl(targetUrl)
                    .build());
        }

        // 4. Збираємо НОТАТКИ ТЕРАПЕВТА (тільки якщо юзер має на це права)
        if (user.isTherapist() || user.isAdmin()) {
            List<TherapyNote> notes = therapyNoteRepository.findByAuthorOrderByCreatedAtDesc(user);
            for (TherapyNote note : notes) {
                String clientName = (note.getClient() != null) ? note.getClient().getName() : "Невідомий клієнт";

                activities.add(UserActivity.builder()
                        .id(note.getId())
                        .type(UserActivity.ActivityType.THERAPIST_NOTE)
                        .typeLabel("Нотатка терапевта")
                        .title("Сесія з: " + clientName)
                        .content(note.getContent())
                        .createdAt(note.getCreatedAt())
                        .targetUrl("/therapist/client-notes")
                        .build());
            }
        }

        // Фінальний штрих: сортуємо загальний масив за датою (від найновіших до найстаріших)
        activities.sort(Comparator.comparing(UserActivity::getCreatedAt).reversed());

        return activities;
    }

    public Page<UserActivity> getUserActivitiesPaged(User user, Pageable pageable) {
        // Отримуємо повний відсортований список активностей
        List<UserActivity> allActivities = getUserActivities(user);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allActivities.size());
        // Захист від виходу за межі списку
        if (start > allActivities.size()) {
            return new PageImpl<>(List.of(), pageable, allActivities.size());
        }
        List<UserActivity> pagedContent = allActivities.subList(start, end);
        return new PageImpl<>(pagedContent, pageable, allActivities.size());
    }
}
