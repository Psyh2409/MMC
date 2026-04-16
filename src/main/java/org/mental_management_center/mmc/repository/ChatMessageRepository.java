package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.ChatMessage;
import org.mental_management_center.mmc.model.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Отримуємо історію чату між двома користувачами, відсортовану за часом
    @Query("SELECT m FROM ChatMessage m WHERE " +
            "(m.senderId = :userA AND m.recipientId = :userB) OR " +
            "(m.senderId = :userB AND m.recipientId = :userA) " +
            "ORDER BY m.timestamp ASC")
    List<ChatMessage> findChatHistory(@Param("userA") String userA, @Param("userB") String userB);

    // Знайти історію, де юзер бере участь, але ЦЕ НЕ ПУБЛІЧНИЙ ЧАТ
    @Query("SELECT m FROM ChatMessage m WHERE (m.senderId = :userId OR m.recipientId = :userId) " +
            "AND m.recipientId != 'PUBLIC' ORDER BY m.timestamp ASC")
    List<ChatMessage> findPrivateConversationHistory(@Param("userId") String userId);

    // Знайти всі непрочитані повідомлення для конкретного користувача
    List<ChatMessage> countByRecipientIdAndStatus(String recipientId, MessageStatus status);

    // Отримуємо історію спільного чату, відсортовану за часом
    List<ChatMessage> findByRecipientIdOrderByTimestampAsc(String recipientId);

}