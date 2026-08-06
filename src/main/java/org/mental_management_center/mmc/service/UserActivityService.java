package org.mental_management_center.mmc.service;

//import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.PublicPostRepository;
import org.mental_management_center.mmc.repository.TherapyNoteRepository;
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

            activities.add(UserActivity.builder()
                    .id(comment.getId())
                    .type(UserActivity.ActivityType.COMMENT)
                    .typeLabel("Коментар до статті")
                    .title(articleTitle)
                    .content(comment.getContent()) // Якщо в майбутньому коментарі будуть шифруватись, тут викликатимемо decrypt
                    .createdAt(comment.getCreatedAt())
                    .targetUrl("/article/" + (comment.getArticle() != null ? comment.getArticle().getId() : ""))
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
            boolean isPrivate = (msg.getRecipientId() != null);

            activities.add(UserActivity.builder()
                    .id(msg.getId())
                    .type(isPrivate ? UserActivity.ActivityType.PRIVATE_CHAT : UserActivity.ActivityType.PUBLIC_CHAT)
                    .typeLabel(isPrivate ? "Приватне повідомлення" : "Повідомлення в загальному чаті")
                    .title(isPrivate ? "Приватний чат" : "Загальна кімната")
                    .content(msg.getContent())
                    .createdAt(msg.getTimestamp())
                    .targetUrl("/chat")
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
}
